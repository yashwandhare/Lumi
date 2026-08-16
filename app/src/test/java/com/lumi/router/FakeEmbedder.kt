package com.lumi.router

import com.lumi.core.ai.Embedder
import com.lumi.core.ai.EmbedderState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Deterministic embedder for router tests: returns scripted vectors for exact texts and
 * nothing else.
 *
 * Scripted vectors keep these tests honest about what they verify: threshold, ordering, and
 * ambiguity logic are checked against cosine values the test chose, not against whatever an
 * embedding model felt like producing. What the tests deliberately do not cover — whether
 * EmbeddingGemma's real scores separate the phrase lists well enough — needs a device and is
 * called out in the phase report.
 */
internal class FakeEmbedder(
    initialState: EmbedderState = EmbedderState.Ready,
    private val vectors: Map<String, FloatArray> = emptyMap(),
) : Embedder {

    override val state: StateFlow<EmbedderState> = MutableStateFlow(initialState)

    override suspend fun prepare() {
        // Stays in whatever state it was built with; a dead embedder does not revive itself.
    }

    override suspend fun embedDocument(text: String): FloatArray? = vectors[text]

    override suspend fun embedQuery(text: String): FloatArray? = vectors[text]

    override fun dimensions(): Int? = vectors.values.firstOrNull()?.size
}
