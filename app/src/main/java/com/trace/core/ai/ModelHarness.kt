package com.trace.core.ai

import com.trace.core.settings.ModelBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where the model is in its lifecycle.
 *
 * Exposed as state rather than hidden behind a blocking call because loading takes long
 * enough that the UI must show honest progress. Dev B binds load and download UI to this.
 */
sealed interface ModelState {

    /** No model on device yet. First run, or the user cleared storage. */
    data object Absent : ModelState

    /**
     * [fraction] is 0f..1f, or null when the server gave no content length.
     *
     * [bytesPerSecond] is a smoothed recent rate, or null before there is enough of a sample to be
     * honest about one. It is deliberately not turned into a time estimate: a remaining-time figure
     * over a phone connection is wrong often enough that it erodes trust in everything else on screen.
     */
    data class Downloading(
        val fraction: Float?,
        val downloadedBytes: Long,
        val bytesPerSecond: Long? = null,
    ) : ModelState

    data object Loading : ModelState

    data object Ready : ModelState

    /**
     * The model cannot be used. [reason] is plain language for the user.
     *
     * [recoverable] distinguishes "no network, try again" from "this device cannot run it".
     * Features must degrade to a non-model path here rather than appearing to hang.
     */
    data class Unavailable(val reason: String, val recoverable: Boolean) : ModelState
}

/** Non-text content in a request. Plain classes: they hold arrays and are never compared. */
sealed interface ModelAttachment {
    class Image(val bytes: ByteArray) : ModelAttachment
    class Audio(val bytes: ByteArray) : ModelAttachment
}

/**
 * Sampling parameters.
 *
 * [Structured] exists because parsing a routine or extracting a reminder needs the model to
 * emit parseable output, not creative output. Using conversational temperature for those
 * paths produces JSON that fails to parse a few percent of the time, which reads to the
 * user as the feature being broken at random.
 */
data class Sampling(
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val maxTokens: Int,
) {
    companion object {
        /** Conversation. Tune against real device numbers, not these placeholders. */
        val Default = Sampling(temperature = 0.45f, topP = 0.9f, topK = 36, maxTokens = 640)

        /** Anything that must parse: routine structure, reminder extraction, classification. */
        val Structured = Sampling(temperature = 0.1f, topP = 0.5f, topK = 8, maxTokens = 384)
    }
}

/**
 * Identifies a conversation whose history the model should keep.
 *
 * A null session means a one-shot inference with no history and no side effects — the right
 * choice for background work like parsing a routine, because such a call must not appear in
 * the user's chat or disturb its context.
 */
@JvmInline
value class SessionId(val value: String)

data class GenerationRequest(
    val prompt: String,
    val systemInstruction: String? = null,
    val attachments: List<ModelAttachment> = emptyList(),
    /** Null means "use the user's settings", which is what the chat path wants. */
    val sampling: Sampling? = null,
    val sessionId: SessionId? = null,
)

/**
 * How the last reply performed.
 *
 * [tokensPerSecond] is measured from the first token onward, not from the send — prefill on a 2B model
 * is a second or more and folding it in would report a number unrelated to how fast text appears.
 * [timeToFirstTokenMs] is that prefill, reported separately because it is the part felt as lag.
 *
 * [approxTokens] is approximate and labelled as such wherever it is shown: there is no tokenizer on
 * this side of the JNI boundary, so it is derived from character count.
 */
data class GenerationMetrics(
    val totalMs: Long,
    val timeToFirstTokenMs: Long,
    val approxTokens: Int,
    val tokensPerSecond: Double?,
)

/**
 * The single entry point to on-device generation.
 *
 * Nothing else in the app touches the inference runtime. That is deliberate: the model is
 * loaded exactly once and kept resident for the process lifetime, and a second owner would
 * eventually reload it. Cold load cost real seconds on v1's hardware, so a per-request load
 * makes the app unusable rather than merely slow.
 *
 * Implemented in Phase 1 against the LiteRT-LM Kotlin API. The interface exists now so the
 * router, capabilities, and load UI can all be written against it in parallel.
 */
interface ModelHarness {

    val state: StateFlow<ModelState>

    /** Timings from the most recent reply, or null before one has completed. */
    val lastMetrics: StateFlow<GenerationMetrics?>

    /**
     * Fetch the model if absent, then load it. Idempotent and safe to call from anywhere;
     * concurrent callers join the same work rather than starting a second load.
     *
     * Never throws. Failures land in [state] as [ModelState.Unavailable] so every caller
     * sees the same truth.
     */
    suspend fun prepare()

    /**
     * Drop the engine and load it again, for when the backend preference changes.
     *
     * Every conversation is closed with it — a `Conversation` belongs to the `Engine` that created it,
     * and outliving that engine is a native crash rather than a stale object.
     */
    suspend fun reload()

    /** Which backend is actually loaded, which may differ from the request if Auto fell back. */
    fun activeBackend(): ModelBackend?

    /** Streamed generation. Emits text as it is produced. */
    fun generate(request: GenerationRequest): Flow<String>

    /** Collects [generate] into one string. For structured output that has no partial value. */
    suspend fun complete(request: GenerationRequest): String

    /** Forget a conversation's history without unloading the model. */
    suspend fun resetSession(sessionId: SessionId)
}
