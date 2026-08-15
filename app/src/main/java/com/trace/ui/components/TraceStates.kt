package com.trace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.trace.ui.theme.LocalMotionEnabled
import com.trace.ui.theme.spacing

/**
 * The shared loading state. Sibling of [EmptyState]; same composition, same restraint.
 *
 * **[operation] names the real work.** "Retrieving your notes…", "Loading Gemma into memory…",
 * "Reading your PDF…" — never "Loading…". A user who knows what is happening waits; a user staring
 * at a spinner assumes the app is stuck. This is a hard rule in `for_devb.md`, not a preference.
 *
 * @param operation what Trace is doing right now, phrased as a continuing action.
 * @param detail optional second line for something slow enough to need reassurance — a byte count,
 *   an item count, or the reason it is taking a while.
 * @param progress 0f..1f when the work has a real measurable end, null when it does not. Never fake
 *   a fraction: a bar that crawls to 90% and stops is worse than no bar.
 */
@Composable
fun LoadingState(
    operation: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    progress: Float? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = MaterialTheme.spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingBody(operation = operation, detail = detail, progress = progress)
    }
}

/**
 * The text and the bar, without the full-screen frame, so an inline caller — a sheet, a list
 * footer — gets the same wording and the same motion rules.
 */
@Composable
fun LoadingBody(
    operation: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    progress: Float? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = operation,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.sm),
            )
        }

        // An indeterminate bar is a permanent animation, which is exactly what a user asking for
        // reduced motion or sitting in battery saver does not want. With motion off, a *determinate*
        // bar still earns its place — it carries real information and only moves when the work
        // does — but the indeterminate one is dropped entirely and the wording carries the state.
        val animate = LocalMotionEnabled.current
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.spacing.lg),
            )
        } else if (animate) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.spacing.lg),
            )
        }
    }
}

/**
 * The shared error state. Sibling of [EmptyState] and [LoadingState].
 *
 * `for_devb.md` requires three things of every error, and the parameters exist so none of them can
 * be skipped by accident:
 *
 * 1. **What happened**, in plain language. Not an exception, not a code.
 * 2. **What to do about it.** An error the user cannot act on should not be a screen.
 * 3. **Whether it partly completed.** "Six of ten notes were indexed" changes what the user does
 *    next; silence about it makes them redo work or trust a half-finished result.
 *
 * Never pass raw exception text to [problem]. Log the exception; tell the user what it means.
 *
 * @param problem what went wrong, from the user's point of view.
 * @param next the action that resolves or works around it.
 * @param partial what did succeed, if anything. Null when the operation was atomic — say nothing
 *   rather than saying "nothing was saved" on an operation that never writes partially.
 * @param onRetry supply only when retrying is genuinely likely to behave differently. A retry button
 *   that fails identically teaches the user the app is broken.
 */
@Composable
fun ErrorState(
    problem: String,
    next: String,
    modifier: Modifier = Modifier,
    partial: String? = null,
    retryLabel: String = "Try again",
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = MaterialTheme.spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = problem,
            style = MaterialTheme.typography.headlineSmall,
            // Not `error`. §3 forbids hierarchy by colour, and a red headline makes a recoverable
            // problem read as a failure. The words carry the severity; the colour stays calm.
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (partial != null) {
            Text(
                text = partial,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.sm),
            )
        }
        Text(
            text = next,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.md),
        )
        if (onRetry != null) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.padding(top = MaterialTheme.spacing.md),
            ) {
                Text(retryLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}


