package com.lumi.reminders

import com.lumi.core.audit.AuditLog
import com.lumi.core.model.AuditOutcome
import com.lumi.core.model.CapabilityId
import com.lumi.data.local.ReminderDao
import com.lumi.data.local.ReminderEntity

/**
 * Processes every reminder whose time has come: one row = claim it, notify, audit.
 *
 * The idempotency guard is the DAO's conditional UPDATE, not a check here: [ReminderDao.markFired]
 * only matches PENDING rows and returns the number of rows touched, so a retried worker that
 * races a dismissed reminder claims zero rows and stops — before any notification, before any
 * audit entry. That ordering matters because an entry saying "fired" for a reminder that never
 * showed would make the log a liar.
 *
 * Every outcome is audited, including the failures: the notification permission denied, the
 * channel dead, a re-arm that misses the repeat. "Including failures" is the Phase 3 brief,
 * and a reminder silently never shown is exactly what the audit must not hide.
 *
 * Pure JVM-testable by construction: everything Android-specific — the wall clock, the
 * notification post — is injected, and the tests hand in both.
 */
class FireProcessor(
    private val dao: ReminderDao,
    private val audit: AuditLog,
    private val notifier: suspend (ReminderEntity) -> NotifyResult,
    private val scheduler: ReminderScheduler,
) {

    /**
     * Claims and fires every due reminder. Safe to run from both the worker and the alarm
     * path — the row claim is the gate, and two runners racing for the same row each try one
     * and at most one succeeds.
     *
     * @return the number of notifications actually shown.
     */
    suspend fun fireDue(nowMs: Long): Int {
        val due = dao.dueAtOrBefore(nowMs)
        var shown = 0
        for (reminder in due) {
            if (fireOne(reminder, nowMs) == NotifyResult.SHOWN) shown++
        }
        return shown
    }

    private suspend fun fireOne(reminder: ReminderEntity, nowMs: Long): NotifyResult {
        val claimed = dao.markFired(reminder.id, nowMs)
        if (claimed == 0) {
            // A user action landed first, or another runner won the race. Not a failure —
            // the reminder did the right thing, and the log already says so.
            return NotifyResult.SKIPPED_ALREADY_HANDLED
        }

        val notified = try {
            notifier(reminder)
        } catch (t: Throwable) {
            NotifyResult.FAILED
        }

        val outcome = when (notified) {
            NotifyResult.SHOWN -> AuditOutcome.SUCCESS
            else -> AuditOutcome.FAILURE
        }
        audit.record(
            capability = CapabilityId.TOOLS,
            summary = if (notified == NotifyResult.SHOWN) {
                "Reminder fired"
            } else {
                "Reminder fired but the notification did not reach you"
            },
            outcome = outcome,
            subject = reminder.text,
            detail = when (notified) {
                NotifyResult.SHOWN -> null
                NotifyResult.NOT_SHOWN -> "The notification permission is off. Enable it in settings."
                NotifyResult.FAILED -> "The system refused the notification."
                NotifyResult.SKIPPED_ALREADY_HANDLED -> null
            },
            occurredAtMs = nowMs,
        )

        // A repeat advances even when the notification failed: the row already says FIRED,
        // and leaving it there would strand the repeat at its current occurrence.
        reminder.repeatIntervalMs?.let { rearmRepeat(reminder, it, nowMs) }

        return notified
    }

    /**
     * Moves a repeating reminder to its next due time. The DAO guard requires the row to be
     * FIRED, and it is — we just fired it — unless a dismissal landed between the fire and
     * here, in which case the update touches nothing and the reminder stays dismissed.
     */
    private suspend fun rearmRepeat(reminder: ReminderEntity, interval: Long, nowMs: Long) {
        var next = reminder.dueAtMs + interval
        while (next <= nowMs) next += interval
        val moved = dao.reschedule(reminder.id, next, interval, nowMs)
        if (moved == 0) return
        scheduler.arm(reminder.id, next)
    }
}

/** The full set of things that can happen to one fired reminder. */
enum class NotifyResult {
    SHOWN,

    /** The OS would not show it — the notification permission is off. */
    NOT_SHOWN,

    /** Posting failed for any other reason. */
    FAILED,

    /** The user or another runner handled it first. Nothing to do, nothing to audit. */
    SKIPPED_ALREADY_HANDLED,
}
