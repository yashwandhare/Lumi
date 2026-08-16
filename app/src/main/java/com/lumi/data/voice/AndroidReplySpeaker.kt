package com.lumi.data.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.lumi.core.voice.ReplySpeaker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Platform TTS behind [ReplySpeaker].
 *
 * The engine stays resident for the process: initializing a TTS engine is an IPC handshake
 * that costs a beat, and voice replies follow one another quickly enough to feel it.
 *
 * **The queue is the fix for v1's second bug.** Streamed reply chunks must be appended with
 * [TextToSpeech.QUEUE_ADD]. v1 queued every chunk into the same slot, which cancelled the
 * engine's own sentence mid-phrase: words skipped, then restarted. The speak job is only
 * ever cancelled by [stop], when the user interrupts a reply — never by the arrival of the
 * next chunk.
 *
 * Progress is tracked by utterance id: the engine reports when each queued chunk starts and
 * finishes, and [speaking] flips false the moment the last one is done, which is what lets
 * the voice UI's speaking state end by itself rather than by a timer.
 */
@Singleton
class AndroidReplySpeaker @Inject constructor(
    @ApplicationContext context: Context,
) : ReplySpeaker, TextToSpeech.OnInitListener {

    private val engine = TextToSpeech(context, this)
    private val queueIndex = AtomicInteger(0)
    private val pending = AtomicInteger(0)

    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    @Volatile
    override var available: Boolean = false
        private set

    @Volatile
    private var initialized = false

    override fun onInit(status: Int) {
        initialized = true
        if (status == TextToSpeech.SUCCESS) {
            // `setLanguage`'s return code, not a read-back of `language`. The getter is deprecated,
            // and worse it hands back the locale that was just set even when that locale's voice data
            // is not installed — so the engine reported itself available on a device that cannot
            // actually speak, and the failure surfaced as silence. LANG_MISSING_DATA and
            // LANG_NOT_SUPPORTED are negative; LANG_AVAILABLE and above can speak.
            available = engine.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE
            if (available) selectFemaleVoice()
            engine.setOnUtteranceProgressListener(utteranceProgress)
        }
    }

    /**
     * Pick a female English voice. Lumi is written as female throughout, and the platform default
     * on this hardware is male.
     *
     * There is no supported API that reports a voice's gender, so this matches on the naming
     * convention Android's own voices follow — `en-us-x-<variant>#female_1-local` and similar. That
     * is a heuristic, so it degrades in steps rather than failing: a named female voice, else any
     * English voice that is not explicitly male, else whatever `setLanguage` already chose. The last
     * case still speaks; it just may not sound female, which is a cosmetic miss rather than a broken
     * feature.
     *
     * Network-required voices are skipped. A higher-quality remote voice would send the reply text
     * to a server, and nothing about a local model's answer should leave the device to be read
     * aloud.
     */
    private fun selectFemaleVoice() {
        val candidates = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { it.locale.language == Locale.ENGLISH.language }
            .filterNot { it.isNetworkConnectionRequired }
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
        if (candidates.isEmpty()) return

        val chosen = candidates.firstOrNull { it.isLikelyFemale() }
            // Prefer the user's own country before falling back to any English voice, so en-IN is
            // kept on an Indian device rather than being replaced by en-US.
            ?: candidates.firstOrNull {
                it.locale.country == Locale.getDefault().country && !it.isLikelyMale()
            }
            ?: candidates.firstOrNull { !it.isLikelyMale() }
            ?: return

        runCatching { engine.setVoice(chosen) }
    }

    /**
     * Note the ordering: "female" *contains* "male", so a naive male check matches every female
     * voice as well. Female is therefore tested first and excluded from the male test.
     */
    private fun Voice.isLikelyFemale(): Boolean = name.contains(FEMALE_MARKER, ignoreCase = true)

    private fun Voice.isLikelyMale(): Boolean =
        !isLikelyFemale() && name.contains(MALE_MARKER, ignoreCase = true)

    override fun speak(text: String, queueAdd: Boolean) {
        val spoken = text.trim()
        if (spoken.isEmpty() || !available || !initialized) return

        val id = "lumi-${queueIndex.incrementAndGet()}"
        pending.incrementAndGet()
        _speaking.value = true
        val queueMode = if (queueAdd) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
        val queued = engine.speak(spoken, queueMode, Bundle(), id)
        if (queued != TextToSpeech.SUCCESS) {
            // The engine rejected the chunk. Without this the pending count would never drain
            // and the speaking state would stay lit on a silent engine.
            pending.decrementAndGet()
            if (pending.get() <= 0) {
                pending.set(0)
                _speaking.value = false
            }
        }
    }

    override fun stop() {
        pending.set(0)
        engine.stop()
        _speaking.value = false
    }

    override fun shutdown() {
        engine.shutdown()
    }

    private val utteranceProgress = object : android.speech.tts.UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            if (pending.decrementAndGet() <= 0) {
                pending.set(0)
                _speaking.value = false
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            onDone(utteranceId)
        }
    }

    private companion object {
        /** How Android's own voice names mark gender. A convention, not an API — see [selectFemaleVoice]. */
        const val FEMALE_MARKER = "female"
        const val MALE_MARKER = "male"
    }
}
