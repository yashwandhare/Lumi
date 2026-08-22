package com.lumi.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lumi.data.local.ReminderDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms real deliveries for stored reminders.
 *
 * **Exact alarms carry the firing; WorkManager is only the safety net.** An exact alarm rings at
 * the minute the user named even in Doze, which a periodic worker cannot promise on mid-range
 * hardware. The periodic sweep ([ensureSweep]) exists for everything that can swallow an arm —
 * a revoked exact-alarm grant cancels its existing alarms, OEM battery managers drop them, and
 * a reminder created while exact alarms were denied never had one. It runs at WorkManager's
 * minimum interval and fires anything overdue.
 *
 * Exactness degrades, delivery does not. When `canScheduleExactAlarms()` is false — the default
 * for newly installed apps on Android 14+ — the arm falls back to an inexact alarm and the sweep
 * bounds the worst case. The UI may prompt for the grant; this class never blocks on it.
 *
 * [rearmPending] re-arms every pending row from scratch and is called on boot, when the alarm
 * registration has died with the old process. Overdue rows are armed at "now" so the alarm
 * fires immediately through the ordinary path — firing stays in [FireProcessor], keeping this
 * class free of any dependency on it.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: ReminderDao,
) : ReminderScheduler {

    override fun arm(reminderId: Long, dueAtMs: Long) {
        val operation = fireIntent(reminderId)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAtMs, operation)
        } else {
            // Still wakes the device out of idle, just without the exact-time promise.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAtMs, operation)
        }
    }

    override fun ensureSweep() {
        val request = PeriodicWorkRequestBuilder<ReminderSweepWorker>(Duration.ofMinutes(SWEEP_MINUTES))
            .build()
        // KEEP: the sweep is idempotent, so an existing schedule is as good as this one and
        // replacing it would reset its cadence every time a reminder is created.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SWEEP_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override suspend fun rearmPending() {
        val now = System.currentTimeMillis()
        dao.pendingInWindow(fromMs = 0L, toMs = Long.MAX_VALUE)
            .filter { it.dueAtMs != NEVER_DUE }
            .forEach { arm(it.id, maxOf(it.dueAtMs, now)) }
    }

    /**
     * One fire intent per reminder id: re-arming replaces its own previous registration rather
     * than stacking, and the receiver reads the whole due set anyway, so a stale extra costs
     * nothing.
     */
    private fun fireIntent(reminderId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        reminderId.toInt(),
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminderId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_FIRE_REMINDER = "com.lumi.reminders.FIRE"
        const val EXTRA_REMINDER_ID = "reminderId"

        const val SWEEP_WORK_NAME = "lumi-reminder-sweep"

        /** WorkManager's minimum periodic interval; the sweep is a net, not the clock. */
        const val SWEEP_MINUTES = 15L

        private const val NEVER_DUE = Long.MAX_VALUE
    }
}
