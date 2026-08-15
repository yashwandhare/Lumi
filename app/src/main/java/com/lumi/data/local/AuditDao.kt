package com.lumi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lumi.core.model.CapabilityId
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {

    @Query("SELECT * FROM audit_events ORDER BY occurredAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditEventEntity>>

    @Query(
        """
        SELECT * FROM audit_events WHERE capability = :capability
        ORDER BY occurredAtMs DESC LIMIT :limit
        """,
    )
    fun observeFor(capability: CapabilityId, limit: Int): Flow<List<AuditEventEntity>>

    @Insert
    suspend fun insert(event: AuditEventEntity): Long

    /**
     * Drop everything past the newest [keep] rows.
     *
     * The log is append-only and every routine fire writes to it, so on a phone it would
     * grow without bound. Trimming is a maintenance task, not something a caller decides
     * per write.
     */
    @Query(
        """
        DELETE FROM audit_events WHERE id NOT IN (
            SELECT id FROM audit_events ORDER BY occurredAtMs DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int)
}
