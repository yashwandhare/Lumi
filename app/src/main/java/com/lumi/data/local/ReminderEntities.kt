package com.lumi.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lumi.core.model.ReminderKind
import com.lumi.core.model.ReminderStatus

/**
 * A reminder or a todo, first-class rather than a routine.
 *
 * The Phase 3 rule: "remind me at 6" must not have to become a trigger/action graph to work.
 * A reminder has a time, the worker notifies at that time, and the row moves on. Routines
 * keep their own graph for the cases that genuinely need triggers beyond a clock.
 *
 * [dueAtMs] is the only trigger. Natural language is parsed to that instant at creation time
 * only; the fired notification reads this row and never re-interprets [text].
 *
 * [status] is the idempotency point: a retried worker that sees anything past [ReminderStatus.PENDING]
 * must not notify again. [firedAtMs] records when the notification was actually shown, so the
 * audit trail can say whether a reminder fired or was cleared before its time.
 *
 * [repeatIntervalMs] is non-null for repeating reminders only. Repeats are resolved by the
 * scheduler (it re-arms with the next due time after firing), so this number is data here —
 * the row stays honest about what it means if scheduling changes later.
 */
@Entity(
    tableName = "reminders",
    indices = [
        androidx.room.Index("dueAtMs"),
        androidx.room.Index("status"),
        androidx.room.Index("kind"),
    ],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: ReminderKind,
    val text: String,
    val status: ReminderStatus,
    /**
     * The instant the reminder is due, epoch millis. `Long.MAX_VALUE` for a todo that has no
     * due time at all — it sorts after every real date, and the worker's query naturally
     * passes it by.
     */
    val dueAtMs: Long,
    val repeatIntervalMs: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val doneAtMs: Long? = null,
    val firedAtMs: Long? = null,
)
