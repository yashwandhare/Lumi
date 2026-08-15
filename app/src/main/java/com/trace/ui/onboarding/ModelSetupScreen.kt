package com.trace.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trace.core.ai.GemmaModel
import com.trace.core.ai.ModelState
import com.trace.ui.components.TraceBlob
import com.trace.ui.components.TraceGlassPanel
import com.trace.ui.theme.TraceShape
import com.trace.ui.theme.TraceSize
import com.trace.ui.theme.spacing

/**
 * First run. The screen where Trace fetches its model.
 *
 * Anchored on the mascot rather than on a progress bar, because this is the user's first sight of
 * the app and a bare bar for several minutes is the least reassuring thing it could show. The
 * mascot is already gated on reduced motion and battery saver, so it costs nothing to be still when
 * stillness was asked for.
 *
 * Every number on this screen is real: the size comes from the pinned artefact, the percentage from
 * bytes actually on disk. Nothing here estimates a time, because a download over an unknown
 * connection cannot be estimated honestly, and a wrong estimate is worse than none.
 *
 * @param onReady called once, when the model is loaded and the app can start.
 */
@Composable
fun ModelSetupScreen(
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val awaitingConsent by viewModel.awaitingConsent.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is ModelState.Ready) onReady()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = MaterialTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TraceBlob(modifier = Modifier.padding(bottom = MaterialTheme.spacing.xl))

        when {
            awaitingConsent -> ConsentStep(onDownload = viewModel::start)
            else -> SetupProgress(state = state, onRetry = viewModel::retry)
        }
    }
}

/**
 * The ask. Says what it costs and what it buys, in that order.
 *
 * The offline promise is the product's whole point, so it is stated here rather than in a marketing
 * screen the user would skip — this is the one moment the download makes sense to them.
 */
@Composable
private fun ConsentStep(onDownload: () -> Unit) {
    Text(
        text = "Trace runs on your phone.",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "It needs to download its model once — ${GemmaModel.HUMAN_SIZE}. " +
            "After that everything works with no internet, and nothing you say leaves the device.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = MaterialTheme.spacing.md),
    )
    Text(
        text = "Best done on Wi-Fi. You can close Trace and come back — the download picks up where " +
            "it stopped.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = MaterialTheme.spacing.sm),
    )
    Button(
        onClick = onDownload,
        shape = TraceShape.default,
        modifier = Modifier.padding(top = MaterialTheme.spacing.xl),
    ) {
        Text("Download model", style = MaterialTheme.typography.labelLarge)
    }
}

/** Everything after consent: downloading, loading, ready, or failed. */
@Composable
private fun SetupProgress(state: ModelState, onRetry: () -> Unit) {
    when (state) {
        is ModelState.Downloading -> {
            Text(
                text = "Downloading the model",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            TraceGlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.spacing.lg),
            ) {
                Column(modifier = Modifier.padding(MaterialTheme.spacing.md)) {
                    LinearProgressIndicator(
                        progress = { state.fraction ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MaterialTheme.spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            // Real bytes on disk, not a synthetic animation. A bar that moves when
                            // nothing is happening is how an app teaches users to distrust it.
                            text = "${state.downloadedBytes.asGigabytes()} of ${GemmaModel.HUMAN_SIZE}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = state.fraction?.let { "${(it * 100).toInt()}%" } ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = "Safe to leave this screen. Trace keeps going.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.md),
            )
        }

        ModelState.Loading -> {
            Text(
                text = "Starting the model",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                // Named honestly, and warned about honestly. A cold load is tens of seconds on this
                // class of hardware; a user who was not told assumes the app has hung.
                text = "This takes up to a minute the first time. It is quicker after that.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.md),
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.spacing.lg),
            )
        }

        is ModelState.Unavailable -> {
            Text(
                text = state.reason,
                style = MaterialTheme.typography.headlineSmall,
                // Not `error` red. §3 puts hierarchy in size and weight, and a red headline makes a
                // resumable download look like a broken app.
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (state.recoverable) {
                    "Nothing downloaded so far was lost. Trace will carry on from where it stopped."
                } else {
                    "Trace cannot run its model on this device. Everything that does not need the " +
                        "model still works."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.md),
            )
            if (state.recoverable) {
                Button(
                    onClick = onRetry,
                    shape = TraceShape.default,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.lg),
                ) {
                    Text("Resume download", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Absent is the moment between tapping Download and the first byte arriving, and Ready is
        // the frame before the caller navigates away. Both are too brief to put a message on, and a
        // flash of text the user cannot read is worse than a beat of quiet.
        ModelState.Absent, ModelState.Ready -> Spacer(Modifier.height(MaterialTheme.spacing.xxl))
    }
}

/** One decimal place. Two would imply a precision the user has no use for. */
private fun Long.asGigabytes(): String = "%.1f GB".format(this / 1_000_000_000.0)


