package com.lumi.reminders

import com.lumi.core.model.ReminderKind

/**
 * The text a reminder row keeps, cleaned of the scaffolding the user spoke around it.
 *
 * "remind me to call mom at six" stores "call mom at six"; the notification reads the title,
 * and a title that starts with "remind me" repeats itself. Pure on purpose: the capability
 * calls it and the JVM tests fix the wording without an Android runtime. Anything that does
 * not match a rule falls back to the utterance itself — a garbled title is worse than a
 * longer one.
 */
object ReminderTitle {

    fun clean(utterance: String, kind: ReminderKind): String {
        val softened = utterance.trim()
            .replace(POLITENESS, "")
            .trim()

        val byPatterns = when (kind) {
            ReminderKind.TODO -> TODO_PATTERNS.firstNotNullOfOrNull { it.extract(softened) }
            ReminderKind.REMINDER -> REMINDER_PATTERNS.firstNotNullOfOrNull { it.extract(softened) }
        }

        val cleaned = (byPatterns ?: COMMON.firstNotNullOfOrNull { it.extract(softened) } ?: softened).trim()
        return cleaned.ifEmpty { utterance.trim() }
    }

    private val POLITENESS = Regex("^(please|hey lumi|hey|hi)\\s+", RegexOption.IGNORE_CASE)

    private val COMMON = listOf(
        Rule(Regex("^remind me (?:to|that|about)\\s+(.+)", RegexOption.IGNORE_CASE)),
        Rule(Regex("^remind me\\s+(.+)", RegexOption.IGNORE_CASE)),
        Rule(Regex("^alert me (?:to|about)\\s+(.+)", RegexOption.IGNORE_CASE)),
        Rule(Regex("^don'?t let me forget\\s+(?:to\\s+)?(.+)", RegexOption.IGNORE_CASE)),
    )

    private val REMINDER_PATTERNS = listOf(
        Rule(Regex("^(?:set|create|add|make|give me) (?:a |an )?reminder (?:for|about|to)\\s+(.+)", RegexOption.IGNORE_CASE)),
    )

    private val TODO_PATTERNS = listOf(
        // "add milk to my shopping list" keeps the item, not the list it landed on.
        Rule(
            Regex(
                "^(?:add|put|append)\\s+(.+?)\\s+(?:to|on)\\s+(?:my |the )?.*?(?:list|checklist|to-?do)s?\\s*$",
                RegexOption.IGNORE_CASE,
            ),
        ),
        Rule(Regex("^(?:create|add|make)\\s+(?:a |an )?(?:to-?do|task)\\s+(?:to|for)\\s+(.+)", RegexOption.IGNORE_CASE)),
        Rule(Regex("^(?:create|add|make)\\s+(?:a |an )?(?:to-?do|task)\\s+(.+)", RegexOption.IGNORE_CASE)),
        Rule(Regex("^note down that\\s+(.+)", RegexOption.IGNORE_CASE)),
    )

    private class Rule(private val pattern: Regex) {
        fun extract(text: String): String? =
            pattern.find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }
}
