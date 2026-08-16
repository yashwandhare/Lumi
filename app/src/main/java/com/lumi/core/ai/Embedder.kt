package com.lumi.core.ai

import kotlinx.coroutines.flow.StateFlow

/**
 * Turns text into a vector.
 *
 * Two callers, one model: the router's similarity tier classifies intent by comparing an utterance
 * against labelled example phrases, and RAG compares a question against indexed note chunks. Both are
 * cosine similarity over the same vector space, so a second embedder would be a second thing to keep
 * consistent for no benefit.
 *
 * **The interface exists so the model is swappable.** EmbeddingGemma is the best quality available
 * without an access token, but it costs 180MB and a few hundred milliseconds per call on mid-range
 * hardware. Universal Sentence Encoder is 6MB and 10ms with weaker retrieval. If the latency proves
 * unacceptable on the target device, the swap should be one implementation of this interface, not a
 * rewrite of the router and RAG.
 */
interface Embedder {

    val state: StateFlow<EmbedderState>

    /** Fetch and load if needed. Idempotent; never throws. Failures land in [state]. */
    suspend fun prepare()

    /**
     * Embed text for storage — a note chunk, or a router example phrase.
     *
     * Separate from [embedQuery] because EmbeddingGemma is trained with task prefixes: a document and a
     * question about that document are encoded differently on purpose, and using one prefix for both
     * measurably degrades retrieval. Implementations without prefixes can treat the two identically.
     */
    suspend fun embedDocument(text: String): FloatArray?

    /** Embed text that is looking for something — a chat message, or a search query. */
    suspend fun embedQuery(text: String): FloatArray?

    /** Vector length of this embedder's output, or null until it has loaded. */
    fun dimensions(): Int?
}

sealed interface EmbedderState {
    data object Absent : EmbedderState
    data class Downloading(val fraction: Float?, val downloadedBytes: Long) : EmbedderState
    data object Loading : EmbedderState
    data object Ready : EmbedderState

    /**
     * Unusable. Unlike the generative model this is not fatal to the app — chat still works without an
     * embedder; only retrieval and the router's similarity tier are lost, and both have cheaper
     * fallbacks. So the reason is recorded and the app carries on.
     */
    data class Unavailable(val reason: String, val recoverable: Boolean) : EmbedderState
}

/**
 * Cosine similarity between two vectors of equal length.
 *
 * Lives here rather than in a caller because the router and RAG both need it and would otherwise each
 * have a copy. Returns 0 for a zero-magnitude vector instead of dividing by zero — an empty or
 * unembeddable string should score as "unrelated to everything", not crash a search.
 */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "vectors must match: ${a.size} vs ${b.size}" }
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    if (normA == 0f || normB == 0f) return 0f
    return dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
}
