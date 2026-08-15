package com.lumi.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumi.core.ai.GemmaModel
import com.lumi.core.ai.ModelState
import com.lumi.ui.components.LumiBlob
import com.lumi.ui.components.LumiSleepingBlob
import com.lumi.ui.components.LumiGlassPanel
import com.lumi.ui.theme.LumiShape
import com.lumi.ui.theme.LumiSize
import com.lumi.ui.theme.spacing

/**
 * First run. The screen where Lumi fetches its model.
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
        // Asleep while the model loads, awake for everything else. The easter egg: Lumi is not
        // "starting", it is being woken up, and the mascot says so before the heading does.
        if (state is ModelState.Loading) {
            LumiSleepingBlob(
                // Padding before size, deliberately. The other order — `.size().padding()` — applies the
                // inset *inside* the sized box, leaving 96x64 for the body and rendering the slime as a
                // wide capsule no corner radius can fix.
                modifier = Modifier
                    .padding(bottom = MaterialTheme.spacing.xl)
                    .size(width = SLEEPING_MASCOT_WIDTH, height = SLEEPING_MASCOT_HEIGHT),
            )
        } else {
            LumiBlob(modifier = Modifier.padding(bottom = MaterialTheme.spacing.xl))
        }

        when {
            awaitingConsent -> ConsentStep(onDownload = viewModel::start)
            else -> SetupProgress(state = state, onRetry = viewModel::retry)
        }
    }
}

/**
 * The ask, and the introduction.
 *
 * This is the first thing anyone sees, and before this it was a size and a button — the user was asked
 * to spend 2.6GB before being told what they were getting. So it leads with what Lumi *is*, gives
 * three concrete things it does, and only then asks. Concrete beats adjectives: "answer questions about
 * your own notes" is a thing you can picture, "powerful AI assistant" is not.
 *
 * Still short. Three lines and a button, not a carousel — an onboarding flow the user has to page
 * through before a 2.6GB download is a worse first impression than one that gets out of the way.
 */
@Composable
private fun ConsentStep(onDownload: () -> Unit) {
    Text(
        text = "This is Lumi.",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "A private assistant that runs on your phone. Not in the cloud — on the phone in your hand.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = MaterialTheme.spacing.md),
    )

    Column(
        modifier = Modifier.padding(top = MaterialTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        CapabilityLine("Ask it things, with no signal and no account.")
        CapabilityLine("Keep notes and documents it can answer from.")
        CapabilityLine("Set routines that run themselves while the app is closed.")
    }

    Text(
        text = "Nothing you type or store ever leaves the device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = MaterialTheme.spacing.lg),
    )

    Button(
        onClick = onDownload,
        shape = LumiShape.default,
        modifier = Modifier.padding(top = MaterialTheme.spacing.lg),
    ) {
        Text("Download model · ${GemmaModel.HUMAN_SIZE}", style = MaterialTheme.typography.labelLarge)
    }
    Text(
        text = "One time, Wi-Fi recommended. It resumes if interrupted.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = MaterialTheme.spacing.sm),
    )
}

/** One thing Lumi does. The accent dot is the only colour on this screen. */
@Composable
private fun CapabilityLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp, end = MaterialTheme.spacing.sm)
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Everything after consent: downloading, loading, ready, or failed. */
@Composable
private fun SetupProgress(state: ModelState, onRetry: () -> Unit) {
    when (state) {
        is ModelState.Downloading -> {
            Text(
                text = "Downloading",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            LumiGlassPanel(
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
                            // Speed and percentage together: the percentage says how far, the speed
                            // says whether it is still moving. A stalled download at 3.9% looks
                            // identical to a slow one without it.
                            text = listOfNotNull(
                                state.bytesPerSecond?.asTransferRate(),
                                state.fraction?.let { "${(it * 100).toInt()}%" },
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = "Safe to leave this screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.md),
            )
        }

        ModelState.Loading -> {
            Text(
                text = "Waking up Lumi",
                // Smaller than the other headings here. There is nothing to decide on this screen and
                // nothing to read — the mascot is the content, and the text is a caption for it.
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(
                // Hairline rather than the Material default, and no track: a heavy bar under a sleeping
                // mascot reads as machinery. Rounded ends so the sliver never looks clipped.
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .padding(top = MaterialTheme.spacing.lg)
                    .height(2.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
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
                    "Nothing was lost. Lumi picks up where it stopped."
                } else {
                    "Everything that does not need the model still works."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MaterialTheme.spacing.md),
            )
            if (state.recoverable) {
                Button(
                    onClick = onRetry,
                    shape = LumiShape.default,
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

/**
 * Narrower and slightly shorter than the home-screen mascot's 69dp, keeping a small wide-base bias.
 *
 * The width/height gap stays deliberately small. `RoundedCornerShape` percentages resolve against the
 * *smaller* dimension, so a box much wider than tall turns the top corners into flat vertical sides and
 * the mascot becomes a capsule — that is how three earlier attempts failed. At an 8dp gap the corner
 * percentages still dominate the silhouette, and the extra width only settles the base.
 */
private val SLEEPING_MASCOT_WIDTH = 66.dp
private val SLEEPING_MASCOT_HEIGHT = 58.dp

/** One decimal place. Two would imply a precision the user has no use for. */
private fun Long.asGigabytes(): String = "%.1f GB".format(this / 1_000_000_000.0)

/**
 * MB/s above a megabyte, KB/s below it.
 *
 * Decimal units, matching how connections are advertised and how [asGigabytes] reports size, so the
 * two numbers on this row are in the same system.
 */
private fun Long.asTransferRate(): String = when {
    this >= 1_000_000 -> "%.1f MB/s".format(this / 1_000_000.0)
    else -> "${this / 1_000} KB/s"
}


