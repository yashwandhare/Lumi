package com.lumi.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumi.ui.components.LumiBlob
import com.lumi.ui.components.LumiGlassPanel
import com.lumi.ui.components.LumiIconButton
import com.lumi.ui.theme.spacing

/**
 * Which of the three voice phases the session is in. The screen a judge remembers: it carries
 * the voice-first claim on its own, so each phase is labelled in words — the label is the
 * state, and colour or animation only ever backs it up.
 */
enum class VoicePhase {
    LISTENING,
    THINKING,
    SPEAKING,
}

/**
 * The voice session surface: mascot-anchored, one phase at a time.
 *
 * - **Listening** shows the live transcript as words arrive, so the user can correct course
 *   before Lumi acts on them.
 * - **Thinking** is the chat screen's status verb applied to voice.
 * - **Speaking** says so plainly while the reply is read aloud.
 *
 * The one stop button always does the right thing for the phase: abandon the recording,
 * interrupt the reply, or silence the speaker. It is the same control the composer already
 * grew in Phase 1, moved to the middle where the session lives.
 */
@Composable
fun VoiceSessionOverlay(
    phase: VoicePhase,
    thinkingVerb: String,
    transcript: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Live sound level, 0f..1f. Drives the mascot's expansion — see [LumiBlob]. */
    soundLevel: Float = 0f,
) {
    LumiGlassPanel(
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The mascot sits in the middle of the screen, not at the top. It is the subject of this
            // screen rather than a header on it — the equal weights above and below are what centre
            // it, and the stop control is pinned to the bottom on its own.
            Spacer(Modifier.weight(1f))

            LumiBlob(
                modifier = Modifier.size(96.dp),
                // Never the typing pose here: that shrinks the mascot to get out of a keyboard's
                // way, and on this screen there is no keyboard and nothing to make room for.
                isTyping = false,
                eyeSize = androidx.compose.ui.unit.DpSize(width = 6.dp, height = 10.dp),
                soundLevel = soundLevel,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = phaseLabel(phase, thinkingVerb),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(MaterialTheme.spacing.md))

            AnimatedVisibility(
                visible = transcript.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.weight(1f))

            LumiIconButton(
                icon = Icons.Rounded.Stop,
                contentDescription = stopDescription(phase),
                onClick = onCancel,
                container = MaterialTheme.colorScheme.primary,
                tint = MaterialTheme.colorScheme.onPrimary,
                iconSize = 32.dp,
            )

            Spacer(Modifier.height(MaterialTheme.spacing.md))

            Text(
                text = stopCaption(phase),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun phaseLabel(phase: VoicePhase, thinkingVerb: String): String = when (phase) {
    VoicePhase.LISTENING -> "Listening"
    VoicePhase.THINKING -> thinkingVerb
    VoicePhase.SPEAKING -> "Speaking"
}

private fun stopCaption(phase: VoicePhase): String = when (phase) {
    VoicePhase.LISTENING -> "Stop listening"
    VoicePhase.THINKING -> "Stop the reply"
    VoicePhase.SPEAKING -> "Stop speaking"
}

private fun stopDescription(phase: VoicePhase): String = when (phase) {
    VoicePhase.LISTENING -> "Stop listening"
    VoicePhase.THINKING -> "Stop generating"
    VoicePhase.SPEAKING -> "Stop speaking"
}
