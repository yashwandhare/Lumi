package com.lumi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Reminders and todos in one table, split by kind only where a screen cares.
 *
 * Every status change returns the number of rows touched and is conditional on the row
 * still being in the state the change assumes. A worker that races with a user who just
 * dismissed the reminder updates zero rows and therefore does not notify again — the
 * idempotency guard lives in the query, where a retried worker cannot talk itself out of it.
 */
@Dao
interface ReminderDao {

    /** The list screen: everything not yet cleared, soonest due first, never-due todos last. */
    @Query(
        """
        SELECT * FROM reminders
        WHERE status IN ('PENDING', 'FIRED')
        ORDER BY dueAtMs ASC
        """,
    )
    fun observeActive(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    suspend fun find(reminderId: Long): ReminderEntity?

    /**
     * Everything whose time has come and is still waiting. The worker runs after the app is
     * killed or the device restarts; it may see several rows at once and must process each
     * exactly once.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE status = 'PENDING' AND dueAtMs <= :nowMs
        ORDER BY dueAtMs ASC
        """,
    )
    suspend fun dueAtOrBefore(nowMs: Long): List<ReminderEntity>

    /**
     * Pending reminders in the window ahead, so the scheduler can arm exact alarms. The
     * window is the caller's decision — WorkManager periodicity is not a substitute for an
     * exact alarm on mid-range hardware.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE status = 'PENDING' AND dueAtMs > :fromMs AND dueAtMs <= :toMs
        ORDER BY dueAtMs ASC
        """,
    )
    suspend fun pendingInWindow(fromMs: Long, toMs: Long): List<ReminderEntity>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    /**
     * Mark the reminder fired. Succeeds only while the row is still [PENDING]; a user
     * dismissal that landed first makes this return 0, which is the worker's signal to skip
     * the notification.
     */
    @Query(
        """
        UPDATE reminders
        SET status = 'FIRED', firedAtMs = :nowMs, updatedAtMs = :nowMs
        WHERE id = :reminderId AND status = 'PENDING'
        """,
    )
    suspend fun markFired(reminderId: Long, nowMs: Long): Int

    /** Complete the item. Accepts any non-terminal state so dismissing is not a prerequisite. */
    @Query(
        """
        UPDATE reminders
        SET status = 'DONE', doneAtMs = :nowMs, updatedAtMs = :nowMs
        WHERE id = :reminderId AND status IN ('PENDING', 'FIRED')
        """,
    )
    suspend fun markDone(reminderId: Long, nowMs: Long): Int

    /** Clear without claiming completion. */
    @Query(
        """
        UPDATE reminders
        SET status = 'DISMISSED', doneAtMs = :nowMs, updatedAtMs = :nowMs
        WHERE id = :reminderId AND status IN ('PENDING', 'FIRED')
        """,
    )
    suspend fun markDismissed(reminderId: Long, nowMs: Long): Int

    /**
     * Re-arm a repeating reminder after it fired. Moves the due time forward by the repeat
     * interval and returns the row to [PENDING] in one statement; the conditional guard
     * keeps a concurrent dismissal from being resurrected.
     */
    @Query(
        """
        UPDATE reminders
        SET status = 'PENDING', dueAtMs = :nextDueAtMs, repeatIntervalMs = :repeatIntervalMs,
            updatedAtMs = :nowMs
        WHERE id = :reminderId AND status = 'FIRED'
        """,
    )
    suspend fun reschedule(reminderId: Long, nextDueAtMs: Long, repeatIntervalMs: Long, nowMs: Long): Int
}
