package com.lumi.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lumi.MainActivity
import com.lumi.R
import com.lumi.data.local.ReminderEntity

/**
 * Shows a fired reminder to the user.
 *
 * Lives outside Hilt on purpose: it is also reached from inside a [ReminderFireWorker], where
 * constructor injection does not exist, so both call sites build one directly.
 *
 * The notification is high-importance (heads-up) — a reminder the user does not see is a
 * reminder that never happened. Opening it returns to the app; the reminder list will surface
 * the FIRED state once that UI lands.
 */
class ReminderNotifier(
    private val context: Context,
    private val notifications: NotificationManager,
) {

    /** The one function the fire path needs. Never throws. */
    fun post(reminder: ReminderEntity): NotifyResult = runCatching {
        ensureChannel()
        if (!postAllowed()) return@runCatching NotifyResult.NOT_SHOWN

        val openApp = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.text)
            .setContentText(NOTIFICATION_HINT)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .build()

        // Tag with the reminder id so a repeat of the same reminder replaces its own card
        // instead of stacking forever.
        notifications.notify(reminder.id.toInt(), notification)
        NotifyResult.SHOWN
    }.getOrElse { NotifyResult.FAILED }

    private fun postAllowed(): Boolean {
        // POST_NOTIFICATIONS is API 33+; below that the OS shows whatever is granted.
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun ensureChannel() {
        if (notifications.getNotificationChannel(CHANNEL_ID) != null) return
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = CHANNEL_DESCRIPTION },
        )
    }

    companion object {
        const val CHANNEL_ID = "lumi_reminders"
        private const val CHANNEL_NAME = "Reminders"
        private const val CHANNEL_DESCRIPTION = "Reminders and todos that are due"
        private const val NOTIFICATION_HINT = "Time to do this one."
    }
}
