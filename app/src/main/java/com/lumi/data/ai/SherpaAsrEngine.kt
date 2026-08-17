package com.lumi.data.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.lumi.BuildConfig
import com.lumi.core.ai.AsrModels
import com.lumi.core.voice.AsrEngine
import com.lumi.core.voice.AsrSessionResult
import com.lumi.core.voice.AsrState
import com.lumi.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Speech-to-text on sherpa-onnx: **Whisper base.en for accuracy, Silero VAD for latency**.
 *
 * The earlier streaming 20M zipformer recognised fast but badly — on-device testing showed it
 * mangling ordinary sentences, and the owner's ruling is that recognition quality is the app's
 * front door and cannot be compromised. Whisper base.en is the smallest model that holds everyday
 * speech. The cost is that Whisper is not streaming, so this engine restores the live feel two
 * other ways:
 *
 * 1. **Silero VAD splits the mic feed into utterances.** Each pause closes a segment, and the
 *    segment is decoded immediately — so a long turn produces running text as it is spoken, and
 *    the final transcript lands one short decode after the last pause, not after the whole take.
 * 2. **Decode runs on its own thread, off the recording loop.** The microphone never waits for
 *    Whisper; the next utterance is already being captured while the previous one is recognised.
 *
 * The recognition models are fetched on first use with the same machinery as the generative
 * model and the embedder: [AsrModels] pins every file by name, size, and SHA-256; [ModelStore]
 * verifies and commits; [ModelDownloader] resumes. Four files, one combined progress number,
 * because that is what the UI shows.
 *
 * **The recognizer is not thread-safe.** One native session at a time is a property of the
 * library, not a choice — so [prepare] and [listen] share a lock, and a second [listen] call
 * while one is running fails rather than corrupting either session.
 *
 * Runs on the IO dispatcher. Recognition is native work with audio-capture waits; it should not
 * sit on the single inference thread the resident Gemma model owns.
 */
