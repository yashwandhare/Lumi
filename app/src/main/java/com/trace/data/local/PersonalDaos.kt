package com.trace.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.trace.core.model.MemoryKind
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE kind = :kind ORDER BY updatedAtMs DESC")
    fun observeByKind(kind: MemoryKind): Flow<List<MemoryEntity>>

    /** Persistent memory injected into the model's context. Bounded on purpose. */
    @Query("SELECT * FROM memories ORDER BY updatedAtMs DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MemoryEntity>

    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :memoryId")
    suspend fun delete(memoryId: Long)
}

@Dao
interface JournalDao {

    @Query("SELECT * FROM journal_entries ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    /**
     * Entries in a window, for pattern surfacing.
     *
     * Pattern surfacing counts user-chosen mood tags over a period. It does not read the
     * body as a clinical signal — see decisions.md.
     */
    @Query("SELECT * FROM journal_entries WHERE createdAtMs >= :sinceMs ORDER BY createdAtMs DESC")
    suspend fun since(sinceMs: Long): List<JournalEntryEntity>

    @Query(
        """
        SELECT COUNT(*) FROM journal_entries
        WHERE moodTag = :moodTag AND createdAtMs >= :sinceMs
        """,
    )
    suspend fun countTagSince(moodTag: String, sinceMs: Long): Int

    @Insert
    suspend fun insert(entry: JournalEntryEntity): Long

    @Query("DELETE FROM journal_entries WHERE id = :entryId")
    suspend fun delete(entryId: Long)
}

@Dao
interface EmergencyContactDao {

    @Query("SELECT * FROM emergency_contacts ORDER BY ordinal ASC")
    fun observeAll(): Flow<List<EmergencyContactEntity>>

    /** SOS reads this synchronously at trigger time; it must stay a single cheap query. */
    @Query("SELECT * FROM emergency_contacts ORDER BY ordinal ASC")
    suspend fun all(): List<EmergencyContactEntity>

    @Insert
    suspend fun insert(contact: EmergencyContactEntity): Long

    @Update
    suspend fun update(contact: EmergencyContactEntity)

    @Delete
    suspend fun delete(contact: EmergencyContactEntity)
}
