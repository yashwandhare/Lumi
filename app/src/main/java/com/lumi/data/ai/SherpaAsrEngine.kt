package com.lumi.data.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.lumi.core.ai.AsrModels
import com.lumi.BuildConfig
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

    /** Feeds audio until the user finishes speaking, a cancel, or the session times out. */
    private suspend fun runSession(
        stream: OnlineStream,
        recorder: AudioRecord,
        onPartial: (String) -> Unit,
    ): AsrSessionResult {
        val frames = FloatArray(BLOCK_SAMPLES)
        var lastPartial = ""
        // What the user has actually said, accumulated across endpoints. sherpa resets its own
        // result at every endpoint, so anything already recognised has to be kept here or a
        // natural mid-sentence pause silently truncates the request.
        val settled = StringBuilder()
        var spokeAtAll = false
        var blocksSinceSpeech = 0
        var silentBlocks = 0
        var loudBlocks = 0
        // The room's own noise level, learned over the opening blocks of this session.
        var calibrationSum = 0.0
        var calibrationBlocks = 0
        var speechThreshold = ABSOLUTE_RMS_FLOOR

        while (!cancelRequested) {
            currentCoroutineContext().ensureActive()

            val read = recorder.read(frames, 0, frames.size, AudioRecord.READ_BLOCKING)
            if (read > 0) {
                // **Was this block actually someone talking?** Every block still goes to the
                // recognizer — sherpa detects an endpoint from trailing silence, so starving it of
                // silence would mean it never reports one. But whether *speech happened* is decided
                // here, on signal energy, not on whether the model produced characters. A streaming
                // model asked to transcribe a quiet room obligingly invents words from it, which is
                // why an untouched phone produced "HALLOOR CANOE" and sent it as a question.
                //
                // The threshold is **learned, not fixed.** Measured on the demo device, a silent room
                // ranges 0.004 to 0.047 RMS with a median of 0.009 — so any constant low enough to
                // catch quiet speech is also low enough to let that room's own peaks through, and any
                // constant safely above the peaks would miss someone speaking softly. The first
                // blocks of each session calibrate the room instead, and speech has to stand out
                // from *that*. It also means a noisy café and a quiet bedroom both work.
                val rms = rmsOf(frames, read)
                if (calibrationBlocks < CALIBRATION_BLOCKS) {
                    calibrationSum += rms
                    calibrationBlocks++
                    if (calibrationBlocks == CALIBRATION_BLOCKS) {
                        val noise = calibrationSum / CALIBRATION_BLOCKS
                        speechThreshold = maxOf(ABSOLUTE_RMS_FLOOR, noise * SPEECH_RMS_MULTIPLE)
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "noise=%.4f threshold=%.4f".format(noise, speechThreshold))
                        }
                    }
                }
                val loud = calibrationBlocks >= CALIBRATION_BLOCKS && rms >= speechThreshold

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

                if (loud) {
                    loudBlocks++
                    if (loudBlocks >= LOUD_BLOCKS_TO_ARM) spokeAtAll = true
                    blocksSinceSpeech = 0
                } else if (spokeAtAll) {
                    blocksSinceSpeech++
                }

                // Nothing is shown until the energy gate is satisfied, so a quiet room never puts
                // invented words on screen.
                if (spokeAtAll) {
                    val combined = (settled.toString() + " " + recognizer!!.getResult(stream).text)
                        .trim()
                    if (combined != lastPartial) {
                        lastPartial = combined
                        onPartial(combined)
                    }
                }
            }

            // **An endpoint only ends the session once something has been said.** sherpa reports
            // an endpoint on trailing silence, and at the top of a session there is nothing *but*
            // silence — so the previous version ended in the first couple of hundred milliseconds,
            // before the user had begun, and returned an empty or single-letter result. That is the
            // "my voice is never registered" and "hello became O" report.
            if (read >= 0 && recognizer!!.isEndpoint(stream)) {
                val atEndpoint = recognizer!!.getResult(stream).text.trim()
                if (atEndpoint.isNotEmpty()) {
                    settled.append(' ').append(atEndpoint)
                }
                // Reset, or the next utterance decodes on top of this one's state. sherpa requires
                // this at every endpoint; without it a second phrase came back mangled.
                recognizer!!.reset(stream)

                // Finish only on a real pause after real speech. Otherwise keep the mic open: a
                // pause for breath in the middle of "remind me at six... to call mom" must not be
                // read as the end of the request.
                if (spokeAtAll && blocksSinceSpeech >= SILENT_BLOCKS_TO_FINISH) break
            }

            // A session that hears nothing at all still has to end, or the mic stays open forever.
            if (!spokeAtAll && ++silentBlocks > MAX_SILENT_BLOCKS) {
                return AsrSessionResult.Failed(
                    reason = "Lumi did not hear anything.",
                    recovery = "Tap the mic and speak, or type instead.",
                )
            }
        }

        val finalText = (settled.toString() + " " + recognizer!!.getResult(stream).text).trim()
        return when {
            cancelRequested -> AsrSessionResult.Cancelled
            // The energy gate has the final say. Without it a session in a quiet room returns
            // invented words, and inventing a question the user never asked is worse than
            // admitting nothing was heard.
            !spokeAtAll || finalText.length < MIN_TRANSCRIPT_CHARS -> AsrSessionResult.Failed(
                reason = "Lumi did not catch that.",
                recovery = "Tap the mic and speak a little closer, or type instead.",
            )
            else -> AsrSessionResult.Final(finalText.normalizeSpokenCase())
        }
    }

    /**
     * Is this block of audio someone speaking, rather than a quiet room?
     *
     * Root-mean-square amplitude against a fixed floor. Deliberately the simplest thing that works:
     * a real voice-activity model is another download and another native session, and the job here is
     * only to separate "a person is talking into this phone" from "a room with a fan in it".
     */
    /**
     * Root-mean-square amplitude of a block, on the -1..1 float scale.
     *
     * Separate from the speech decision because the first blocks of a session are used to learn the
     * room rather than to detect speech — see [runSession].
     */
    private fun rmsOf(frames: FloatArray, count: Int): Double {
        if (count == 0) return 0.0
        var sumSquares = 0.0
        for (i in 0 until count) {
            val sample = frames[i]
            sumSquares += sample * sample
        }
        return kotlin.math.sqrt(sumSquares / count)
    }

    /**
     * The recogniser emits uppercase BPE output — "WHAT CAN YOU DO". Lowercased before it reaches the
     * model, because ALL CAPS reads as shouting to an instruction-tuned model and measurably changes
     * the register of the reply. The first letter is restored so the transcript still looks like a
     * sentence on screen.
     */
    private fun String.normalizeSpokenCase(): String {
        if (none { it.isLowerCase() }) {
            return lowercase().replaceFirstChar { it.uppercaseChar() }
        }
        return this
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
                (bufferBytes * 2).coerceAtLeast(BLOCK_SAMPLES * 4),
            )
        } catch (t: Throwable) {
            return null
        }

        // **An uninitialized recorder still holds the microphone, so it must be released.** The
        // previous version dropped the reference instead, which left the mic held by an object
        // nothing could reach — so the first failed open made every later attempt fail too, and
        // the mic button became a coin flip.
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        return recorder
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
        const val TAG = "LumiAsr"

        /** 200ms of 16kHz floats. Small enough for live partials, big enough to read efficiently. */
        const val BLOCK_SAMPLES = 3_200

        /**
         * Decoding threads for the recognizer. Mid-range hardware, short utterances — two keeps
         * latency low without fighting the resident model for cores. Tuned on the device in
         * Phase 6 if call mode needs more.
         */
        const val NUM_THREADS = 2

        /**
         * Quiet blocks after speech before the request is treated as finished. At 200ms a block
         * this is a little under a second — long enough to survive a pause for breath mid-sentence,
         * short enough that the user is not left staring at a live mic after they have stopped.
         */
        const val SILENT_BLOCKS_TO_FINISH = 4

        /**
         * How long a session waits for the user to begin before giving up. Roughly ten seconds.
         * Without this the mic stays open indefinitely when the tap was an accident.
         */
        const val MAX_SILENT_BLOCKS = 50

        /**
         * The floor no room can talk its way under, on the -1..1 RMS scale. Measured on the demo
         * device: a silent room peaks around 0.047, so this sits just above it. The learned
         * threshold takes over whenever the room is noisier than that.
         */
        const val ABSOLUTE_RMS_FLOOR = 0.055

        /**
         * How far above the room's own noise level a block must sit to count as speech. Speech at
         * arm's length measures several times a quiet room; four is comfortably inside that margin
         * while still catching someone speaking softly.
         */
        const val SPEECH_RMS_MULTIPLE = 4.0

        /** Blocks spent learning the room before speech detection starts. 200ms each. */
        const val CALIBRATION_BLOCKS = 3

        /**
         * Consecutive loud blocks before the session believes someone is talking. A single block is a
         * door closing or a knock on the desk; three in a row at 200ms each is a voice. This is what
         * stops an isolated noise spike above the threshold from arming the session.
         */
        const val LOUD_BLOCKS_TO_ARM = 3

        /**
         * Shorter than this and the transcript is noise that cleared the gate by luck. Two or three
         * characters cannot be a request worth acting on.
         */
        const val MIN_TRANSCRIPT_CHARS = 4
    }
}
