package com.trace.core.audit

import com.trace.core.model.AuditOutcome
import com.trace.core.model.CapabilityId
import com.trace.data.local.AuditDao
import com.trace.data.local.AuditEventEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the record of what Trace did.
 *
 * Exists as a thin class over [AuditDao] rather than callers using the DAO directly for two
 * reasons: it stamps the time in one place, and it bounds the table. Every routine fire
 * writes a row, so without trimming the log grows forever on the user's phone.
 *
 * Every capability writes here, including on failure. An action that happened without an
 * entry is worse than an action that did not happen, because it makes the log a liar — and
 * the log is the product's proof that nothing left the device.
 */
@Singleton
class AuditLog @Inject constructor(
    private val auditDao: AuditDao,
) {

    fun observeRecent(limit: Int = DEFAULT_PAGE): Flow<List<AuditEventEntity>> =
        auditDao.observeRecent(limit)

    fun observeFor(capability: CapabilityId, limit: Int = DEFAULT_PAGE): Flow<List<AuditEventEntity>> =
        auditDao.observeFor(capability, limit)

    /**
     * Record an action.
     *
     * [summary] and [detail] are read by the phone's owner, not by a developer. Write plain
     * language: "Silenced the phone" rather than "SILENT_MODE=on", and "Location was
     * unavailable" rather than a permission constant.
     */
    suspend fun record(
        capability: CapabilityId,
        summary: String,
        outcome: AuditOutcome,
        subject: String? = null,
        detail: String? = null,
        occurredAtMs: Long = System.currentTimeMillis(),
    ) {
        auditDao.insert(
            AuditEventEntity(
                occurredAtMs = occurredAtMs,
                capability = capability,
                summary = summary,
                subject = subject,
                outcome = outcome,
                detail = detail,
            ),
        )
        auditDao.trimTo(MAX_RETAINED)
    }

    private companion object {
        const val DEFAULT_PAGE = 200

        /**
         * Roughly a month of heavy use. Old enough to answer "what did this app do
         * yesterday", small enough that the table never becomes the largest thing on disk.
         */
        const val MAX_RETAINED = 5_000
    }
}
