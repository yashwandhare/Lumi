package com.trace.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trace.core.model.AuditOutcome
import com.trace.core.model.CapabilityId

/**
 * One thing Trace did, and how it went.
 *
 * This table is the product's transparency surface: the answer to "what did this app
 * touch, and when". Every capability writes here, including on failure. An action that
 * happened but was not audited is worse than an action that did not happen, because it
 * makes the log a liar.
 *
 * [summary] and [detail] are written in the user's language. Never put an Android
 * exception message or a class name in either — the audit log is read by the person who
 * owns the phone, not by a developer.
 */
@Entity(
    tableName = "audit_events",
    indices = [Index("occurredAtMs"), Index("capability")],
)
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAtMs: Long,
    val capability: CapabilityId,
    val summary: String,
    /** What was acted on: a document name, a routine name, a contact. */
    val subject: String? = null,
    val outcome: AuditOutcome,
    /** Why it failed or only partly completed. Plain language, no stack traces. */
    val detail: String? = null,
)
