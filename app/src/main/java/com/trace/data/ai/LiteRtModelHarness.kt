package com.trace.data.ai

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.trace.core.ai.GenerationMetrics
import com.trace.core.ai.GenerationRequest
import com.trace.core.ai.ModelAttachment
import com.trace.core.ai.ModelHarness
import com.trace.core.ai.ModelState
import com.trace.core.ai.Sampling
import com.trace.core.ai.SessionId
import com.trace.core.settings.ModelBackend
import com.trace.core.settings.ModelSettings
import com.trace.data.settings.SettingsStore
import com.trace.di.InferenceDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only thing in Trace that touches the inference runtime.
 *
 * Three properties carry the design, all three from `decisions.md`:
 *
 * 1. **One owner.** Nothing else constructs an [Engine]. A second owner would eventually reload the
 *    model, and a cold load is measured in tens of seconds.
 * 2. **Serialised through one thread.** The native runtime holds one loaded model; two coroutines
 *    entering it concurrently is a native crash, not a catchable exception. The single-thread
 *    [InferenceDispatcher] is what prevents that, and [lock] additionally prevents two *logical*
 *    generations interleaving on one conversation.
 * 3. **Sessions are optional.** A null session is a one-shot with no history and no side effects —
 *    the right shape for parsing a routine, which must never appear in the user's chat.
 *
 * [prepare] never throws. Failures land in [state] so every caller sees one truth and can fall back
 * to a non-model path instead of hanging.
 */
