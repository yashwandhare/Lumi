package com.lumi.reminders

import com.lumi.core.audit.AuditLog
import com.lumi.core.model.AuditOutcome
import com.lumi.core.model.CapabilityId
import com.lumi.core.model.ReminderKind
import com.lumi.core.model.ReminderStatus
import com.lumi.data.local.ReminderEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the guarantees a background worker depends on when nobody is watching.
 *
 * A reminder can be delivered while the app is closed, so its correctness cannot rest on the
 * UI being alive. What these tests hold in place: a row fires **exactly once** even when the
 * sweep retries; a user who dismissed first wins over the worker and nothing shows; every
 * failure — permission denied, posting crashed — is claimed as FIRED anyway and audited with
 * what to do about it, never silently dropped; and a repeat advances from its fired state
 * instead of stranding at the missed occurrence.
 */
class FireProcessorTest {

    @Test
    fun `a due reminder fires once and audits success`() = runBlocking {
        val dao = FakeReminderDao()
        val id = dao.insert(pendingRow(dueAtMs = NOW - MINUTE_MS))
        val notifications = mutableListOf<ReminderEntity>()
        val processor = processorOf(dao, notifications) { NotifyResult.SHOWN }

        val shown = processor.fireDue(NOW)

        assertEquals(1, shown)
        assertEquals(listOf(id), notifications.map { it.id })
        assertEquals(ReminderStatus.FIRED, dao.rows[id]!!.status)
        assertEquals(NOW, dao.rows[id]!!.firedAtMs)

        val events = dao.audit.events
        assertEquals(1, events.size)
        assertEquals(CapabilityId.TOOLS, events[0].capability)
        assertEquals("Reminder fired", events[0].summary)
        assertEquals(AuditOutcome.SUCCESS, events[0].outcome)
        assertEquals("call mom", events[0].subject)
    }

    @Test
    fun `a retried sweep never notifies twice`() = runBlocking {
        val dao = FakeReminderDao()
        dao.insert(pendingRow(dueAtMs = NOW - MINUTE_MS))
        val notifications = mutableListOf<ReminderEntity>()
        val processor = processorOf(dao, notifications) { NotifyResult.SHOWN }

        assertEquals(1, processor.fireDue(NOW))
        assertEquals(0, processor.fireDue(NOW))

        assertEquals("one post across both runs", 1, notifications.size)
        assertEquals("one audit entry across both runs", 1, dao.audit.events.size)
    }

    @Test
    fun `a dismissal that lands before the worker wins the race`() = runBlocking {
        val dao = FakeReminderDao()
        val id = dao.insert(pendingRow(dueAtMs = NOW - MINUTE_MS))
        dao.markDismissed(id, NOW)
        val notifications = mutableListOf<ReminderEntity>()
        val processor = processorOf(dao, notifications) { NotifyResult.SHOWN }

        val shown = processor.fireDue(NOW)

        assertEquals(0, shown)
        assertTrue(notifications.isEmpty())
        assertEquals("nothing happened, so the log says nothing", 0, dao.audit.events.size)
        assertEquals(ReminderStatus.DISMISSED, dao.rows[id]!!.status)
    }

    @Test
    fun `the row is claimed even when the notification cannot show`() = runBlocking {
        val dao = FakeReminderDao()
        dao.insert(pendingRow(dueAtMs = NOW - MINUTE_MS))
        val processor = processorOf(dao) { NotifyResult.NOT_SHOWN }

        assertEquals(0, processor.fireDue(NOW))

        val event = dao.audit.events.single()
        assertEquals(AuditOutcome.FAILURE, event.outcome)
        assertEquals("Reminder fired but the notification did not reach you", event.summary)
        assertEquals(
            "the recovery hint names the fix",
            "The notification permission is off. Enable it in settings.",
            event.detail,
        )
    }

    @Test
    fun `a posting crash is audited as a failure rather than lost`() = runBlocking {
        val dao = FakeReminderDao()
        val id = dao.insert(pendingRow(dueAtMs = NOW - MINUTE_MS))
        val processor = processorOf(dao) { error("notification service exploded") }

        assertEquals(0, processor.fireDue(NOW))

        val event = dao.audit.events.single()
        assertEquals(AuditOutcome.FAILURE, event.outcome)
        assertEquals("The system refused the notification.", event.detail)
        assertEquals("claimed so a retry will not double-fire", ReminderStatus.FIRED, dao.rows[id]!!.status)
    }

    @Test
    fun `a repeating reminder advances past the missed occurrences and re-arms`() = runBlocking {
        val dao = FakeReminderDao()
        // Due an hour ago, repeating hourly: the next ring belongs an hour ahead of *now*,
        // not an hour after the stale due time.
        val id = dao.insert(pendingRow(dueAtMs = NOW - HOUR_MS, repeatIntervalMs = HOUR_MS))
        val scheduler = RecordingScheduler()
        val processor = FireProcessor(dao, AuditLog(dao.audit), { NotifyResult.SHOWN }, scheduler)

        processor.fireDue(NOW)

        val row = dao.rows[id]!!
        assertEquals("back in the queue for its next occurrence", ReminderStatus.PENDING, row.status)
        assertEquals(NOW + HOUR_MS, row.dueAtMs)
        assertEquals(HOUR_MS, row.repeatIntervalMs)
        assertEquals(listOf(id to (NOW + HOUR_MS)), scheduler.armed)
    }

    @Test
    fun `every overdue reminder in the batch fires in one pass`() = runBlocking {
        val dao = FakeReminderDao()
        dao.insert(pendingRow(dueAtMs = NOW - 2 * HOUR_MS, text = "first"))
        dao.insert(pendingRow(dueAtMs = NOW - HOUR_MS, text = "second"))
        dao.insert(pendingRow(dueAtMs = NOW + HOUR_MS, text = "future"))
        val notifications = mutableListOf<ReminderEntity>()
        val processor = processorOf(dao, notifications) { NotifyResult.SHOWN }

        assertEquals(2, processor.fireDue(NOW))
        assertEquals(listOf("first", "second"), notifications.map { it.text })
    }

    /** A processor whose notifier records what it was handed and replies with [result]. */
    private fun processorOf(
        dao: FakeReminderDao,
        notifications: MutableList<ReminderEntity> = mutableListOf(),
        result: suspend (ReminderEntity) -> NotifyResult,
    ): FireProcessor {
        val scheduler = RecordingScheduler()
        return FireProcessor(dao, AuditLog(dao.audit), { reminder ->
            notifications += reminder
            result(reminder)
        }, scheduler)
    }

    private fun pendingRow(
        dueAtMs: Long,
        text: String = "call mom",
        repeatIntervalMs: Long? = null,
    ) = ReminderEntity(
        kind = ReminderKind.REMINDER,
        text = text,
        status = ReminderStatus.PENDING,
        dueAtMs = dueAtMs,
        repeatIntervalMs = repeatIntervalMs,
        createdAtMs = NOW - DAY_MS,
        updatedAtMs = NOW - DAY_MS,
    )

    private companion object {
        const val NOW = 1_789_000_000_000L
        const val HOUR_MS = 3_600_000L
        const val MINUTE_MS = 60_000L
        const val DAY_MS = 86_400_000L
    }
}
