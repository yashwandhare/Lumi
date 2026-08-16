package com.lumi.router

import com.lumi.core.ai.EmbedderState
import com.lumi.core.model.CapabilityId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tier-2 logic verified against scripted cosine values.
 *
 * Phrase vectors are fixed per group and the utterance vector is chosen per test, so the
 * threshold, the ambiguity gap, and the marker-tie-break each run against numbers the test
 * picked. Whether EmbeddingGemma's real embeddings separate the phrase lists well enough is a
 * device measurement, not something a JVM test can answer.
 *
 * Angles used (unit vectors, cosine = cos of the angle between):
 * - REMINDER phrases on A = (1, 0)
 * - TODO phrases on B = (0.6, 0.8), so A and B meet at ~26.6 degrees
 * - utterances on the unit circle at the angle each case names.
 */
class SimilarityTierTest {

    @Test
    fun `a clear winner above the threshold routes with its group's capability and kind`() = runBlocking {
        val tier = tierWith(
            phraseVectors = mapOf(
                PhraseGroup.REMINDER to vec(1.0, 0.0),
                PhraseGroup.TODO to vec(0.6, 0.8),
            ),
            queryVectors = mapOf("i need to pick up some groceries" to vec(cos(45), sin(45))),
        )

        val answer = tier.route("i need to pick up some groceries", rawText = "I need to pick up some groceries")

        assertNotNull(answer)
        val decision = answer!!.decision
        assertNotNull(decision)
        assertEquals(CapabilityId.TOOLS, decision!!.intent.capability)
        assertEquals(RulesTier.KIND_TODO, decision.intent[RulesTier.SLOT_KIND])
    }

    @Test
    fun `a marker word in the utterance resolves a near tie instead of asking`() = runBlocking {
        // TODO phrases sit at ~53 degrees, REMINDER at 0 degrees. An utterance at 28 degrees
        // scores ~0.905 vs TODO and ~0.883 vs REMINDER — a 0.02 gap, inside the ambiguity gap,
        // so without help the router would ask. The utterance itself says "remind", which
        // answers its own question, and the marker path must return REMINDER without asking.
        val tier = tierWith(
            phraseVectors = mapOf(
                PhraseGroup.REMINDER to vec(1.0, 0.0),
                PhraseGroup.TODO to vec(0.6, 0.8),
            ),
            queryVectors = mapOf("remind me to buy bread and milk" to vec(cos(28), sin(28))),
        )

        val answer = tier.route("remind me to buy bread and milk", rawText = "Remind me to buy bread and milk")

        assertNotNull(answer)
        val decision = answer!!.decision
        assertNotNull(decision)
        assertNull(answer.ambiguousWith)
        assertEquals(RulesTier.KIND_REMINDER, decision!!.intent[RulesTier.SLOT_KIND])
    }

    @Test
    fun `two groups inside the ambiguity gap ask instead of acting`() = runBlocking {
        // Utterance at 5 degrees sits ~0.015 from both REMINDER (0 degrees) and RAG (10 degrees).
        val tier = tierWith(
            phraseVectors = mapOf(
                PhraseGroup.REMINDER to vec(1.0, 0.0),
                PhraseGroup.RAG to vec(cos(10), sin(10)),
            ),
            queryVectors = mapOf("what did i write about" to vec(cos(5), sin(5))),
        )

        val answer = tier.route("what did i write about", rawText = "What did I write about")

        assertNotNull(answer)
        assertNull(answer!!.decision)
        assertEquals(
            listOf(PhraseGroup.REMINDER, PhraseGroup.RAG),
            answer.ambiguousWith.orEmpty().sortedBy { it.ordinal },
        )
    }

    @Test
    fun `nothing above the threshold returns null so the caller can fall back`() = runBlocking {
        val tier = tierWith(
            phraseVectors = mapOf(
                PhraseGroup.REMINDER to vec(1.0, 0.0),
                PhraseGroup.TODO to vec(0.6, 0.8),
            ),
            // Pointing away from both group directions: cosine negative against both, so the
            // tier must hand back null rather than force a classification.
            queryVectors = mapOf("hello there" to vec(0.0, -1.0)),
        )

        assertNull(tier.route("hello there", rawText = "Hello there"))
    }

    @Test
    fun `a down embedder degrades to lexical scoring and still routes an exact phrase`() = runBlocking {
        val tier = SimilarityTier(FakeEmbedder(initialState = EmbedderState.Unavailable("no model", false)))

        val answer = tier.route("add eggs bread and butter to my list", rawText = "Add eggs, bread and butter to my list")

        // The utterance matches a todo phrase word for word — Jaccard 1.0 over the fallback,
        // and the "my list" marker resolves it without asking.
        assertNotNull(answer)
        val decision = answer!!.decision
        assertNotNull(decision)
        assertEquals(RulesTier.KIND_TODO, decision!!.intent[RulesTier.SLOT_KIND])
    }

    @Test
    fun `a ready embedder that fails every embed call degrades the same way`() = runBlocking {
        val tier = SimilarityTier(
            FakeEmbedder(initialState = EmbedderState.Ready, vectors = mapOf("the weather is fine" to floatArrayOf(1.0f, 0.0f)))
        )

        // Vectors exist only for unrelated text, so every phrase embed fails and the tier must
        // fall back rather than score everything zero on a dead cache.
        val answer = tier.route("add eggs bread and butter to my list", rawText = "Add eggs bread and butter to my list")

        assertNotNull(answer)
        val decision = answer!!.decision
        assertNotNull(decision)
        assertEquals(CapabilityId.TOOLS, decision!!.intent.capability)
    }

    @Test
    fun `a blank utterance scores nothing`() = runBlocking {
        val tier = SimilarityTier(FakeEmbedder())
        assertNull(tier.route("", rawText = ""))
        assertNull(tier.route("   ", rawText = "   "))
    }

    private fun tierWith(
        phraseVectors: Map<PhraseGroup, DoubleArray>,
        queryVectors: Map<String, DoubleArray>,
    ): SimilarityTier {
        val vectors = buildMap<String, FloatArray> {
            IntentPhrases.byGroup.forEach { (group, phrases) ->
                val groupVector = phraseVectors[group] ?: return@forEach
                phrases.forEach { phrase -> put(phrase, floatOf(groupVector)) }
            }
            queryVectors.forEach { (text, vector) -> put(text, floatOf(vector)) }
        }
        return SimilarityTier(FakeEmbedder(vectors = vectors))
    }

    private fun vec(x: Double, y: Double) = doubleArrayOf(x, y)

    private fun floatOf(vector: DoubleArray) =
        FloatArray(vector.size) { vector[it].toFloat() }

    private fun cos(degrees: Int): Double = kotlin.math.cos(Math.toRadians(degrees.toDouble()))

    private fun sin(degrees: Int): Double = kotlin.math.sin(Math.toRadians(degrees.toDouble()))
}
