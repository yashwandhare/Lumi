package com.lumi.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The defaults are load-bearing, not decoration: they came off v1's shipped model allowlist, tuned
 * against this same Gemma build. A silent edit here changes the behaviour of every conversation, so
 * they are pinned by a test rather than by a comment.
 */
class ModelSettingsTest {

    @Test
    fun `defaults match the values v1 shipped for this model`() {
        val defaults = ModelSettings()

        assertEquals(64, defaults.topK)
        assertEquals(0.95f, defaults.topP, 0f)
        assertEquals(1.0f, defaults.temperature, 0f)
        assertEquals(4096, defaults.maxTokens)
    }

    @Test
    fun `the CPU is the default backend, so a failing GPU cannot strand a first run`() {
        assertEquals(ModelBackend.CPU, ModelSettings().backend)
    }

    @Test
    fun `every default sits inside the range the settings screen allows`() {
        val defaults = ModelSettings()

        assertTrue(defaults.topK in ModelSettings.TOP_K_RANGE)
        assertTrue(defaults.topP in ModelSettings.TOP_P_RANGE)
        assertTrue(defaults.temperature in ModelSettings.TEMPERATURE_RANGE)
        assertTrue(defaults.maxTokens in ModelSettings.MAX_TOKENS_RANGE)
    }

    @Test
    fun `the shipped persona is the default system prompt`() {
        assertEquals(LumiPersona.CHAT, ModelSettings().systemPrompt)
    }

    @Test
    fun `the persona states the boundaries that decisions_md treats as hard rules`() {
        val prompt = LumiPersona.CHAT.lowercase()

        // Journaling boundary: never a therapist or emotional companion.
        assertTrue("persona must refuse the therapist framing", "therapist" in prompt)
        // No autonomous action on anything with real consequence.
        assertTrue("persona must require confirmation first", "wait to be told" in prompt)
        // Offline by default.
        assertTrue("persona must state it has no internet", "no internet access" in prompt)
    }

    @Test
    fun `the persona omits the SOS tool instruction until the tool exists`() {
        // Carried from v1 deliberately unfinished: instructing the model to call a tool that is not
        // built would promise an action nothing performs. Restore it with the tool, in Phase 5.
        assertTrue("sos_emergency" !in LumiPersona.CHAT)
    }
}
