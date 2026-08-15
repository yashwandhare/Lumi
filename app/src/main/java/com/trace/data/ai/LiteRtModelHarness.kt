package com.trace.data.ai

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
import com.trace.core.ai.GenerationRequest
import com.trace.core.ai.ModelAttachment
import com.trace.core.ai.ModelHarness
import com.trace.core.ai.ModelState
import com.trace.core.ai.Sampling
import com.trace.core.ai.SessionId
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
    @InferenceDispatcher private val inference: CoroutineDispatcher,
) : ModelHarness {

    private val _state = MutableStateFlow<ModelState>(ModelState.Absent)
    override val state: StateFlow<ModelState> = _state.asStateFlow()

    private val lock = Mutex()
    private var engine: Engine? = null
    private val conversations = mutableMapOf<String, Conversation>()

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
                when (val outcome = downloader.download { downloaded, _ ->
                    _state.value = ModelState.Downloading(fractionOf(downloaded), downloaded)
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
     * `Engine.initialize()` is a blocking native call, so it runs on the inference dispatcher. It is
     * also where a device that cannot run the model finds out, which is why the failure is reported
     * as unrecoverable: retrying the same load on the same hardware will fail the same way.
     */
    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    private suspend fun loadEngine(): ModelState = withContext(inference) {
        try {
            // "Universally recommended for all tasks on GPU backends" per Google's docs. Set before
            // initialize() or it is ignored. Measure it on the target device; drop it if unstable.
            ExperimentalFlags.enableSpeculativeDecoding = true

            val created = Engine(
                EngineConfig(
                    modelPath = store.modelFile.absolutePath,
                    backend = Backend.GPU(),
                    // Vision and audio share the GPU backend so "ask about this image" works without
                    // a second engine. PRD §5's top risk is whether this Gemma build accepts them at
                    // all; that is a device test, not something to assume here.
                    visionBackend = Backend.GPU(),
                    audioBackend = Backend.GPU(),
                    maxNumTokens = MAX_CONTEXT_TOKENS,
                    // The docs say a cache directory improves second-load time. It holds compiled
                    // kernels, not weights, so losing it costs seconds rather than 1.9GB.
                    cacheDir = store.engineCacheDir.absolutePath,
                )
            )
            created.initialize()
            engine = created
            ModelState.Ready
        } catch (t: Throwable) {
            engine = null
            ModelState.Unavailable(
                reason = "Trace could not start the model on this device.",
                recoverable = false,
            )
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
                conversation.sendMessageAsync(request.toContents()).collect { message ->
                    val text = message.text()
                    if (text.isEmpty()) return@collect
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
            } finally {
                // A one-shot leaves nothing behind: no history, nothing in the user's chat.
                if (request.sessionId == null) conversation.close()
            }
        }
    }.flowOn(inference)

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

    /**
     * A kept conversation per session id, or a throwaway for a one-shot.
     *
     * The system instruction and sampling belong to the conversation rather than the request, so a
     * session that was opened with one persona keeps it. A one-shot gets exactly what it asked for.
     */
    private fun conversationFor(engine: Engine, request: GenerationRequest): Conversation {
        val config = ConversationConfig(
            systemInstruction = Contents.of(request.systemInstruction ?: TracePersona.SYSTEM),
            samplerConfig = request.sampling.toSamplerConfig(),
        )
        val id = request.sessionId?.value ?: return engine.createConversation(config)
        return conversations.getOrPut(id) { engine.createConversation(config) }
    }

    private fun fractionOf(downloaded: Long): Float =
        (downloaded.toDouble() / com.trace.core.ai.GemmaModel.SIZE_BYTES).toFloat().coerceIn(0f, 1f)

    private companion object {
        /** Ceiling for the engine. Per-request limits live in [Sampling]. */
        const val MAX_CONTEXT_TOKENS = 4096
    }
}

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
