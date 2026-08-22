package com.lumi.reminders

import com.lumi.data.local.AuditDao
import com.lumi.data.local.AuditEventEntity
import com.lumi.data.local.ReminderDao
import com.lumi.data.local.ReminderEntity
import com.lumi.core.model.CapabilityId
import com.lumi.core.model.ReminderKind
import com.lumi.core.model.ReminderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Shared JVM fakes for the reminder path.
 *
 * The DAO fake replicates the conditional-update semantics exactly — a status change touches
 * only rows in the state it assumes and returns how many moved. Those guards are the
 * duplicate-prevention mechanism under test, so the fake must not be looser than the SQL it
 * stands in for.
 */
internal class FakeReminderDao : ReminderDao {

    val rows = LinkedHashMap<Long, ReminderEntity>()
    val audit = RecordingAuditDao()
    private var nextId = 1L

    override fun observeActive(): Flow<List<ReminderEntity>> =
        flowOf(rows.values.filter { it.status == ReminderStatus.PENDING || it.status == ReminderStatus.FIRED })

    override suspend fun find(reminderId: Long): ReminderEntity? = rows[reminderId]

    override suspend fun dueAtOrBefore(nowMs: Long): List<ReminderEntity> =
        rows.values.filter { it.status == ReminderStatus.PENDING && it.dueAtMs <= nowMs }
            .sortedBy { it.dueAtMs }

    override suspend fun pendingInWindow(fromMs: Long, toMs: Long): List<ReminderEntity> =
        rows.values.filter {
            it.status == ReminderStatus.PENDING && it.dueAtMs > fromMs && it.dueAtMs <= toMs
        }.sortedBy { it.dueAtMs }

    override suspend fun insert(reminder: ReminderEntity): Long {
        val id = nextId++
        rows[id] = reminder.copy(id = id)
        return id
    }

    override suspend fun markFired(reminderId: Long, nowMs: Long): Int =
        transition(reminderId, setOf(ReminderStatus.PENDING)) {
            it.copy(status = ReminderStatus.FIRED, firedAtMs = nowMs, updatedAtMs = nowMs)
        }

    override suspend fun markDone(reminderId: Long, nowMs: Long): Int =
        transition(reminderId, setOf(ReminderStatus.PENDING, ReminderStatus.FIRED)) {
            it.copy(status = ReminderStatus.DONE, doneAtMs = nowMs, updatedAtMs = nowMs)
        }

    override suspend fun markDismissed(reminderId: Long, nowMs: Long): Int =
        transition(reminderId, setOf(ReminderStatus.PENDING, ReminderStatus.FIRED)) {
            it.copy(status = ReminderStatus.DISMISSED, doneAtMs = nowMs, updatedAtMs = nowMs)
        }

    override suspend fun reschedule(
        reminderId: Long,
        nextDueAtMs: Long,
        repeatIntervalMs: Long,
        nowMs: Long,
    ): Int = transition(reminderId, setOf(ReminderStatus.FIRED)) {
        it.copy(
            status = ReminderStatus.PENDING,
            dueAtMs = nextDueAtMs,
            repeatIntervalMs = repeatIntervalMs,
            updatedAtMs = nowMs,
        )
    }

    private inline fun transition(
        reminderId: Long,
        allowedFrom: Set<ReminderStatus>,
        transform: (ReminderEntity) -> ReminderEntity,
    ): Int {
        val current = rows[reminderId] ?: return 0
        if (current.status !in allowedFrom) return 0
        rows[reminderId] = transform(current)
        return 1
    }
}

/** Records what the log was told, so tests can assert the record reads truthfully. */
internal class RecordingAuditDao : AuditDao {

    val events = mutableListOf<AuditEventEntity>()

    override fun observeRecent(limit: Int): Flow<List<AuditEventEntity>> = flowOf(events.toList())

    override fun observeFor(capability: CapabilityId, limit: Int): Flow<List<AuditEventEntity>> =
        flowOf(events.filter { it.capability == capability })

    override suspend fun insert(event: AuditEventEntity): Long {
        events += event
        return events.size.toLong()
    }

    override suspend fun trimTo(keep: Int) = Unit
}

/** Stands in for the alarm path; records every arm so tests can assert the next ring time. */
internal class RecordingScheduler : ReminderScheduler {

    val armed = mutableListOf<Pair<Long, Long>>()
    var sweeps = 0

    override fun arm(reminderId: Long, dueAtMs: Long) {
        armed += reminderId to dueAtMs
    }

    override fun ensureSweep() {
        sweeps++
    }

    override suspend fun rearmPending() = Unit
}
