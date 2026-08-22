package com.lumi.reminders

import com.lumi.core.Capability
import com.lumi.core.CapabilityInput
import com.lumi.core.CapabilityResult
import com.lumi.core.audit.AuditLog
import com.lumi.core.model.AuditOutcome
import com.lumi.core.model.CapabilityId
import com.lumi.core.model.ReminderKind
import com.lumi.core.model.ReminderStatus
import com.lumi.data.local.ReminderDao
import com.lumi.data.local.ReminderEntity
import com.lumi.router.RulesTier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a routed "remind me…" or "add … to my list" into a stored row and an armed alarm.
 *
 * Rides on [CapabilityId.TOOLS] — the closed capability set forbids a REMINDER value without a
 * decision entry — with [RulesTier.SLOT_KIND] telling it which of the two it is. The two behave
 * identically except for the label: both land in the one `reminders` table, and only a due time
 * makes one ring.
 *
 * The sentence is interpreted **here, at creation time, only**. The stored row carries the
 * resolved instant and the cleaned title; everything downstream — the worker, the alarm, the
 * notification — reads the row and never re-reads the sentence. A time the parser cannot honour
 * does not fail the request: the row is kept without a due time and still shows on the list,
 * which is the honest outcome for "remind me someday".
 *
 * JVM-testable by construction: DAO, scheduler, audit, and clock are all injected interfaces.
 */
@Singleton
class ReminderCapability @Inject constructor(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
    private val audit: AuditLog,
    private val clock: Clock,
) : Capability {

    override val id: CapabilityId = CapabilityId.TOOLS

    override suspend fun execute(input: CapabilityInput): CapabilityResult {
        val kind = when (input.intent[RulesTier.SLOT_KIND]) {
            RulesTier.KIND_TODO -> ReminderKind.TODO
            // A missing kind still means reminder: a reminder misread as a todo loses its
            // time, a todo misread as a reminder just never fires. The safe default is the
            // one that can still ring.
            else -> ReminderKind.REMINDER
        }

        val utterance = input.intent.rawText.trim()
        val nowMs = clock.now()
        val dueMs = ReminderTimeParser.parse(utterance, nowMs)
        val title = ReminderTitle.clean(utterance, kind)

        val row = ReminderEntity(
            kind = kind,
            text = title,
            status = ReminderStatus.PENDING,
            dueAtMs = dueMs ?: NEVER_DUE,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
        val id = dao.insert(row)

        if (dueMs != null) {
            scheduler.arm(id, dueMs)
        }
        // The sweep is the safety net for every arm that could not be exact: reboot edges,
        // a revoked exact-alarm grant, a Doze window that swallowed the alarm.
        scheduler.ensureSweep()

        val dueLabel = dueMs?.let { ReminderFormat.dueLabel(it, nowMs) }
        audit.record(
            capability = CapabilityId.TOOLS,
            summary = if (dueMs != null) {
                if (kind == ReminderKind.TODO) "Added a to-do with a time" else "Set a reminder"
            } else {
                if (kind == ReminderKind.TODO) "Added a to-do" else "Kept a reminder without a time"
            },
            outcome = AuditOutcome.SUCCESS,
            subject = title,
            detail = dueMs?.let { "Due $it." } ?:
                "No time was found in the request, so it will not ring — it stays on the list.",
        )

        return CapabilityResult.Ok(
            userMessage = when {
                dueMs != null && kind == ReminderKind.REMINDER ->
                    "Reminder set for $dueLabel."
                dueMs != null ->
                    "Added to your list, due $dueLabel."
                kind == ReminderKind.TODO ->
                    "Added to your list."
                else ->
                    "I could not find a time in that, so I kept it on your list without one."
            },
        )
    }

    private companion object {
        /** The never-due sentinel from the schema: sorts after every real date, never fires. */
        const val NEVER_DUE = Long.MAX_VALUE
    }
}