@Singleton
class LiteRtModelHarness @Inject constructor(
    private val store: ModelStore,
    private val downloader: ModelDownloader,
    private val settings: SettingsStore,
    @InferenceDispatcher private val inference: CoroutineDispatcher,
) : ModelHarness {

    private val _state = MutableStateFlow<ModelState>(ModelState.Absent)
    override val state: StateFlow<ModelState> = _state.asStateFlow()

    private val _lastMetrics = MutableStateFlow<GenerationMetrics?>(null)
    override val lastMetrics: StateFlow<GenerationMetrics?> = _lastMetrics.asStateFlow()

    private val lock = Mutex()
    private var engine: Engine? = null
    private val conversations = mutableMapOf<String, Conversation>()

    /** Which backend the loaded engine is actually running on, for the settings screen to report. */
    private var loadedBackend: ModelBackend? = null

    /**
     * Idempotent by way of [lock]: a second caller waits and then sees [ModelState.Ready] rather
     * than starting a second download or a second load.
     */
    override suspend fun prepare() {
        lock.withLock {
            if (engine != null) {
                _state.value = ModelState.Ready
                return
            }

            if (!store.isReady()) {
                _state.value = ModelState.Downloading(
                    fraction = fractionOf(store.partialBytes()),
                    downloadedBytes = store.partialBytes(),
                )
                when (val outcome = downloader.download { downloaded, _, bytesPerSecond ->
                    _state.value = ModelState.Downloading(
                        fraction = fractionOf(downloaded),
                        downloadedBytes = downloaded,
                        bytesPerSecond = bytesPerSecond,
                    )
                }) {
                    is DownloadOutcome.Success -> Unit
                    is DownloadOutcome.Retryable -> {
                        _state.value = ModelState.Unavailable(outcome.reason, recoverable = true)
                        return
                    }
                    is DownloadOutcome.Permanent -> {
                        _state.value = ModelState.Unavailable(outcome.reason, recoverable = false)
                        return
                    }
                }
            }

            _state.value = ModelState.Loading
            _state.value = loadEngine()
        }
    }

    /**
     * Drop the engine and load it again — for when the backend preference changes.
     *
     * Conversations are closed with it, because a `Conversation` belongs to the `Engine` that made it
     * and outliving that engine is a native crash rather than a stale object. The user loses chat
     * history on the model's side, which is why the settings screen says so before switching.
     */
    override suspend fun reload() {
        lock.withLock {
            closeEverything()
            _state.value = ModelState.Loading
            _state.value = loadEngine()
        }
    }

    /** The backend actually in use, which may not be the one requested if Auto fell back. */
    override fun activeBackend(): ModelBackend? = loadedBackend

    /**
     * `Engine.initialize()` is a blocking native call, so it runs on the inference dispatcher.
     *
     * The attempts tried depend on the user's backend preference. `AUTO` tries GPU then falls back to
     * CPU; an explicit choice tries only that one, so a user who picked GPU is told it failed rather
     * than silently getting the slow path. The manifest declares `libOpenCL.so` at `required="false"`
     * on the promise that a device without OpenCL still runs — the CPU attempt is where that promise
     * is kept. Before the fallback existed, a GPU failure reported "cannot run on this device" on
     * hardware that runs it perfectly well on CPU.
     *
     * Every failure is logged with its cause. The user-facing string stays plain, but a swallowed
     * native exception makes this the one failure in the app nobody can diagnose.
     */
    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    private suspend fun loadEngine(): ModelState = withContext(inference) {
        var lastFailure: Throwable? = null
        val requested = settings.model.value.backend

        // Ask the model file what it supports instead of assuming. v1 did this and it matters: setting
        // the flag on a build without a draft model is one of the ways engine creation fails.
        val speculativeSupported = runCatching {
            com.google.ai.edge.litertlm.Capabilities(store.modelFile.absolutePath)
                .use { it.hasSpeculativeDecodingSupport() }
        }.onFailure { Log.w(TAG, "Could not read model capabilities", it) }
            .getOrDefault(false)

        for (attempt in attemptsFor(requested, speculativeSupported)) {
            try {
                // Set before initialize() or it is ignored.
                ExperimentalFlags.enableSpeculativeDecoding = attempt.speculativeDecoding

                val startedAtMs = System.currentTimeMillis()
                val created = Engine(
                    EngineConfig(
                        modelPath = store.modelFile.absolutePath,
                        backend = attempt.backend(),
                        // Both null until the multimodal path actually exists.
                        //
                        // This is what broke the GPU. Requesting a GPU audio backend fails engine
                        // creation outright — v1's config carries the comment "must be CPU" beside its
                        // audio backend for exactly this reason — and configuring backends for
                        // capabilities nothing in the app yet exercises bought nothing but that
                        // failure. Phase 4 sets them when image input lands, vision on GPU and audio
                        // on CPU, following v1.
                        visionBackend = null,
                        audioBackend = null,
                        maxNumTokens = MAX_CONTEXT_TOKENS,
                        // Null, deliberately. v1 passes a cache directory only for models loaded from
                        // /data/local/tmp and null for app-internal storage, which is where ours lives.
                        // A compiled-kernel cache is a second-load optimisation; a GPU that will not
                        // initialise at all is not an optimisation problem.
                        cacheDir = null,
                    )
                )
                created.initialize()
                engine = created
                loadedBackend = attempt.backend
                Log.i(
                    TAG,
                    "Model ready on ${attempt.label} in ${System.currentTimeMillis() - startedAtMs}ms" +
                        " (requested=$requested, speculativeSupported=$speculativeSupported)",
                )
                return@withContext ModelState.Ready
            } catch (t: Throwable) {
                lastFailure = t
                Log.w(TAG, "Model load failed on ${attempt.label}", t)
            }
        }

        engine = null
        loadedBackend = null
        Log.e(TAG, "Model load failed on every attempted backend (requested=$requested)", lastFailure)
        ModelState.Unavailable(
            reason = when (requested) {
                // An explicit GPU choice that fails is actionable: the user can switch to CPU. Saying
                // "cannot run on this device" there would be false, since CPU would work.
                ModelBackend.GPU -> "Trace could not start the model on the GPU. Try CPU in settings."
                else -> "Trace could not start the model on this device."
            },
            recoverable = requested == ModelBackend.GPU,
        )
    }

    /**
     * Speculative decoding is dropped before the backend is, because it is experimental API and the
     * likelier of the two to be what a given driver chokes on. It is only ever attempted when the model
     * file reports supporting it.
     */
    private fun attemptsFor(
        backend: ModelBackend,
        speculativeSupported: Boolean,
    ): List<LoadAttempt> {
        fun attempts(target: ModelBackend, factory: () -> Backend): List<LoadAttempt> = buildList {
            if (speculativeSupported) {
                add(LoadAttempt("${target.label} + speculative decoding", target, factory, true))
            }
            add(LoadAttempt(target.label, target, factory, false))
        }

        return when (backend) {
            ModelBackend.CPU -> attempts(ModelBackend.CPU) { Backend.CPU() }
            ModelBackend.GPU -> attempts(ModelBackend.GPU) { Backend.GPU() }
            ModelBackend.AUTO ->
                attempts(ModelBackend.GPU) { Backend.GPU() } +
                    attempts(ModelBackend.CPU) { Backend.CPU() }
        }
    }

    override fun generate(request: GenerationRequest): Flow<String> = flow {
        val ready = engine ?: run { prepare(); engine } ?: return@flow

        lock.withLock {
            val conversation = conversationFor(ready, request)
            try {
                // The streaming overload, not the blocking `sendMessage` — the docs prefer it for
                // coroutine code and it is what lets the chat render as the model decodes.
                var emitted = ""
                val startedAtMs = System.currentTimeMillis()
                var firstTokenAtMs = 0L

                conversation.sendMessageAsync(request.toContents()).collect { message ->
                    val text = message.text()
                    if (text.isEmpty()) return@collect
                    if (firstTokenAtMs == 0L) firstTokenAtMs = System.currentTimeMillis()
                    // The runtime may stream either cumulative text or per-token deltas depending on
                    // the build, and getting it wrong shows the user either duplicated or truncated
                    // output. Detecting which by prefix handles both without guessing.
                    if (text.length > emitted.length && text.startsWith(emitted)) {
                        emit(text.substring(emitted.length))
                        emitted = text
                    } else {
                        emit(text)
                        emitted += text
                    }
                }

                _lastMetrics.value = metricsFor(emitted, startedAtMs, firstTokenAtMs)
            } finally {
                // A one-shot leaves nothing behind: no history, nothing in the user's chat.
                if (request.sessionId == null) conversation.close()
            }
        }
    }.flowOn(inference)

    /**
     * Decode rate is measured from the first token, not from the send.
     *
     * Prefill on a 2B model is a second or more, and folding it into the rate would report a number
     * that has nothing to do with how fast text actually appears. Time-to-first-token is reported
     * separately because it is the part the user experiences as lag.
     *
     * Tokens are approximated at four characters each — there is no tokenizer on this side of the JNI
     * boundary, and the alternative to an approximation is no number at all. Labelled as approximate
     * wherever it is shown.
     */
    private fun metricsFor(text: String, startedAtMs: Long, firstTokenAtMs: Long): GenerationMetrics? {
        if (text.isBlank() || firstTokenAtMs == 0L) return null
        val finishedAtMs = System.currentTimeMillis()
        val decodeMs = finishedAtMs - firstTokenAtMs
        val approxTokens = (text.length / CHARS_PER_TOKEN).coerceAtLeast(1)
        return GenerationMetrics(
            totalMs = finishedAtMs - startedAtMs,
            timeToFirstTokenMs = firstTokenAtMs - startedAtMs,
            approxTokens = approxTokens,
            tokensPerSecond = if (decodeMs > 0) approxTokens * 1000.0 / decodeMs else null,
        )
    }

    override suspend fun complete(request: GenerationRequest): String {
        val builder = StringBuilder()
        generate(request).collect { builder.append(it) }
        return builder.toString()
    }

    override suspend fun resetSession(sessionId: SessionId) {
        lock.withLock {
            conversations.remove(sessionId.value)?.close()
        }
    }

    private fun closeEverything() {
        conversations.values.forEach { runCatching { it.close() } }
        conversations.clear()
        runCatching { engine?.close() }
        engine = null
        loadedBackend = null
    }

    /**
     * A kept conversation per session id, or a throwaway for a one-shot.
     *
     * The system instruction and sampling belong to the conversation rather than the request, so a
     * session opened with one persona keeps it. A one-shot gets exactly what it asked for. Both fall
     * back to the user's settings rather than to hardcoded values.
     */
    private fun conversationFor(engine: Engine, request: GenerationRequest): Conversation {
        val preferences = settings.model.value
        val config = ConversationConfig(
            systemInstruction = Contents.of(request.systemInstruction ?: preferences.systemPrompt),
            samplerConfig = (request.sampling ?: preferences.toSampling()).toSamplerConfig(),
        )
        val id = request.sessionId?.value ?: return engine.createConversation(config)
        return conversations.getOrPut(id) { engine.createConversation(config) }
    }

    private fun fractionOf(downloaded: Long): Float =
        (downloaded.toDouble() / com.trace.core.ai.GemmaModel.SIZE_BYTES).toFloat().coerceIn(0f, 1f)

    private companion object {
        const val TAG = "TraceModel"

        /** Ceiling for the engine. Per-reply limits come from the user's settings. */
        const val MAX_CONTEXT_TOKENS = 4096

        /** No tokenizer on this side of the JNI boundary, so token counts are approximated. */
        const val CHARS_PER_TOKEN = 4
    }
}

