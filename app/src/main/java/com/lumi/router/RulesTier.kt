package com.lumi.router

import com.lumi.core.InteractionOrigin
import com.lumi.core.RouterDecision
import com.lumi.core.RouterTier
import com.lumi.core.StructuredIntent
import com.lumi.core.model.CapabilityId

/**
 * Router tier 1: deterministic rules. Zero latency, no model, always runs first.
 *
 * Catches what the cheaper read *already* decides — exact device commands and verb phrases
 * with an unambiguous marker — so they never spend an embedding on the question. The
 * similarity tier is for the fuzzy residue, not for certainties.
 *
 * **Order matters and encodes the "add" precedence the brief requires tested.** Rules are
 * tried top to bottom and the first match wins, so the list itself is the precedence rule:
 *
 * 1. File-ish verbs ("attach", "find my …") before reminders, because "attach my resume"
 *    contains no reminder word and must not drift under a fuzzy comparison.
 * 2. Search before mail: "search the web for mail addresses" is a web search, and a bare
 *    inbox phrase must still reach mail.
 * 3. Reminders before todos before routines: a routine sentence can mention a time and an
 *    action, and "remind me at 7 to turn on wifi" is a reminder, not a wifi routine. Marker
 *    verb first, action graph second.
 *
 * Device commands sit last among the specific groups: "when i get home turn on wifi" is a
 * routine with a wifi action, not a wifi command — the routine rule matches first.
 *
 * SOS phrases are gone from this tier — SOS was cut entirely; do not re-add without a
 * decision entry.
 *
 * A rule sets slots where it can name them (the device target and its value, the app to
 * open, the search query), and always keeps the raw text, because the capability shows the
 * user what was understood.
 */
internal object RulesTier {

    /** Which rule fired, recorded in a slot for tests and tuning. */
    const val SLOT_MARKER = "marker"

    /** Distinguishes the web query from a mail fetch for the network gate. */
    const val SLOT_KIND = "kind"

    private val rules: List<Rule> = listOf(
        Rule(CapabilityId.FILES) { normalized ->
            FILES.firstOrNull { it.matches(normalized) }?.pattern
        },
        Rule(CapabilityId.SEARCH) { normalized ->
            SEARCH.firstOrNull { it.matches(normalized) }?.pattern
        },
        Rule(CapabilityId.MAIL) { normalized ->
            MAIL.firstOrNull { it.matches(normalized) }?.pattern
        },
        Rule(CapabilityId.TOOLS, kind = KIND_REMINDER) { normalized ->
            REMINDER_RULES.firstOrNull { it.matches(normalized) }?.pattern
        },
        Rule(CapabilityId.TOOLS, kind = KIND_TODO) { normalized ->
            TODO_RULES.firstOrNull { it.matches(normalized) }?.pattern
        },
        Rule(CapabilityId.ROUTINE) { normalized ->
            ROUTINE.firstOrNull { it.matches(normalized) }?.pattern
        },
        Rule(CapabilityId.DEVICE) { normalized -> deviceRule(normalized)?.marker },
    )

    /**
     * @return a decision at [CONFIDENCE], or null when no rule claims the utterance.
     */
    fun route(normalized: String, rawText: String, origin: InteractionOrigin): RouterDecision? {
        for (rule in rules) {
            val marker = rule.match(normalized) ?: continue
            val slots = slotsFor(rule.capability, normalized).toMutableMap()
            slots[SLOT_MARKER] = marker
            rule.kind?.let { slots[SLOT_KIND] = it }
            return RouterDecision(
                intent = StructuredIntent(
                    capability = rule.capability,
                    rawText = rawText,
                    slots = slots,
                ),
                confidence = CONFIDENCE,
                tier = RouterTier.RULES,
            )
        }
        return null
    }

    /**
     * Exact device commands with their target and value lifted into slots.
     *
     * Anchored tightly on purpose: "make it quiet" is fuzzy and belongs to a later tier
     * (and needs a confirmation before touching the ringer anyway). These are the commands
     * that are already unambiguous as typed.
     *
     * Open-app captures the app name into the value slot — Phase 3's fuzzy app matcher
     * consumes it, stripping spaces and punctuation before comparing.
     */
    private fun deviceRule(normalized: String): DeviceCommand? = when {
        SILENCE_ON.any { it.matches(normalized) } -> DeviceCommand("silent_mode", "on")
        SILENCE_OFF.any { it.matches(normalized) } -> DeviceCommand("silent_mode", "off")
        WIFI_ON.any { it.matches(normalized) } -> DeviceCommand("wifi", "on")
        WIFI_OFF.any { it.matches(normalized) } -> DeviceCommand("wifi", "off")
        BLUETOOTH_ON.any { it.matches(normalized) } -> DeviceCommand("bluetooth", "on")
        BLUETOOTH_OFF.any { it.matches(normalized) } -> DeviceCommand("bluetooth", "off")
        FLASHLIGHT_ON.any { it.matches(normalized) } -> DeviceCommand("flashlight", "on")
        FLASHLIGHT_OFF.any { it.matches(normalized) } -> DeviceCommand("flashlight", "off")
        else -> OPEN_APP.firstNotNullOfOrNull { it.matchEntire(normalized) }
            ?.groups
            ?.get("app")
            ?.value
            ?.let { appName -> DeviceCommand("open_app", appName) }
    }

