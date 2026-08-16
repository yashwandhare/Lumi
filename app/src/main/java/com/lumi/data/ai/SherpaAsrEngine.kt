package com.lumi.data.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Streaming speech-to-text on sherpa-onnx, the Phase 2 ASR stack on the owner's ruling.
 *
 * **Streaming is the point.** Audio goes into the recognizer chunk by chunk and partial text
 * comes back while the user is still talking — v1's batch-after-stop model is exactly what
 * made its voice stack feel broken, and the strategy-brief ruling moved sherpa into the
 * critical path for exactly this property.
 *
 * The recognition model is fetched on first use with the same machinery as the generative
 * model and the embedder: [AsrModels] pins every file by name, size, and SHA-256; [ModelStore]
 * verifies and commits; [ModelDownloader] resumes. Four small files, one combined progress
 * number, because that is what the UI shows.
 *
 * **The recognizer is not thread-safe.** One native session at a time is a property of the
 * library, not a choice — so [prepare] and [listen] share a lock, and a second [listen] call
 * while one is running fails rather than corrupting either session.
 *
 * Runs on the IO dispatcher. Recognition is chunk-sized native work with audio-capture
 * waits; it should not sit on the single inference thread the resident Gemma model owns.
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

    private val lock = Mutex()

    @Volatile
    private var recognizer: OnlineRecognizer? = null

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
            recognizer = createRecognizer()
        } catch (t: Throwable) {
            // A load failure is about the device or the bytes, not the session — surface the
            // cause honestly rather than as a silent button that does nothing.
            _state.value = AsrState.Unavailable(
                reason = "The speech engine could not load.",
                recoverable = true,
            )
            return
        }
        _state.value = AsrState.Ready
    }

    override suspend fun listen(onPartial: (String) -> Unit): AsrSessionResult {
        if (_state.value !is AsrState.Ready || recognizer == null) {
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

                val stream: OnlineStream = try {
                    recognizer!!.createStream()
                } catch (t: Throwable) {
                    recorder.release()
                    return@withContext AsrSessionResult.Failed(
                        reason = "The speech engine refused to start.",
                        recovery = "Try again, or type instead.",
                    )
                }

                var sessionResult: AsrSessionResult = AsrSessionResult.Failed(
                    reason = "The session did not run.",
                    recovery = "Try again.",
                )
                try {
                    recorder.startRecording()
                    if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        sessionResult = AsrSessionResult.Failed(
                            reason = "The microphone did not start.",
                            recovery = "Check that microphone access is allowed, then try again.",
                        )
                    } else {
                        sessionResult = runSession(stream, recorder, onPartial)
                    }
                    sessionResult
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
                    stream.release()
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

    /** Feeds audio until an endpoint, a cancel, or silence times out. */
    private suspend fun runSession(
        stream: OnlineStream,
        recorder: AudioRecord,
        onPartial: (String) -> Unit,
    ): AsrSessionResult {
        val frames = FloatArray(BLOCK_SAMPLES)
        var lastPartial = ""

        while (!cancelRequested) {
            currentCoroutineContext().ensureActive()

            val read = recorder.read(frames, 0, frames.size, AudioRecord.READ_BLOCKING)
            if (read > 0) {
                if (read < frames.size) {
                    stream.acceptWaveform(frames.copyOf(read), AsrModels.SAMPLE_RATE)
                } else {
                    stream.acceptWaveform(frames, AsrModels.SAMPLE_RATE)
                }
                // **`decode` only when `isReady` says so, and loop while it keeps saying so.**
                // The feature extractor needs a full window before a decode can run; calling
                // decode on a short buffer aborts the process inside the native layer
                // ("0 + 39 > 19" from features.cc), which no Kotlin catch can intercept. One
                // buffer can also hold enough audio for several decode steps, so this is a
                // while, not an if — an `if` would let the backlog grow and the transcript
                // fall progressively further behind the speaker.
                while (recognizer!!.isReady(stream)) {
                    recognizer!!.decode(stream)
                }

                val partial = recognizer!!.getResult(stream).text
                if (partial != lastPartial) {
                    lastPartial = partial
                    onPartial(partial)
                }
            }

            if (read >= 0 && recognizer!!.isEndpoint(stream)) {
                // Drain whatever is left before finishing, or the tail of the last word is
                // dropped — the words nearest the silence are the ones a user notices missing.
                stream.inputFinished()
                while (recognizer!!.isReady(stream)) {
                    recognizer!!.decode(stream)
                }
                break
            }
        }

        return if (cancelRequested) {
            AsrSessionResult.Cancelled
        } else {
            AsrSessionResult.Final(recognizer!!.getResult(stream).text)
        }
    }

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

        return try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AsrModels.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                // Two chunks of headroom so a slow consumer skips a beat rather than an overrun.
                (bufferBytes * 2).coerceAtLeast(BLOCK_SAMPLES * 4),
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                ?: null.also { /* an uninitialized recorder holds the mic — release it */ }
        } catch (t: Throwable) {
            null
        }
    }

    private fun createRecognizer(): OnlineRecognizer {
        val transducer = OnlineTransducerModelConfig().apply {
            encoder = store.fileFor(AsrModels.encoder).absolutePath
            decoder = store.fileFor(AsrModels.decoder).absolutePath
            joiner = store.fileFor(AsrModels.joiner).absolutePath
        }
        val modelConfig = OnlineModelConfig().apply {
            this.transducer = transducer
            tokens = store.fileFor(AsrModels.tokens).absolutePath
            numThreads = NUM_THREADS
            provider = "cpu"
        }
        val config = OnlineRecognizerConfig().apply {
            featConfig = FeatureConfig().apply {
                sampleRate = AsrModels.SAMPLE_RATE
                featureDim = AsrModels.FEATURE_DIM
                dither = 0f
            }
            this.modelConfig = modelConfig
            enableEndpoint = true
        }
        // **The asset manager must be null.** sherpa branches on it: a non-null manager means
        // "these paths are asset names", so the absolute paths above are looked up inside the APK,
        // fail, and the native layer calls abort() — a process kill no Kotlin catch can intercept.
        // Its own log says so ("set assetManager to null when you load model files from the SD
        // card"). Every model file here is downloaded into filesDir, so null is the correct value.
        return OnlineRecognizer(assetManager = null, config = config)
    }

    /** All four model files, one combined progress number for one progress bar. */
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
        /** 200ms of 16kHz floats. Small enough for live partials, big enough to read efficiently. */
        const val BLOCK_SAMPLES = 3_200

        /**
         * Decoding threads for the recognizer. Mid-range hardware, short utterances — two keeps
         * latency low without fighting the resident model for cores. Tuned on the device in
         * Phase 6 if call mode needs more.
         */
        const val NUM_THREADS = 2
    }
}
