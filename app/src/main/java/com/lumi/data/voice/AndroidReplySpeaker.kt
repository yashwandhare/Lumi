package com.lumi.data.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
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
    @param:ApplicationContext context: Context,
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
            engine.language = Locale.US
            engine.setOnUtteranceProgressListener(utteranceProgress)
            available = engine.language != null
        }
    }

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
}