@Singleton
class SherpaAsrEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: ModelStore,
    private val downloader: ModelDownloader,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : AsrEngine {

    private val _state = MutableStateFlow<AsrState>(AsrState.Idle)
    override val state: StateFlow<AsrState> = _state.asStateFlow()

    private val _inputLevel = MutableStateFlow(0f)
    override val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()

    private val lock = Mutex()

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Volatile
    private var vad: Vad? = null

    @Volatile
    private var cancelRequested = false

    override suspend fun prepare() = lock.withLock {
        if (recognizer != null) {
            _state.value = AsrState.Ready
            return
        }

        if (AsrModels.all.any { !store.isReady(it) }) {
            when (val outcome = downloadAll()) {
                is DownloadOutcome.Success -> Unit
                is DownloadOutcome.Retryable -> {
                    _state.value = AsrState.Unavailable(outcome.reason, recoverable = true)
                    return
                }
                is DownloadOutcome.Permanent -> {
                    _state.value = AsrState.Unavailable(outcome.reason, recoverable = false)
                    return
                }
            }
        }

        _state.value = AsrState.Preparing(fraction = null, downloadedBytes = AsrModels.totalBytes)
        try {
            // Model loading is seconds of native init; it must not run on the caller's thread,
            // which for a voice session launch is the main thread.
            withContext(io) {
                recognizer = createRecognizer()
                vad = createVad()
            }
        } catch (t: Throwable) {
            // A load failure is about the device or the bytes, not the session — surface the
            // cause honestly rather than as a silent button that does nothing.
            recognizer = null
            vad = null
            _state.value = AsrState.Unavailable(
                reason = "The speech engine could not load.",
                recoverable = true,
            )
            return
        }
        _state.value = AsrState.Ready
    }

    override suspend fun listen(onPartial: (String) -> Unit): AsrSessionResult {
        if (_state.value !is AsrState.Ready || recognizer == null || vad == null) {
            return AsrSessionResult.Failed(
                reason = "Speech recognition is not ready.",
                recovery = "Try again in a moment, or type instead.",
            )
        }
        // One native session at a time is a property of the library. tryLock rather than withLock
        // so a second caller gets an honest "already in use" instead of queueing up a session
        // that will fight the first for the mic.
        if (!lock.tryLock()) {
            return AsrSessionResult.Failed(
                reason = "The microphone is already in use.",
                recovery = "Wait for the current session to finish.",
            )
        }

        cancelRequested = false
        return try {
            withContext(io) {
                val recorder = openRecorder() ?: return@withContext AsrSessionResult.Failed(
                    reason = "Lumi could not open the microphone.",
                    recovery = "Make sure another app is not recording, then try again.",
                )

                try {
                    recorder.startRecording()
                    if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        AsrSessionResult.Failed(
                            reason = "The microphone did not start.",
                            recovery = "Check that microphone access is allowed, then try again.",
                        )
                    } else {
                        runSession(recorder, onPartial)
                    }
                } catch (cancelled: CancellationException) {
                    // Mapping rather than rethrowing on purpose: the caller's contract here is a
                    // session outcome, and a torn-down scope means the session ended with the user.
                    AsrSessionResult.Cancelled
                } catch (io: IOException) {
                    AsrSessionResult.Failed(
                        reason = "The microphone stopped working mid-session.",
                        recovery = "Try again.",
                    )
                } finally {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                    recorder.release()
                }
            }
        } finally {
            lock.unlock()
        }
    }

    override fun cancel() {
        cancelRequested = true
    }

    /**
     * Records until the utterance is over, decoding each VAD segment the moment it closes.
     *
     * The VAD owns endpointing: a pause of [SileroVadModelConfig]'s min-silence closes a segment,
     * which is handed to the decode executor immediately. The session itself ends shortly after
     * the last segment closed and the mic has stayed quiet — that is when the user is done, not
     * when any single pause is.
     */
    private suspend fun runSession(
        recorder: AudioRecord,
        onPartial: (String) -> Unit,
    ): AsrSessionResult {
        val offlineRecognizer = recognizer!!
        val vadInstance = vad!!
        vadInstance.reset()

        val decoder = Executors.newSingleThreadExecutor()
        val transcript = StringBuilder()
        var heardSpeech = false
        var lastSpeechMs = System.currentTimeMillis()
        val sessionStartMs = lastSpeechMs
        val mic = FloatArray(MIC_BLOCK_SAMPLES)

        fun submitSegment(samples: FloatArray) {
            decoder.execute {
                try {
                    val startedMs = System.currentTimeMillis()
                    val text = cleanText(decodeSegment(offlineRecognizer, samples))
                    if (BuildConfig.DEBUG) {
                        // The two numbers that matter for perceived lag: how much audio this segment
                        // held, and how long Whisper took on it. A decode slower than the audio it
                        // covers means the transcript cannot keep up and VAD_MAX_SPEECH_S is the
                        // lever — see decisions_devb.md's reversal condition.
                        val audioMs = samples.size * 1_000L / AsrModels.SAMPLE_RATE
                        Log.d(
                            TAG,
                            "segment audio=${audioMs}ms decode=${System.currentTimeMillis() - startedMs}ms",
                        )
                    }
                    if (text.isNotEmpty()) {
                        val joined = synchronized(transcript) {
                            if (transcript.isNotEmpty()) transcript.append(' ')
                            transcript.append(text)
                            transcript.toString()
                        }
                        onPartial(joined)
                    }
                } catch (t: Throwable) {
                    // A failed segment is skipped, not fatal: losing one clause is better than
                    // losing the whole utterance, and the user still has the keyboard.
                }
            }
        }

        try {
            while (!cancelRequested) {
                currentCoroutineContext().ensureActive()

                val read = recorder.read(mic, 0, mic.size, AudioRecord.READ_BLOCKING)
                if (read < 0) break
                if (read > 0) {
                    publishLevel(mic, read)
                    vadInstance.acceptWaveform(if (read == mic.size) mic else mic.copyOf(read))
                }

                // A segment is closed speech — hand it off at once so its decode overlaps with
                // whatever the user says next.
                //
                // **`lastSpeechMs` is deliberately not touched here.** A segment only becomes
                // available after Silero has already observed `VAD_MIN_SILENCE_S` of quiet to
                // decide the utterance ended, so by the time it pops, the speech stopped ~450ms
                // ago. Restarting the end-of-turn timer at that moment stacked the two windows —
                // 450ms of VAD silence and then a further 900ms — so every turn waited about 1.35s
                // after the user stopped talking before anything was sent. The timer below measures
                // from genuine voice activity only, which is the thing it is supposed to measure.
                while (!vadInstance.empty()) {
                    heardSpeech = true
                    val segment = vadInstance.front()
                    vadInstance.pop()
                    submitSegment(segment.samples)
                }
                if (vadInstance.isSpeechDetected()) {
                    heardSpeech = true
                    lastSpeechMs = System.currentTimeMillis()
                }

                val now = System.currentTimeMillis()
                val silentFor = now - lastSpeechMs
                when {
                    // Nothing heard for a while: the tap was a mistake or the user walked away.
                    !heardSpeech && silentFor > IDLE_TIMEOUT_MS -> break
                    // Speech heard, and the silence after it has run out: the turn is over.
                    heardSpeech && silentFor > END_SILENCE_MS -> break
                    // Hard stop for a runaway session; the audio captured so far is still used.
                    now - sessionStartMs > MAX_SESSION_MS -> break
                }
            }

            // Squeeze the tail out of the VAD so the last word is not dropped for a pause.
            vadInstance.flush()
            while (!vadInstance.empty()) {
                val segment = vadInstance.front()
                vadInstance.pop()
                submitSegment(segment.samples)
            }
        } finally {
            _inputLevel.value = 0f
            drain(decoder)
        }

        return if (cancelRequested) {
            AsrSessionResult.Cancelled
        } else {
            AsrSessionResult.Final(
                synchronized(transcript) { transcript.toString() },
            )
        }
    }

    /** One Whisper pass over one closed utterance. */
    private fun decodeSegment(offlineRecognizer: OfflineRecognizer, samples: FloatArray): String {        val stream: OfflineStream = offlineRecognizer.createStream()
        return try {
            stream.acceptWaveform(samples, AsrModels.SAMPLE_RATE)
            offlineRecognizer.decode(stream)
            offlineRecognizer.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    /**
     * Publish how loud this block was, 0f..1f, for the mascot to breathe with.
     *
     * RMS rather than peak: peak tracks a single click and makes the mascot twitch, RMS tracks how
     * much energy the block carried and reads as speech. The scale is deliberately generous — normal
     * speech sits well below 1.0 RMS, so [LEVEL_FULL_SCALE_RMS] maps a comfortable speaking voice to
     * most of the range instead of leaving the mascot barely moving.
     *
     * Smoothed asymmetrically: fast to rise, slow to fall. Attacking quickly makes the mascot feel
     * responsive to a word starting, while decaying slowly stops it collapsing in the gaps between
     * syllables — which at 64ms a block it otherwise would, several times per word.
     */
    private fun publishLevel(frames: FloatArray, count: Int) {
        if (count <= 0) return
        var sumSquares = 0.0
        for (i in 0 until count) {
            val sample = frames[i]
            sumSquares += sample * sample
        }
        val rms = kotlin.math.sqrt(sumSquares / count).toFloat()
        val target = (rms / LEVEL_FULL_SCALE_RMS).coerceIn(0f, 1f)
        val previous = _inputLevel.value
        val smoothing = if (target > previous) LEVEL_ATTACK else LEVEL_DECAY
        _inputLevel.value = previous + (target - previous) * smoothing
    }

    /** Wait for every queued decode; bounded so a hung segment cannot strand the session. */
    private fun drain(executor: ExecutorService) {        executor.shutdown()
        executor.awaitTermination(DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        executor.shutdownNow()
    }

    /** Whisper output is padded and spacey; the router and the UI expect one clean line. */
    private fun cleanText(raw: String): String =
        raw.trim().replace(WHITESPACE_RUNS, " ")

    /**
     * The microphone at the sample rate the model wants.
     *
     * Returns null on any failure instead of throwing: a denied permission, a busy mic, and
     * unsupported hardware all end with the same honest fallback — typed input.
     */
    private fun openRecorder(): AudioRecord? {
        val permitted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitted) return null

        val bufferBytes = AudioRecord.getMinBufferSize(
            AsrModels.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).takeIf { it > 0 } ?: return null

        val recorder = try {
            AudioRecord(
                // VOICE_RECOGNITION rather than MIC. MIC applies whatever tuning the vendor thinks
                // suits general recording — on Samsung that includes gain and noise processing aimed
                // at voice memos, which distorts the spectrum a recogniser is trained on.
                // VOICE_RECOGNITION is the source Android documents for exactly this job and asks the
                // platform to leave the signal alone.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                AsrModels.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                // Two chunks of headroom so a slow consumer skips a beat rather than an overrun.
                (bufferBytes * 2).coerceAtLeast(MIC_BLOCK_SAMPLES * 4),
            )
        } catch (t: Throwable) {
            return null
        }

        // **An uninitialized recorder still holds the microphone, so it must be released.** Dropping
        // the reference instead left the mic held by an object nothing could reach, so the first
        // failed open made every later attempt fail too and the mic button became a coin flip.
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        return recorder
    }

    private fun createRecognizer(): OfflineRecognizer {
        val whisper = OfflineWhisperModelConfig().apply {
            encoder = store.fileFor(AsrModels.encoder).absolutePath
            decoder = store.fileFor(AsrModels.decoder).absolutePath
            language = "en"
            task = "transcribe"
        }
        val modelConfig = OfflineModelConfig().apply {
            this.whisper = whisper
            tokens = store.fileFor(AsrModels.tokens).absolutePath
            numThreads = NUM_THREADS
            provider = "cpu"
        }
        val config = OfflineRecognizerConfig().apply {
            featConfig = FeatureConfig().apply {
                sampleRate = AsrModels.SAMPLE_RATE
                featureDim = AsrModels.FEATURE_DIM
                dither = 0f
            }
            this.modelConfig = modelConfig
        }
        // **The asset manager must be null.** sherpa branches on it: a non-null manager means
        // "these paths are asset names", so the absolute paths above are looked up inside the APK,
        // fail, and the native layer calls abort() — a process kill no Kotlin catch can intercept.
        // Every model file here is downloaded into filesDir, so null is the correct value.
        return OfflineRecognizer(assetManager = null, config = config)
    }

    /**
     * Silero VAD tuned for low latency: a short pause closes an utterance so its decode can
     * start while the user is still talking, and a long utterance is force-split so no single
     * decode grows large.
     */
    private fun createVad(): Vad {
        val silero = SileroVadModelConfig(
            model = store.fileFor(AsrModels.vad).absolutePath,
            threshold = VAD_THRESHOLD,
            minSilenceDuration = VAD_MIN_SILENCE_S,
            minSpeechDuration = VAD_MIN_SPEECH_S,
            windowSize = VAD_WINDOW_SAMPLES,
            maxSpeechDuration = VAD_MAX_SPEECH_S,
        )
        val config = VadModelConfig(
            sileroVadModelConfig = silero,
            sampleRate = AsrModels.SAMPLE_RATE,
        )
        return Vad(assetManager = null, config = config)
    }

    /** All model files, one combined progress number for one progress bar. */
    private suspend fun downloadAll(): DownloadOutcome {
        var downloadedBase = AsrModels.all.filter { store.isReady(it) }.sumOf { it.sizeBytes }
        for (model in AsrModels.all) {
            if (store.isReady(model)) continue
            val outcome = downloader.download(model) { downloaded, _, _ ->
                _state.value = AsrState.Preparing(
                    fraction = (downloadedBase + downloaded).toFloat() / AsrModels.totalBytes,
                    downloadedBytes = downloadedBase + downloaded,
                )
            }
            if (outcome !is DownloadOutcome.Success) return outcome
            downloadedBase += model.sizeBytes
        }
        return DownloadOutcome.Success
    }

    private companion object {
        const val TAG = "LumiAsr"

        /**
         * RMS that maps to a fully-expanded mascot. Measured on the demo device: a quiet room sits
         * near 0.01 and a normal speaking voice at arm's length peaks around 0.15, so this puts
         * ordinary speech across most of the range rather than in the bottom tenth of it.
         */
        const val LEVEL_FULL_SCALE_RMS = 0.15f

        /** Fast rise, slow fall — see [publishLevel]. */
        const val LEVEL_ATTACK = 0.6f
        const val LEVEL_DECAY = 0.15f

        /**
         * 64ms of 16kHz audio per mic read, a multiple of the VAD window so every sample goes
         * straight to the voice detector without a leftover buffer to manage.
         *
         * Halved from 128ms to cut latency: the loop can only notice that speech ended, and close a
         * segment, on a read boundary, so the block length is a floor under every reaction time in
         * this loop. 64ms is still two VAD windows per read, so the detector is not starved.
         */
        const val MIC_BLOCK_SAMPLES = 1_024

        /** Silero's analysis window at 16kHz — the library's fixed contract, in samples. */
        const val VAD_WINDOW_SAMPLES = 512

        const val VAD_THRESHOLD = 0.5f

        /**
         * Silence shorter than this does not end an utterance: real speakers pause.
         *
         * 0.30s rather than 0.45s. This is the dominant term in perceived lag — it is paid on every
         * single utterance, before the decode even starts. 300ms is still longer than the gaps
         * inside connected speech, and a mid-sentence split is not a correctness problem here
         * anyway: segments are concatenated into one transcript, so the cost of splitting early is
         * an extra short decode, not a lost word.
         */
        const val VAD_MIN_SILENCE_S = 0.30f

        const val VAD_MIN_SPEECH_S = 0.25f

        /** Utterances longer than this are split, so every decode stays short and fast. */
        const val VAD_MAX_SPEECH_S = 8f

        /**
         * Session closes this long after voice activity stops.
         *
         * Measured from real speech, not from a segment popping — see the note in the capture loop,
         * where restarting this timer on a pop was silently adding [VAD_MIN_SILENCE_S] on top of it.
         * With that double-count gone, this is the only end-of-turn wait, so it can be short: the
         * VAD has already confirmed the speaker stopped before this timer runs out.
         */
        const val END_SILENCE_MS = 500L

        /** A tap with no speech at all closes the session rather than listening forever. */
        const val IDLE_TIMEOUT_MS = 7_000L

        /** Wall-clock bound on one session; the audio captured by then is still recognised. */
        const val MAX_SESSION_MS = 60_000L

        /** Upper bound on waiting for queued decodes at session end. */
        const val DRAIN_TIMEOUT_MS = 15_000L

        /**
         * Decode threads for Whisper. Voice recognition runs alone — generation has not started
         * yet while the user is still speaking — so four threads can be spent on latency.
         */
        const val NUM_THREADS = 4

        val WHITESPACE_RUNS = Regex("\\s+")
    }
}
