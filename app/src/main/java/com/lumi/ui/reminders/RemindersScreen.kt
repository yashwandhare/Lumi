package com.lumi.ui.reminders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumi.core.model.ReminderKind
import com.lumi.core.model.ReminderStatus
import com.lumi.data.local.ReminderEntity
import com.lumi.reminders.ReminderFormat
import com.lumi.ui.components.EmptyState
import com.lumi.ui.components.LumiCardRow
import com.lumi.ui.theme.spacing

/**
 * The reminders and todos the user has captured, with completion and dismissal.
 *
 * The notification permission is asked here rather than at capture time: a reminder saved
 * while the permission is off still shows on this list, so the moment the user cares about
 * reminders is exactly when they can act on the grant. A denial changes nothing here — the
 * list works, only the ringing waits.
 */
@Composable
fun RemindersScreen(viewModel: RemindersViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    RequestNotificationPermission()

    val current = items ?: return // Room's first frame; not an empty state, just not read yet.

    if (current.isEmpty()) {
        EmptyState(
            headline = "Reminders",
            invitation = "Reminders, todos, and calendar items will show up here once you ask Lumi to keep track of something.",
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            contentPadding = PaddingValues(vertical = MaterialTheme.spacing.md),
        ) {
            items(current, key = { it.id }) { reminder ->
                ReminderRow(
                    reminder = reminder,
                    onComplete = { viewModel.complete(reminder.id) },
                    onDismiss = { viewModel.dismiss(reminder.id) },
                )
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: ReminderEntity,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    LumiCardRow(
        title = reminder.text,
        detail = detailOf(reminder),
        icon = when (reminder.kind) {
            ReminderKind.TODO -> Icons.Rounded.Checklist
            ReminderKind.REMINDER -> Icons.Rounded.Alarm
        },
        trailing = {
            TrailingActions(onComplete, onDismiss)
        },
    )
}

/** Both actions always visible — a swipe or long-press is not discoverable in a narrow list. */
@Composable
private fun TrailingActions(onComplete: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.foundation.layout.Row {
        IconButton(onClick = onComplete) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Mark done",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * When the item rings, in words. Status is carried by text, never colour alone. A todo with no
 * time says nothing — it is complete as written — while a reminder that already fired says so,
 * because a row reading "today at noon" after noon would be lying.
 */
private fun detailOf(reminder: ReminderEntity): String? = when {
    reminder.status == ReminderStatus.FIRED && reminder.firedAtMs != null ->
        "Rang ${ReminderFormat.dueLabel(reminder.firedAtMs, System.currentTimeMillis())}"
    reminder.dueAtMs == Long.MAX_VALUE -> null
    else -> ReminderFormat.dueLabel(reminder.dueAtMs, System.currentTimeMillis())
}

/** One ask for the notification permission per visit to the screen, on API 33+. */
@Composable
private fun RequestNotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
