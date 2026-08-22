package com.lumi.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lumi.ui.widget.refreshRemindersWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The delivery end of an armed reminder, and the boot re-arm.
 *
 * Two actions arrive here. [AlarmScheduler.ACTION_FIRE_REMINDER] is the exact (or inexact)
 * alarm: fire everything due — the receiver processes the whole due set rather than the one id
 * in the extra, so a reminder whose alarm was dropped still rings on the next one that lands,
 * and a duplicate arm costs nothing because the DAO claim is the gate. `BOOT_COMPLETED` wipes
 * every alarm registration in the old process, so it re-arms all pending rows instead.
 *
 * `goAsync()` because the work is a Room round-trip and notification posts: longer than a
 * receiver may safely run on the main thread, shorter than a service justifies.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var fireProcessor: FireProcessor

    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        // A fresh scope per broadcast: receivers have no lifecycle to hang a job on, and
        // SupervisorJob keeps one failed reminder from cancelling the rest of the batch.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED -> scheduler.rearmPending()
                    else -> {
                        fireProcessor.fireDue(System.currentTimeMillis())
                        // A fire changes what "next up" means on the home screen.
                        refreshRemindersWidget(context)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Reminder delivery failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "LumiReminders"
    }
}
