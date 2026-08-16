package com.lumi.router

import com.lumi.core.InteractionOrigin
import com.lumi.core.model.CapabilityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tier-1 rules are the certainty layer, so the tests assert the outcomes a misroute would hurt
 * most: the "add" collisions, device commands with their slots, and that a fuzzy sentence the
 * rules cannot settle falls through (null) rather than being claimed.
 *
 * Every assertion reads through [route] on already-normalized text — the router normalizes
 * upstream, so these give the tier exactly what it will see in production.
 */
class RulesTierTest {

    private fun route(text: String) =
        RulesTier.route(InputNormalizer.normalize(text), text, InteractionOrigin.TEXT)

    // --- The "add" collisions the brief names explicitly. ---

    @Test
    fun `add with a list destination is a todo, not a reminder or a device action`() {
        val decision = route("add milk to my shopping list")!!
        assertEquals(CapabilityId.TOOLS, decision.intent.capability)
        assertEquals(RulesTier.KIND_TODO, decision.intent[RulesTier.SLOT_KIND])
    }

    @Test
    fun `remind me with a time is a reminder, not a todo and not a routine`() {
        val decision = route("remind me to call mom at six")!!
        assertEquals(CapabilityId.TOOLS, decision.intent.capability)
        assertEquals(RulesTier.KIND_REMINDER, decision.intent[RulesTier.SLOT_KIND])
    }

    @Test
    fun `a reminder to change a device setting stays a reminder, not a device command`() {
        val decision = route("remind me at seven to turn on wifi")!!
        assertEquals(CapabilityId.TOOLS, decision.intent.capability)
        assertEquals(RulesTier.KIND_REMINDER, decision.intent[RulesTier.SLOT_KIND])
    }

    @Test
    fun `set a reminder is a reminder even though set also opens device verbs`() {
        val decision = route("set a reminder for my appointment")!!
        assertEquals(CapabilityId.TOOLS, decision.intent.capability)
        assertEquals(RulesTier.KIND_REMINDER, decision.intent[RulesTier.SLOT_KIND])
    }

    // --- File requests win over attach-adjacent verbs. ---

    @Test
    fun `attach routes to files, not reminders, despite sharing add-ish verbs`() {
        val decision = route("attach my resume to the chat")!!
        assertEquals(CapabilityId.FILES, decision.intent.capability)
    }

    @Test
    fun `open a document object routes to files, not to opening an app`() {
        val decision = route("open my aadhaar card")!!
        assertEquals(CapabilityId.FILES, decision.intent.capability)
    }

    // --- Device commands carry their target and value as slots. ---

    @Test
    fun `silence the phone produces a silent_mode-on device intent`() {
        val decision = route("put my phone on silent")!!
        assertEquals(CapabilityId.DEVICE, decision.intent.capability)
        assertEquals("silent_mode", decision.intent[com.lumi.core.StructuredIntent.SLOT_TARGET])
        assertEquals("on", decision.intent[com.lumi.core.StructuredIntent.SLOT_VALUE])
    }

    @Test
    fun `turn on wifi captures the wifi toggle`() {
        val decision = route("turn on wi-fi")!!
        assertEquals(CapabilityId.DEVICE, decision.intent.capability)
        assertEquals("wifi", decision.intent[com.lumi.core.StructuredIntent.SLOT_TARGET])
        assertEquals("on", decision.intent[com.lumi.core.StructuredIntent.SLOT_VALUE])
    }

    @Test
    fun `open an app names the app in the value slot`() {
        val decision = route("open WhatsApp")!!
        assertEquals(CapabilityId.DEVICE, decision.intent.capability)
        assertEquals("open_app", decision.intent[com.lumi.core.StructuredIntent.SLOT_TARGET])
        assertEquals("whatsapp", decision.intent[com.lumi.core.StructuredIntent.SLOT_VALUE])
    }

    // --- Search and mail, ordered so they do not steal each other's phrases. ---

    @Test
    fun `search the web is a web search even when the query mentions mail`() {
        val decision = route("search the web for my mail address")!!
        assertEquals(CapabilityId.SEARCH, decision.intent.capability)
        assertEquals("web", decision.intent[RulesTier.SLOT_KIND])
    }

    @Test
    fun `check the inbox is mail, not a web search`() {
        val decision = route("check my gmail")!!
        assertEquals(CapabilityId.MAIL, decision.intent.capability)
        assertEquals("fetch", decision.intent[RulesTier.SLOT_KIND])
    }

    // --- Routines. ---

    @Test
    fun `a when-then device graph is a routine, not a bare device command`() {
        val decision = route("when i get home turn on wifi")!!
        assertEquals(CapabilityId.ROUTINE, decision.intent.capability)
    }

    // --- The fall-through: fuzzy prose must not be claimed by tier 1. ---

    @Test
    fun `a conversational question no rule settles returns null`() {
        assertNull(route("what is a black hole"))
    }

    @Test
    fun `vague add without a destination is left for the similarity tier`() {
        assertNull(route("add a thing"))
    }

    @Test
    fun `the raw text survives into the intent so the capability shows what was said`() {
        val raw = "Remind  Me at  SIX"
        val decision = RulesTier.route(InputNormalizer.normalize(raw), raw, InteractionOrigin.VOICE)
        assertNotNull(decision)
        assertEquals(raw, decision!!.intent.rawText)
    }
}
