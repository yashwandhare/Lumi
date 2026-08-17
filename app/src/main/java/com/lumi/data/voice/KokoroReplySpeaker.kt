package com.lumi.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.lumi.core.ai.TtsModels
import com.lumi.core.voice.ReplySpeaker
import com.lumi.data.ai.DownloadOutcome
import com.lumi.data.ai.ModelDownloader
import com.lumi.data.ai.ModelStore
import com.lumi.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device neural TTS behind [ReplySpeaker]: **Kokoro-82M (int8)** through sherpa-onnx, speaking
 * as `af_heart`.
 *
 * The platform engine this replaces reads as generic and robotic — the owner's words, "nothing
 * human like or even near" — and it is what a judge hears when Lumi replies aloud. Kokoro is the
 * best-sounding open voice in its class and runs entirely on-device through the same sherpa-onnx
 * AAR that hosts recognition, so the reply never leaves the phone. Cloud voices were ruled out
 * for that exact reason: network TTS would put an on-device model's replies on a server.
 *
 * **Synthesis and playback run as a pipeline.** A synth thread turns sentence chunks into audio
 * and feeds a bounded queue; a play thread streams the queue to the audio output. The next
 * sentence is synthesised while the current one is still being spoken, so a reply reads as one
 * continuous voice instead of a sentence, a gap, a sentence. Waiting for playback of one chunk
 * to end before synthesising the next was exactly that gap — the pause-after-every-sentence bug
 * found on the demo device.
 *
 * Whole-chunk synthesis, not a native callback: the callback variant makes the sherpa native
 * layer invoke the Kotlin lambda through JNI reflection, and the compiled lambda's synthetic
 * class exposes only a *static* `invoke`, so the lookup fails and the native layer aborts the
 * process — a crash found on device, kept out by construction here.
 *
 * Chunk timing and sizes are logged under [TAG], never the text: the audit rule applies to the
 * voice engine like everything else.
 *
 * **Fallback is structural, not optimistic.** [prepare] fetches and loads the voice models on
 * first use; if any of that fails the speaker hands every call to [AndroidReplySpeaker] and the
 * app keeps reading replies aloud in the platform voice. A missing voice must never mean silence.
 *
 * The models are pinned and fetched with the same discipline as every other artefact:
 * [TtsModels] names, sizes, and digests each file; [ModelStore] verifies and commits;
 * [ModelDownloader] resumes. The espeak phoneme data ships as one digest-pinned archive and is
 * extracted once, with a receipt so it is never unpacked twice.
 */
