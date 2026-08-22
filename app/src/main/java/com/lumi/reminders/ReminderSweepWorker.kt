package com.lumi.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lumi.data.local.ReminderDao
import com.lumi.ui.widget.refreshRemindersWidget
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The safety net that runs even when every exact alarm failed.
 *
 * Fires anything overdue and re-arms the future, on WorkManager's minimum cadence — it is not
 * how a reminder reaches the user on time, it is what catches the reminder that nothing else
 * delivered: an alarm cancelled by a revoked grant, dropped by an aggressive battery manager,
 * or never armed because exact alarms were already denied at creation.
 *
 * Outside Hilt on purpose: constructor injection for workers needs the `hilt-work` artifact,
 * which is not an approved dependency. The entry point hands over the same singletons the
 * receiver uses, so both delivery paths share one [FireProcessor].
 */
class ReminderSweepWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val processor = EntryPointAccessors
            .fromApplication(applicationContext, RemindersEntryPoint::class.java)
            .fireProcessor()

        return try {
            processor.fireDue(System.currentTimeMillis())
            refreshRemindersWidget(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            // A database-level failure is transient more often than not; retry within
            // WorkManager's backoff before giving up on this run entirely.
            if (runAttemptCount < MAX_RUNS - 1) Result.retry() else Result.failure()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RemindersEntryPoint {
        fun fireProcessor(): FireProcessor

        /** Also read by the home-screen widget, which renders straight from Room. */
        fun reminderDao(): ReminderDao
    }

    private companion object {
        const val MAX_RUNS = 3
    }
}
