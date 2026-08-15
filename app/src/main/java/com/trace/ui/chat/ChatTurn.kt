package com.trace.ui.chat

import com.trace.core.ai.GenerationMetrics

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

/** One turn on screen. */
data class ChatTurn(
    val role: TurnRole,
    val text: String,
    val streaming: Boolean = false,
    /** Attached to a model turn once it finishes, when the user has metrics switched on. */
    val metrics: GenerationMetrics? = null,
)

enum class TurnRole { USER, MODEL }
