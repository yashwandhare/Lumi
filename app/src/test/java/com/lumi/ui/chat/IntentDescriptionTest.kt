package com.lumi.ui.chat

import com.lumi.core.StructuredIntent
import com.lumi.core.model.CapabilityId
import com.lumi.router.RulesTier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The confirmation surface quotes [describeIntent] verbatim, so these pin the wording a user
 * actually sees: plain language, the action not the capability id, and no slot keys leaking.
 */
class IntentDescriptionTest {

    @Test
    fun `a wifi on command reads as turning wifi on`() {
        val description = describeIntent(deviceIntent(target = "wifi", value = "on"))
        assertEquals("Turn wifi on", description)
    }

    @Test
    fun `a wifi off command reads as turning wifi off`() {
        val description = describeIntent(deviceIntent(target = "wifi", value = "off"))
        assertEquals("Turn wifi off", description)
    }

    @Test
    fun `a flashlight off command reads as turning it off`() {
        val description = describeIntent(deviceIntent(target = "flashlight", value = "off"))
        assertEquals("Turn the flashlight off", description)
    }

    @Test
    fun `silent mode on reads as silencing the phone`() {
        val description = describeIntent(deviceIntent(target = "silent_mode", value = "on"))
        assertEquals("Silence the phone", description)
    }

    @Test
    fun `opening an app names the app`() {
        val description = describeIntent(deviceIntent(target = "open_app", value = "spotify"))
        assertEquals("Open spotify", description)
    }

    @Test
    fun `a device intent without a target stays honest`() {
        val description = describeIntent(
            StructuredIntent(capability = CapabilityId.DEVICE, rawText = "do something")
        )
        assertEquals("Change a device setting", description)
    }

    @Test
    fun `a reminder kind reads differently from a to do`() {
        val reminder = describeIntent(toolsIntent(RulesTier.KIND_REMINDER))
        val todo = describeIntent(toolsIntent(RulesTier.KIND_TODO))

        assertEquals("Set a reminder", reminder)
        assertEquals("Add a to-do", todo)
    }

    @Test
    fun `a tools intent with no kind is a plain capture`() {
        val description = describeIntent(
            StructuredIntent(capability = CapabilityId.TOOLS, rawText = "note thing")
        )
        assertEquals("Capture something to do", description)
    }

    @Test
    fun `a search intent says web search`() {
        val description = describeIntent(
            StructuredIntent(
                capability = CapabilityId.SEARCH,
                rawText = "search trains to pune",
                slots = mapOf(StructuredIntent.SLOT_QUERY to "trains to pune"),
            )
        )
        assertEquals("Search the web", description)
    }

    @Test
    fun `a chat intent names the conversation, not the model`() {
        val description = describeIntent(
            StructuredIntent(capability = CapabilityId.CHAT, rawText = "tell me a joke")
        )
        assertEquals("Have a conversation", description)
    }

    @Test
    fun `no description leaks a slot key or enum name`() {
        val descriptions = CapabilityId.entries.map { capability ->
            describeIntent(
                StructuredIntent(
                    capability = capability,
                    rawText = "anything",
                    slots = mapOf(
                        StructuredIntent.SLOT_TARGET to "wifi",
                        StructuredIntent.SLOT_VALUE to "on",
                        RulesTier.SLOT_KIND to RulesTier.KIND_REMINDER,
                    ),
                )
            )
        }
        // A run of uppercase letters means an enum or slot-key name leaked into the wording.
        descriptions.forEach { description ->
            assertEquals(
                "leaked a constant name: $description",
                description,
                description.replace(Regex("[A-Z_]{2,}"), ""),
            )
        }
    }

    private fun deviceIntent(target: String, value: String) =
        StructuredIntent(
            capability = CapabilityId.DEVICE,
            rawText = "turn it $value",
            slots = mapOf(
                StructuredIntent.SLOT_TARGET to target,
                StructuredIntent.SLOT_VALUE to value,
            ),
        )

    private fun toolsIntent(kind: String) =
        StructuredIntent(
            capability = CapabilityId.TOOLS,
            rawText = "add a thing",
            slots = mapOf(RulesTier.SLOT_KIND to kind),
        )
}