@Singleton
class KokoroReplySpeaker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: ModelStore,
    private val downloader: ModelDownloader,
    private val fallback: AndroidReplySpeaker,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ReplySpeaker {

    /**
     * True while either engine speaks: ours while neural audio is playing, or the platform
     * engine's own flow while a fallback turn reads aloud. The voice UI anchors its speaking
     * state on this, so it must hold on whichever branch actually has the sound.
     */
    private val mergeScope = CoroutineScope(SupervisorJob() + io)

    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> =
        combine(_speaking, fallback.speaking) { local, backup -> local || backup }
            .stateIn(mergeScope, SharingStarted.Eagerly, false)

    @Volatile
    private var tts: OfflineTts? = null

    @Volatile
    private var fallbackActive = false

    @Volatile
    private var prepared = false

    override val available: Boolean
        get() = if (fallbackActive) fallback.available else tts != null

    override suspend fun prepare() {
        if (prepared || tts != null) return
        if (fallbackActive) return

        val downloaded = downloadAll()
        if (downloaded !is DownloadOutcome.Success) {
            val permanent = downloaded is DownloadOutcome.Permanent
            if (permanent) fallbackActive = true
            Log.w(
                TAG,
                "Voice download ${if (permanent) "cannot complete" else "was interrupted"}; " +
                    "replies use the platform voice instead.",
            )
            return
        }

        try {
            withContext(io) {
                if (!extractEspeakData()) throw IllegalStateException("Unpacking the voice data failed.")
                tts = createEngine()
                track = openAudioTrack()
            }
            prepared = true
            // First real synthesis pays one-time allocation inside the runtime unless the engine
            // has produced audio before. Burn it now, off the listening path, so the first spoken
            // reply is not the slowest one.
            synthExecutor.execute(::warmUp)
            Log.i(TAG, "Kokoro voice ready.")
        } catch (t: Throwable) {
            tts = null
            fallbackActive = true
            // A native init failure is not a session error — the platform voice still works.
            Log.e(TAG, "Kokoro could not load; falling back to the platform voice.", t)
        }
    }

    override fun speak(text: String, queueAdd: Boolean) {
        val spoken = text.trim()
        if (spoken.isEmpty()) return

        if (tts == null || fallbackActive) {
            // Not ready yet, or fallen back — the platform engine speaks instead. First voice
            // turn while the download is mid-flight lands here, and the reply is still heard.
            fallback.speak(spoken, queueAdd)
            return
        }

        if (!queueAdd) stop()

        synchronized(pipelineLock) {
            textInbox.offer(spoken)
            ensureWorkersLocked()
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        synchronized(pipelineLock) {
            textInbox.clear()
            audioQueue.clear()
            running = false
        }
        runCatching {
            val output = track
            output?.pause()
            output?.flush()
            // Re-sync the drain bookkeeping to the playback head: after a flush the frames
            // still counted as written will never reach the speaker, so they must not hold
            // the speaking state.
            if (output != null) writtenFrames.set(output.playbackHeadPosition.toLong())
        }
        _speaking.value = false
        fallback.stop()
    }

    override fun shutdown() {
        stop()
        runCatching { tts?.release() }
        runCatching { track?.release() }
        tts = null
        track = null
        fallback.shutdown()
    }

    /** Start the pipeline's worker threads if they are not already running. Called under [pipelineLock]. */
    private fun ensureWorkersLocked() {
        if (!running) {
            running = true
            _speaking.value = true
        }
        if (!synthActive) {
            synthActive = true
            synthExecutor.execute(::synthLoop)
        }
        if (!playActive) {
            playActive = true
            playExecutor.execute(::playLoop)
        }
    }

    /**
     * Producer: turn queued text into audio. Runs ahead of playback by up to [MAX_QUEUED_AUDIO]
     * chunks, so the play thread never waits on synthesis for anything but the very first one.
     */
    private fun synthLoop() {
        val gen = generation.get()
        try {
            while (gen == generation.get()) {
                val text = textInbox.poll(SYNTH_POLL_MS, TimeUnit.MILLISECONDS)
                if (text == null) {
                    // The wait came up empty; if nothing arrived in the meantime, exit. The
                    // finally block restarts the loop if text landed after this check.
                    val exit = synchronized(pipelineLock) { textInbox.isEmpty() }
                    if (exit) return
                    continue
                }

                val start = System.currentTimeMillis()
                val outcome = runCatching {
                    tts!!.generate(text, TtsModels.SPEAKER_ID, 1.0f)
                }
                if (gen != generation.get()) return
                val audio = outcome.getOrElse { failure ->
                    // Swallowed errors already cost this project a day of debugging; name
                    // the failure with everything except the content.
                    Log.w(TAG, "Synthesis failed for ${text.length} chars.", failure)
                    continue
                }
                if (audio.samples.isEmpty()) {
                    Log.w(TAG, "Synthesis returned no audio for ${text.length} chars.")
                    continue
                }
                Log.i(
                    TAG,
                    "chunk chars=${text.length} " +
                        "synth=${System.currentTimeMillis() - start}ms " +
                        "audio=${(audio.samples.size * 1000L) / TtsModels.SAMPLE_RATE}ms",
                )
                // Bounded offer rather than put: an interrupted pipeline must not leave this
                // thread parked inside an unbounded blocking put.
                var offered = false
                while (!offered && gen == generation.get()) {
                    offered = audioQueue.offer(audio, OFFER_POLL_MS, TimeUnit.MILLISECONDS)
                }
                if (!offered) return
            }
        } finally {
            synchronized(pipelineLock) {
                synthActive = false
                if (running && textInbox.isNotEmpty()) {
                    synthActive = true
                    synthExecutor.execute(::synthLoop)
                }
            }
        }
    }

    /**
     * Consumer: stream queued audio out. The session's speaking state ends only when the text
     * queue, the audio queue, and the output buffer are all empty — not when the text happens to
     * be gone. Ending it while seconds of audio were still draining let the hands-free mic
     * re-open early and its [stop] cut the tail off the reply, which is how the user heard only
     * the last few words before the drain fix.
     */
    private fun playLoop() {
        val gen = generation.get()
        try {
            while (gen == generation.get()) {
                val audio = audioQueue.poll(PLAY_POLL_MS, TimeUnit.MILLISECONDS)
                if (audio != null) {
                    writeToTrack(audio.samples, gen)
                    continue
                }

                val idle = synchronized(pipelineLock) {
                    audioQueue.isEmpty() && textInbox.isEmpty() && !synthActive
                }
                if (!idle) continue

                // Nothing is coming: play out what is already in the buffer, then end. The
                // drain must run outside the lock — [stop] takes it, and waiting seconds for
                // audio to finish while holding it would freeze interruption.
                drain(gen)
                val stillIdle = synchronized(pipelineLock) {
                    val nothingLeft = audioQueue.isEmpty() && textInbox.isEmpty() && !synthActive
                    if (nothingLeft) {
                        runCatching { track?.pause() }
                        running = false
                        _speaking.value = false
                    }
                    nothingLeft
                }
                if (stillIdle) return
            }
        } finally {
            synchronized(pipelineLock) {
                playActive = false
                if (running && (audioQueue.isNotEmpty() || textInbox.isNotEmpty() || synthActive)) {
                    playActive = true
                    playExecutor.execute(::playLoop)
                }
            }
        }
    }

    /** Blocking-style write loop that can be cut mid-chunk by [stop]. */
    private fun writeToTrack(samples: FloatArray, gen: Int) {
        val output = track ?: return
        runCatching { output.play() }
        var offset = 0
        while (offset < samples.size && gen == generation.get()) {
            val written = runCatching {
                output.write(samples, offset, samples.size - offset, AudioTrack.WRITE_NON_BLOCKING)
            }.getOrElse { -1 }
            if (written > 0) {
                offset += written
                writtenFrames.addAndGet(written.toLong())
            } else {
                Thread.sleep(WRITE_WAIT_MS)
            }
        }
    }

    /** One throwaway synthesis, so the engine's first-replies-slowest cost is paid in the background. */
    private fun warmUp() {
        runCatching { tts?.generate(WARMUP_TEXT, TtsModels.SPEAKER_ID, 1.0f) }
            .onSuccess { Log.i(TAG, "Warm-up done.") }
    }

    /** Wait for every written frame to reach the speaker, or for an interruption. */
    private fun drain(gen: Int) {
        val output = track ?: return
        while (gen == generation.get()) {
            val pending = writtenFrames.get() - output.playbackHeadPosition.toLong()
            if (pending <= 0) return
            Thread.sleep(DRAIN_POLL_MS)
        }
    }

    private fun createEngine(): OfflineTts {
        val kokoro = OfflineTtsKokoroModelConfig().apply {
            model = store.fileFor(TtsModels.model).absolutePath
            voices = store.fileFor(TtsModels.voices).absolutePath
            tokens = store.fileFor(TtsModels.tokens).absolutePath
            lexicon = store.fileFor(TtsModels.lexicon).absolutePath
            // The phoneme data, not its parent: sherpa opens the files directly inside.
            dataDir = espeakDir().absolutePath
            lang = "en"
        }
        val modelConfig = OfflineTtsModelConfig().apply {
            this.kokoro = kokoro
            numThreads = NUM_THREADS
            provider = "cpu"
        }
        val config = OfflineTtsConfig().apply {
            this.model = modelConfig
            // Kokoro processes sentences one at a time internally regardless of
            // maxNumSentences, so batching multiple sentences into a single generate()
            // call does not gain parallelism — but it does reduce inter-call overhead.
            // silenceScale=0.15 shortens pauses >200ms (generated by the model's own
            // prosody) to keep replies sounding like one continuous speaker rather than
            // a sentence, a breath, a sentence. Range is [0.01, 10]; 0.2 default was
            // noticeably long on device.
            silenceScale = 0.15f
        }
        // Same contract as the recogniser: a null asset manager means the paths are real files
        // in the app's own storage, which they are.
        return OfflineTts(assetManager = null, config = config)
    }

    private fun openAudioTrack(): AudioTrack {
        val bufferSize = AudioTrack.getMinBufferSize(
            TtsModels.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).takeIf { it > 0 } ?: throw IllegalStateException("No usable audio output buffer size.")

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(TtsModels.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private suspend fun downloadAll(): DownloadOutcome = withContext(io) {
        for (model in TtsModels.all) {
            if (store.isReady(model)) continue
            val outcome = downloader.download(model) { _, _, _ ->
                // No progress surface here yet: the download rides along behind the voice
                // session while listening is what the user sees. If it is slow the reply
                // speaks in the platform voice until it lands.
            }
            if (outcome !is DownloadOutcome.Success) return@withContext outcome
        }
        DownloadOutcome.Success
    }

    /** The extracted phoneme directory sherpa reads from. */
    private fun espeakDir(): File =
        File(store.fileFor(TtsModels.espeakData).parentFile!!, "espeak-ng-data")

    /**
     * Expand the pinned espeak archive once, then record a receipt carrying the archive digest.
     * The receipt, not the directory's existence, is the extracted check — a half-unpacked tree
     * from an interrupted first run must not be mistaken for a full one.
     */
    private fun extractEspeakData(): Boolean {
        val target = espeakDir()
        val receipt = File(target.parentFile!!, ESPACK_RECEIPT)
        if (receipt.isFile && receipt.readText().trim() == TtsModels.espeakData.sha256 &&
            File(target, "phondata").isFile
        ) {
            return true
        }

        val archive = store.fileFor(TtsModels.espeakData)
        if (!store.isReady(TtsModels.espeakData)) return false

        target.deleteRecursively()
        try {
            java.util.zip.ZipInputStream(archive.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val out = File(target.parentFile!!, entry.name)
                        // The archive is digest-pinned so its contents are known, but a traversal
                        // check costs nothing and a renamed archive is worth no debugging time.
                        if (!out.canonicalPath.startsWith(target.parentFile!!.canonicalPath + File.separator)) {
                            throw IllegalStateException("Archive entry escapes its directory: ${entry.name}")
                        }
                        out.parentFile?.mkdirs()
                        out.outputStream().use { fileOut -> zip.copyTo(fileOut) }
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not unpack the voice data.", t)
            target.deleteRecursively()
            receipt.delete()
            return false
        }

        if (!File(target, "phondata").isFile) {
            Log.e(TAG, "Voice data unpacked but is missing its core files.")
            return false
        }
        receipt.writeText(TtsModels.espeakData.sha256)
        return true
    }

    /** Text waiting to be synthesised. */
    private val textInbox = LinkedBlockingQueue<String>()

    /** Audio waiting to be played. Bounded so synthesis never races far ahead of the play head. */
    private val audioQueue = LinkedBlockingQueue<GeneratedAudio>(MAX_QUEUED_AUDIO)

    private val generation = AtomicInteger(0)

    /** Total frames written, tracked against playback head position for the [drain] check. */
    private val writtenFrames = AtomicLong(0)

    /** Pipeline is live while true. Guarded by [pipelineLock]. */
    private var running = false
    private var synthActive = false
    private var playActive = false

    private val pipelineLock = Any()

    @Volatile
    private var track: AudioTrack? = null

    private val synthExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lumi-voice-synth").apply { isDaemon = true }
    }

    private val playExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lumi-voice-play").apply { isDaemon = true }
    }

    private companion object {
        const val TAG = "LumiTts"
        const val ESPACK_RECEIPT = "espeak-ng-data.receipt"

        /**
         * Synthesis threads. Generation overlaps the model's reply streaming on this 8-core
         * device, so this spends the same budget the recognition engine uses rather than the
         * whole machine.
         */
        const val NUM_THREADS = 4

        /** Chunks synthesised ahead of playback. A small bound: each is a few hundred KB. */
        const val MAX_QUEUED_AUDIO = 4

        /** Poll cadences: short enough that a new chunk is picked up quickly, long enough to idle. */
        const val SYNTH_POLL_MS = 500L
        const val PLAY_POLL_MS = 50L
        const val OFFER_POLL_MS = 200L

        /** Sleep between non-blocking audio writes when the output buffer is full. */
        const val WRITE_WAIT_MS = 5L

        /** How often [drain] checks whether the written audio has reached the speaker. */
        const val DRAIN_POLL_MS = 20L

        /** Short enough to be quick, real enough to exercise the engine. */
        const val WARMUP_TEXT = "warm up"
    }
}
