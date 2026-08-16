package com.lumi.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lumi.ui.theme.LumiShape

/**
 * The one dialog style the app uses.
 *
 * A hand-rolled dialog surface would drift from Material's focus handling, dismissal on outside
 * tap, and system-back behaviour, so this wraps [AlertDialog] and only restyles it to Lumi:
 * the app's surface, §4's default radius, serif title, sans-serif button labels.
 *
 * Phases 3-5 all confirm consequence before acting — saving a routine, deleting a conversation,
 * letting a network request leave the device — and they must all ask in the same voice.
 *
 * **Destructive confirmations keep the confirm label plain.** "Delete" not a red button: §3 gets
 * hierarchy from size and weight, never colour, and the words are what carry the consequence.
 *
 * @param text the body copy. Required — a dialog that asks without explaining is a trap, and the
 *   parameter being a slot rather than a string lets a screen show structure — an interpreted
 *   WHEN/DO, a file list — inside the question.
 * @param dismissLabel present unless null; pass null only when dismissing is not an option.
 */
@Composable
fun LumiDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: @Composable (() -> Unit)? = null,
    dismissLabel: String? = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = LumiShape.default,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        icon = if (icon != null) {
            { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
        } else {
            null
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = text,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = if (dismissLabel != null) {
            {
                TextButton(onClick = onDismissRequest) {
                    Text(dismissLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            null
        },
    )
}
