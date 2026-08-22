package com.lumi.ui.chat

import com.lumi.core.ai.GenerationMetrics
import com.lumi.core.model.ReminderKind

/**
 * Status shown while the model works, rotated so a long wait does not read as a frozen screen.
 *
 * The verbs are deliberately playful — the mascot is the only playful thing in the app, and this label
 * sits directly under it. They are also honest: every one of them means "still working", which is the
 * only claim being made. Nothing here estimates progress, because a 2B model on a phone cannot report
 * how far through a reply it is.
 */
internal val THINKING_VERBS = listOf(
    "Pondering",
    "Drafting",
    "Cooking",
    "Resonating",
    "Mulling",
    "Thinking it over",
    "Turning it over",
    "Chewing on it",
    "Working",
    "Composing",
    "Percolating",
    "Considering",
)

/**
 * A structured outcome rendered as a card rather than prose.
 *
 * A confirmed reminder reads better as a titled surface than as a sentence — the title is what
 * will ring, the detail is when. [detail] is null when there is genuinely nothing to add: a
 * plain todo has no time by nature, so stating its absence would be noise. Only the fields the
 * card can honestly show; anything richer belongs on the reminders screen.
 */
data class ChatTurnCard(
    val title: String,
    val detail: String?,
    val kind: ReminderKind,
)

/** One turn on screen. */
data class ChatTurn(
    val role: TurnRole,
    val text: String,
    val streaming: Boolean = false,
    /** Attached to a model turn once it finishes, when the user has metrics switched on. */
    val metrics: GenerationMetrics? = null,
    /** Set when the turn's outcome is a captured item, rendered as [ChatTurnCard]. */
    val card: ChatTurnCard? = null,
)

enum class TurnRole { USER, MODEL }
