package com.lumi.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lumi.core.model.MemoryKind
import com.lumi.core.model.MemorySource

/**
 * Something the user asked Lumi to remember, or something Lumi recorded about them.
 *
 * [MemoryKind.SYSTEM_AUTHORED] rows must show their [source] in the UI. A user seeing a
 * fact about themselves has a right to know whether they wrote it or the app inferred it.
 */
@Entity(tableName = "memories", indices = [Index("kind"), Index("updatedAtMs")])
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: MemoryKind,
    val source: MemorySource,
    val title: String,
    val body: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

/**
 * A private journal entry. Local only, never uploaded, never sent to a model for
 * emotional assessment.
 *
 * [moodTag] is a short label the **user** picks, not something inferred. Pattern surfacing
 * counts these tags over time to suggest a routine. It does not interpret the body text as
 * a mental-health signal — see decisions.md for why that boundary is hard.
 */
@Entity(tableName = "journal_entries", indices = [Index("createdAtMs"), Index("moodTag")])
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val body: String,
    val moodTag: String? = null,
    val createdAtMs: Long,
)

/**
 * Someone SOS sends a location message to.
 *
 * Populated only through SOS setup, where the SMS and location permissions are requested.
 * Never harvested from the device contact list without the user choosing each entry.
 */
@Entity(tableName = "emergency_contacts", indices = [Index("ordinal")])
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val ordinal: Int,
)
