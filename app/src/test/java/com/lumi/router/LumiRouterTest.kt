package com.lumi.router

import com.lumi.core.InteractionOrigin
import com.lumi.core.RouterOutcome
import com.lumi.core.RouterTier
import com.lumi.core.StructuredIntent
import com.lumi.core.ai.EmbedderState
import com.lumi.core.model.CapabilityId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The composed router, exercised end to end (normalize → rules → similarity → fallback).
 *
 * Rule claims are asserted with the embedder deliberately offline, so a wrong answer here
 * points at rule ordering or the fallback path, not at an embedding model. Similarity and
 * ambiguity cases give the fake embedder scripted vectors; the two are separated on purpose so
 * each tier's responsibility is visible in whichever test fails.
 */
class LumiRouterTest {

    // --- Rules win even when an embedder would rather disagree. ---

    @Test
    fun `an exact device command routes through rules, not similarity`() = runBlocking {
        val router = LumiRouter(FakeEmbedder(initialState = EmbedderState.Unavailable("no embedder", false)))

        val outcome = router.route("turn on the flashlight", InteractionOrigin.VOICE)

        val decision = (outcome as RouterOutcome.Routed).decision
        assertEquals(CapabilityId.DEVICE, decision.intent.capability)
        assertEquals("flashlight", decision.intent[StructuredIntent.SLOT_TARGET])
        assertEquals(RouterTier.RULES, decision.tier)
    }

    @Test
    fun `a reminder whose action is a device change stays a reminder`() = runBlocking {
        val router = LumiRouter(FakeEmbedder(initialState = EmbedderState.Unavailable("no embedder", false)))

        val outcome = router.route("remind me at seven to turn on wifi", InteractionOrigin.TEXT)

        val decision = (outcome as RouterOutcome.Routed).decision
        assertEquals(CapabilityId.TOOLS, decision.intent.capability)
        assertEquals(RulesTier.KIND_REMINDER, decision.intent[RulesTier.SLOT_KIND])
    }

    @Test
    fun `every core intent named in the brief resolves to its capability`() = runBlocking {
        val router = LumiRouter(FakeEmbedder(initialState = EmbedderState.Unavailable("no embedder", false)))

        val cases = mapOf(
            "set a reminder for standup" to (CapabilityId.TOOLS to RulesTier.KIND_REMINDER),
            "add the report to my todo list" to (CapabilityId.TOOLS to RulesTier.KIND_TODO),
            "set up a routine for the morning" to (CapabilityId.ROUTINE to null),
            "find my college id" to (CapabilityId.FILES to null),
            "search the web for train times" to (CapabilityId.SEARCH to "web"),
            "check my gmail" to (CapabilityId.MAIL to "fetch"),
        )

        cases.forEach { (utterance, expected) ->
            val outcome = router.route(utterance, InteractionOrigin.TEXT)
            val decision = (outcome as RouterOutcome.Routed).decision
            assertEquals("capability for: $utterance", expected.first, decision.intent.capability)
            assertEquals("kind for: $utterance", expected.second, decision.intent[RulesTier.SLOT_KIND])
        }
    }

    // --- Similarity picks up what no rule claims. ---

    @Test
    fun `a reminder phrased without any rule verb routes by similarity`() = runBlocking {
        // "remember" is not a tier-1 verb, and nothing else in the sentence matches a rule, so
        // this must reach the similarity tier. Reminder phrases sit at (1,0), todos at (0,1),
        // and the utterance points almost exactly at the reminder direction.
        val router = LumiRouter(
            embedder(
                groupVectors = mapOf(
                    PhraseGroup.REMINDER to floatArrayOf(1f, 0f),
                    PhraseGroup.TODO to floatArrayOf(0f, 1f),
                ),
                queries = mapOf("i have to remember to take my medicine at noon" to floatArrayOf(0.966f, 0.259f)),
            )
        )

        val outcome = router.route("i have to remember to take my medicine at noon", InteractionOrigin.VOICE)

        val decision = (outcome as RouterOutcome.Routed).decision
        assertEquals(CapabilityId.TOOLS, decision.intent.capability)
        assertEquals(RulesTier.KIND_REMINDER, decision.intent[RulesTier.SLOT_KIND])
        assertEquals(RouterTier.SIMILARITY, decision.tier)
    }

    // --- Ambiguity asks rather than guessing. ---

    @Test
    fun `two equally plausible groups produce an ambiguous outcome naming both`() = runBlocking {
        // Every reminder and todo phrase mapped to one direction, and the utterance matches it
        // exactly: the top two scores are tied, and no rule or marker can break the tie, so the
        // router must ask.
        val allPhrases = (IntentPhrases.byGroup[PhraseGroup.REMINDER].orEmpty() +
            IntentPhrases.byGroup[PhraseGroup.TODO].orEmpty())
        val vectors = allPhrases.associateWith { floatArrayOf(1f, 0f) }.toMutableMap()
        vectors["pick something up tomorrow"] = floatArrayOf(1f, 0f)
        val router = LumiRouter(FakeEmbedder(vectors = vectors))

        val outcome = router.route("pick something up tomorrow", InteractionOrigin.TEXT)

        val ambiguous = outcome as RouterOutcome.Ambiguous
        assertTrue("names the reminder option", ambiguous.question.contains("reminder"))
        assertTrue("names the todo option", ambiguous.question.contains("to-do list"))
        // Both groups dispatch to the same capability, so the candidate list is one entry.
        assertEquals(listOf(CapabilityId.TOOLS), ambiguous.candidates)
    }

    // --- The resting place. ---

    @Test
    fun `a request nothing claims falls back to chat`() = runBlocking {
        val router = LumiRouter(FakeEmbedder(initialState = EmbedderState.Unavailable("no embedder", false)))

        val outcome = router.route("what is a black hole", InteractionOrigin.TEXT)

        val decision = (outcome as RouterOutcome.Routed).decision
        assertEquals(CapabilityId.CHAT, decision.intent.capability)
        assertEquals("what is a black hole", decision.intent.rawText)
    }

    @Test
    fun `an empty utterance still routes to chat rather than throwing`() = runBlocking {
        val router = LumiRouter(FakeEmbedder(initialState = EmbedderState.Unavailable("no embedder", false)))

        val outcome = router.route("   ", InteractionOrigin.TEXT)
        assertNotNull(outcome)

        assertEquals(CapabilityId.CHAT, (outcome as RouterOutcome.Routed).decision.intent.capability)
    }

    /**
     * Gives every phrase of each named group one shared vector, plus per-utterance query
     * vectors. Unnamed groups embed to nothing, which the tier treats as absent.
     */
    private fun embedder(
        groupVectors: Map<PhraseGroup, FloatArray>,
        queries: Map<String, FloatArray>,
    ): FakeEmbedder {
        val vectors = queries.toMutableMap()
        groupVectors.forEach { (group, vector) ->
            IntentPhrases.byGroup[group].orEmpty().forEach { phrase -> vectors[phrase] = vector }
        }
        return FakeEmbedder(vectors = vectors)
    }
}