    private fun slotsFor(capability: CapabilityId, normalized: String): Map<String, String> {
        if (capability == CapabilityId.SEARCH) {
            return mapOf(
                StructuredIntent.SLOT_QUERY to normalized,
                SLOT_KIND to "web",
            )
        }
        if (capability == CapabilityId.MAIL) {
            return mapOf(SLOT_KIND to "fetch")
        }
        if (capability == CapabilityId.DEVICE) {
            return deviceRule(normalized)?.let { command ->
                mapOf(
                    StructuredIntent.SLOT_TARGET to command.target,
                    StructuredIntent.SLOT_VALUE to command.value,
                )
            }.orEmpty()
        }
        return emptyMap()
    }

    private val FILES = listOf(
        Regex("^(please )?attach \\S"),
        Regex("^(please )?(find|locate) (my|the) \\S"),
        Regex("\\bwhere is my \\S"),
        Regex("^(please )?open (my|the) .+\\b(file|pdf|photo|image|report|document|ticket|resume)"),
        // Document objects without a file-ish word beside them. Without this, "open my aadhaar
        // card" reaches the open-app rule and the marquee file-fetch demo misroutes.
        Regex("^(please )?open (my |the )?.*\\b(aadhaar|pan|passport|marksheet|certificate|" +
            "id|bill|invoice|receipt|letter|form)"),
    )

    private val SEARCH = listOf(
        Regex("^(please )?search (the web|online)\\b"),
        Regex("^web search\\b"),
        Regex("^(please )?look up "),
        Regex("\\bduckduckgo\\b"),
    )

    private val MAIL = listOf(
        Regex("^(please )?(check|fetch|read|show) (my |the )?(mail|gmail|inbox|emails?)\\b"),
        Regex("^(any )?new (mail|emails?)\\b"),
        Regex("^(do i have|got) (any )?(new )?(mail|emails?)\\b"),
        Regex("^what's in my inbox"),
    )

    private val REMINDER_RULES = listOf(
        Regex("^(please )?remind (me|us)\\b"),
        Regex("^(please )?(set|create|add|make|give me) (a |an )?reminder\\b"),
        Regex("\\breminder for\\b"),
        Regex("^(please )?alert me\\b"),
        Regex("^don'?t let me forget\\b"),
    )

    private val TODO_RULES = listOf(
        Regex("^(please )?(add|put|append) .+ (to|on) (my |the )?(list|checklist|to-?do)\\b"),
        Regex("^(please )?(create|add|make) (a |an )?(to-?do|task)\\b"),
        Regex("\\bto-?do list\\b"),
        Regex("^(please )?(add|put) .+ (to|on) my (shopping|grocery) list\\b"),
    )

    private val ROUTINE = listOf(
        Regex("^(please )?(set up|create|make|add|start|build) (a |an )?(routine|automation)\\b"),
        Regex("\\bautomate\\b"),
        Regex("^(please )?every (morning|evening|night|day|weekday|monday|tuesday|wednesday|" +
            "thursday|friday|saturday|sunday)\\b.*\\b(turn|open|set|switch|enable|disable)\\b"),
        Regex("^when (i|we|the phone)\\b.*\\b(turn|open|set|switch|enable|disable|put)\\b"),
    )

    private val SILENCE_ON = listOf(
        Regex("^(please )?(put|set|switch|turn) (the |my )?(phone|device) (on|to|in) " +
            "(silent|vibrate|do not disturb|dnd)"),
        Regex("^(please )?(silence|mute) (the |my )?(phone|device)"),
    )

    private val SILENCE_OFF = listOf(
        Regex("^(please )?(take|switch|turn) (the |my )?(phone|device) (off|out of) " +
            "(silent|vibrate|do not disturb|dnd)"),
        Regex("^(please )?unmute (the |my )?(phone|device)"),
    )

    private val WIFI_ON = listOf(
        Regex("^(please )?(turn|switch) on (the )?wi-?fi"),
        Regex("^(please )?enable (the )?wi-?fi"),
        Regex("^(please )?wi-?fi on"),
    )

    private val WIFI_OFF = listOf(
        Regex("^(please )?(turn|switch) off (the )?wi-?fi"),
        Regex("^(please )?disable (the )?wi-?fi"),
        Regex("^(please )?wi-?fi off"),
    )

    private val BLUETOOTH_ON = listOf(
        Regex("^(please )?(turn|switch) on (the )?bluetooth"),
        Regex("^(please )?enable (the )?bluetooth"),
        Regex("^(please )?bluetooth on"),
    )

    private val BLUETOOTH_OFF = listOf(
        Regex("^(please )?(turn|switch) off (the )?bluetooth"),
        Regex("^(please )?disable (the )?bluetooth"),
        Regex("^(please )?bluetooth off"),
    )

    private val FLASHLIGHT_ON = listOf(
        Regex("^(please )?(turn|switch) (on )?the flashlight"),
        Regex("^(please )?flashlight on"),
        Regex("^(please )?enable the flashlight"),
    )

    private val FLASHLIGHT_OFF = listOf(
        Regex("^(please )?(turn|switch) off the flashlight"),
        Regex("^(please )?flashlight off"),
        Regex("^(please )?disable the flashlight"),
    )

    private val OPEN_APP = listOf(
        Regex("^(please )?(open|launch|start) (the |app )?(?<app>\\S+)$"),
    )

    private const val CONFIDENCE = 0.99f

    /** [SLOT_KIND] values for the two TOOLS routes, shared with the dispatcher and tests. */
    const val KIND_REMINDER = "reminder"
    const val KIND_TODO = "todo"
}

private class Rule(
    val capability: CapabilityId,
    val kind: String? = null,
    val match: (String) -> String?,
)

private class DeviceCommand(val target: String, val value: String) {
    /** Recorded into the marker slot: which command fired. */
    val marker: String get() = "$target:$value"
}
