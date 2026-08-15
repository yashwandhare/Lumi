package com.trace.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateContentSize
import com.trace.ui.theme.TraceShape
import com.trace.ui.theme.spacing

@Composable
fun TraceInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSendText: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Chat with Trace...",
    enabled: Boolean = true,
    /**
     * Whether the send button should act, independent of whether there is text.
     *
     * Separate from [enabled] because the two mean different things: [enabled] is about the whole
     * composer, this is about whether the thing behind it can accept work right now — the model still
     * loading, or already decoding a reply.
     */
    canSend: Boolean = true,
    showAttach: Boolean = true,
    onAttach: () -> Unit = {},
) {
    val shape = TraceShape.input

    TraceGlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = shape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.xs)
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp),
                enabled = enabled,
                placeholder = {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                },
                maxLines = 4,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions.Default,
                keyboardActions = KeyboardActions.Default,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.spacing.md,
                        end = MaterialTheme.spacing.md,
                        bottom = MaterialTheme.spacing.sm,
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showAttach) {
                    TraceIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Attach",
                        onClick = onAttach,
                        bordered = true,
                        iconSize = 22.dp,
                    )
                }

                Spacer(Modifier.weight(1f))

                TraceIconButton(
                    icon = Icons.Rounded.GraphicEq,
                    contentDescription = "Audio",
                    onClick = { /* TODO Audio */ },
                    container = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    tint = MaterialTheme.colorScheme.surface,
                )

                Spacer(Modifier.width(MaterialTheme.spacing.md))

                val sendable = value.isNotBlank() && canSend
                TraceIconButton(
                    icon = Icons.Rounded.ArrowUpward,
                    contentDescription = "Send",
                    onClick = { onSendText(value) },
                    container = if (sendable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    },
                    tint = if (sendable) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    },
                    enabled = sendable,
                )
            }
        }
    }
}