private class LoadAttempt(
    val label: String,
    val backend: ModelBackend,
    val backendFactory: () -> Backend,
    val speculativeDecoding: Boolean,
) {
    fun backend(): Backend = backendFactory()
}

/** The user's tuning, in the shape the request layer speaks. */
private fun ModelSettings.toSampling(): Sampling = Sampling(
    temperature = temperature,
    topP = topP,
    topK = topK,
    maxTokens = maxTokens,
)

/** Every emitted chunk's text, concatenated. Non-text parts are not part of a streamed reply. */
private fun Message.text(): String =
    contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

private fun GenerationRequest.toContents(): Contents = Contents.of(
    buildList {
        add(Content.Text(prompt))
        attachments.forEach { attachment ->
            when (attachment) {
                is ModelAttachment.Image -> add(Content.ImageBytes(attachment.bytes))
                is ModelAttachment.Audio -> add(Content.AudioBytes(attachment.bytes))
            }
        }
    }
)

/**
 * `topP` and `temperature` are `Double` in the native config while [Sampling] keeps them as `Float`,
 * which is the right type for a UI slider. Converting here keeps the widening in one place.
 */
private fun Sampling.toSamplerConfig(): SamplerConfig = SamplerConfig(
    topK = topK,
    topP = topP.toDouble(),
    temperature = temperature.toDouble(),
)
