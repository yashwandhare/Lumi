package com.lumi.ui.chat

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lumi.core.ai.GenerationMetrics
import com.lumi.core.model.ReminderKind
import com.lumi.ui.components.LumiCardRow
import com.lumi.ui.components.MarkdownText
import com.lumi.ui.theme.LocalMotionEnabled
import com.lumi.ui.theme.LumiShape
import com.lumi.ui.theme.spacing

/**
 * The transcript.
 *
 * A [LazyColumn] rather than a scrolling `Column`: a long conversation composes only what is visible,
 * and v1 had a real jank bug from getting this wrong.
 *
 * The user's turn sits in a surface bubble; the model's runs as plain text on the background. That
 * asymmetry is deliberate — the model's reply is the content of the screen, and boxing it would make a
 * two-paragraph answer read as a quoted aside. It also keeps the accent away from the transcript, which
 * §2 reserves for actions.
 */
@Composable
fun ChatTranscript(
    turns: List<ChatTurn>,
    thinkingVerb: String?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    /**
     * Whether the view should track the growing reply.
     *
     * Dropped the moment the user drags, restored when they let go at the bottom. Reading real drag
     * interactions rather than `isScrollInProgress` matters: that flag is also true during *our own*
     * programmatic scrolls, so using it made the transcript's own scrolling look like user input and
     * the follow state flickered.
     */
    var following by remember { mutableStateOf(true) }
    var userDragging by remember { mutableStateOf(false) }

    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    userDragging = true
                    following = false
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> userDragging = false
            }
        }
    }

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + BOTTOM_SLACK_PX
        }
    }

    // Resume following only once the finger is off and the view is actually at the end.
    LaunchedEffect(userDragging, atBottom) {
        if (!userDragging && atBottom) following = true
    }

    // Keyed on the last turn's length as well as the count, so it tracks a single reply as it grows
    // rather than only jumping when a whole turn is added.
    LaunchedEffect(turns.size, turns.lastOrNull()?.text?.length, following) {
        if (following && turns.isNotEmpty()) {
            // Pin the *bottom* of the last item. Plain `scrollToItem(index)` puts that item's top at
            // the top of the viewport, which for a reply taller than the screen jumps the reader to
            // the start of the message and then re-jumps there on every token — so trying to reach the
            // end of a long answer felt like the list refusing to scroll down. The offset overshoots
            // deliberately and the list clamps it to its real maximum.
            listState.scrollToItem(turns.lastIndex, scrollOffset = MAX_SCROLL_OFFSET_PX)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        contentPadding = PaddingValues(vertical = MaterialTheme.spacing.md),
    ) {
        items(turns) { turn ->
            when (turn.role) {
                TurnRole.USER -> UserTurn(turn.text)
                TurnRole.MODEL -> ModelTurn(turn, thinkingVerb)
            }
        }
    }
}

@Composable
private fun UserTurn(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = text,
            // bodyMedium, not bodyLarge. A phone-width transcript at 17sp fits very few words per
            // line, so a reply broke into a tall column of fragments; 15sp reads as prose.
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                // Never full width: a bubble spanning the screen stops reading as one side of a
                // conversation.
                .widthIn(max = 300.dp)
                .clip(LumiShape.default)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(
                    horizontal = MaterialTheme.spacing.md,
                    vertical = MaterialTheme.spacing.sm,
                ),
        )
    }
}

@Composable
private fun ModelTurn(turn: ChatTurn, thinkingVerb: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Before the first token there is nothing to show but the fact that something is happening.
        if (turn.streaming && turn.text.isEmpty()) {
            ThinkingLabel(verb = thinkingVerb ?: THINKING_VERBS.first())
        } else if (turn.card != null) {
            // A captured item reads as its own surface — the title is what will ring, the
            // detail is when. The confirmation sentence above it stays plain text.
            LumiCardRow(
                title = turn.card.title,
                detail = turn.card.detail,
                icon = when (turn.card.kind) {
                    ReminderKind.TODO -> Icons.Rounded.Checklist
                    ReminderKind.REMINDER -> Icons.Rounded.Alarm
                },
            )
        } else {
            // Rendered as markdown, including mid-stream. Half-finished syntax is tolerated by the
            // parser — an unclosed `**` or an open fence just renders as text until its partner
            // arrives, which looks like the reply still being written rather than like a glitch.
            MarkdownText(
                text = turn.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        turn.metrics?.let { metrics ->
            Text(
                text = metrics.summary(),
                style = MaterialTheme.typography.labelSmall,
                // Fainter than secondary text. This is a footnote about the reply, not part of it —
                // at full onSurfaceVariant it competed with the answer directly above it.
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = MaterialTheme.spacing.xs),
            )
        }
    }
}

/**
 * The status while the model works: one verb, and dots that animate.
 *
 * The verb is fixed for the whole reply — chosen once when the send happens. The motion lives in the
 * dots instead, which is where it belongs: a changing word pulls the eye and reads as a retry, while
 * moving dots read as ongoing work and are ignorable.
 *
 * Both the dots and the breathing are gated on [LocalMotionEnabled]. With motion off the label renders
 * with all three dots showing rather than animating or hiding, so it still says "working" without
 * moving.
 */
@Composable
private fun ThinkingLabel(verb: String) {
    val motionEnabled = LocalMotionEnabled.current

    val dotCount = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "thinking")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = (DOT_MAX + 1).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(DOT_CYCLE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "thinkingDots",
        )
        progress.toInt().coerceIn(0, DOT_MAX)
    } else {
        DOT_MAX
    }

    val alpha = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "thinkingPulse")
        val pulse by transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_100, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "thinkingAlpha",
        )
        pulse
    } else {
        0.75f
    }

    Text(
        // Trailing dots are padded to a fixed width so the text does not jitter as they cycle.
        text = verb + ".".repeat(dotCount) + " ".repeat(DOT_MAX - dotCount),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.alpha(alpha),
    )
}

/**
 * "1.4s · ~12 tok/s".
 *
 * Time to first token is included only when it is a meaningful share of the wait — on a fast reply it
 * is noise, and on a slow one it is the whole story. "~" on the rate is not decoration: there is no
 * tokenizer available here, so the count is derived from characters and the tilde says so.
 */
private fun GenerationMetrics.summary(): String = buildList {
    add("%.1fs".format(totalMs / 1000.0))
    tokensPerSecond?.let { add("~%.0f tok/s".format(it)) }
    if (timeToFirstTokenMs > SLOW_FIRST_TOKEN_MS) {
        add("%.1fs to first token".format(timeToFirstTokenMs / 1000.0))
    }
}.joinToString("  ·  ")

private const val SLOW_FIRST_TOKEN_MS = 1_500L

/**
 * How near the bottom still counts as "at the bottom".
 *
 * Without slack, a reply whose last line lands a pixel below the viewport would drop the view out of
 * follow mode and it would stop tracking mid-generation.
 */
private const val BOTTOM_SLACK_PX = 120

/**
 * Deliberate overshoot for the follow scroll, clamped by the list to its true maximum.
 *
 * Large enough that no single reply exceeds it, small enough not to risk overflow in the list's own
 * arithmetic the way Int.MAX_VALUE would.
 */
private const val MAX_SCROLL_OFFSET_PX = 1_000_000

/** Three dots, cycling once per [DOT_CYCLE_MS]. */
private const val DOT_MAX = 3
private const val DOT_CYCLE_MS = 1_400
