package com.lumi.ui.chat

import com.lumi.core.StructuredIntent
import com.lumi.core.model.CapabilityId
import com.lumi.router.RulesTier

/**
 * An understood intent waiting for the user's yes.
 *
 * The surface exists because Lumi can change real settings and fetch real things: the
 * confirmation shows the interpreted action in plain words, with the sentence that produced
 * it shown alongside, so a wrong guess is visible before it becomes a wrong action.
 */
data class PendingIntent(
    val intent: StructuredIntent,
    val description: String,
)

/**
 * One line of plain language describing what the router understood, for the confirmation
 * surface. The user reads this before Lumi acts, so it says the action in their words, not a
 * capability id, and never echoes a slot key or an enum name.
 *
 * Pure on purpose: the confirmation UI renders it and a JVM test can assert on the wording
 * without a Compose runtime. Lives in `ui` rather than `core` because describing an intent
 * to a person in their words is presentation, even though the input is a router contract.
 */
fun describeIntent(intent: StructuredIntent): String = when (intent.capability) {
    CapabilityId.CHAT -> "Have a conversation"

    CapabilityId.DEVICE -> describeDevice(intent)

    CapabilityId.SEARCH -> "Search the web"

    CapabilityId.MAIL -> "Check your mail"

    CapabilityId.FILES -> "Find a file"

    CapabilityId.ROUTINE -> "Create a routine"

    CapabilityId.TOOLS -> when (intent[RulesTier.SLOT_KIND]) {
        RulesTier.KIND_REMINDER -> "Set a reminder"
        RulesTier.KIND_TODO -> "Add a to-do"
        else -> "Capture something to do"
    }

    CapabilityId.JOURNAL -> "Write a journal entry"
    CapabilityId.MEMORY -> "Remember this"
    CapabilityId.CALL -> "Follow a meeting"
    CapabilityId.NOTIFICATIONS -> "Read your notifications"
    CapabilityId.RAG -> "Search your own notes"
}

private fun describeDevice(intent: StructuredIntent): String {
    val target = intent[StructuredIntent.SLOT_TARGET] ?: return "Change a device setting"
    val value = intent[StructuredIntent.SLOT_VALUE]
    return when (target) {
        "wifi" -> if (value == "on") "Turn wifi on" else "Turn wifi off"
        "bluetooth" -> if (value == "on") "Turn Bluetooth on" else "Turn Bluetooth off"
        "flashlight" -> if (value == "on") "Turn the flashlight on" else "Turn the flashlight off"
        "silent_mode" -> if (value == "on") "Silence the phone" else "Un-silence the phone"
        "open_app" -> "Open ${value ?: "an app"}"
        else -> "Change a device setting"
    }
}
