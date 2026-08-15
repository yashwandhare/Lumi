package com.lumi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Lumi's only database. Everything the user owns lives here and nowhere else.
 *
 * **Migrations.** `exportSchema` is on and `app/schemas/` is committed. Every schema change
 * needs a real [androidx.room.migration.Migration] — never `fallbackToDestructiveMigration`.
 * A destructive migration on this database deletes the user's notes, journal, and emergency
 * contacts, which is not a recoverable mistake. During development, uninstall the app
 * instead of loosening this rule.
 *
 * There is no `User` table. Design Spec §17 lists one, but Lumi is single-user with no
 * accounts and no sign-in, so a table with exactly one row would only add joins. Recorded
 * in decisions.md.
 */
@Database(
    entities = [
        ChatEntity::class,
        ChatMessageEntity::class,
        DocumentEntity::class,
        DocumentChunkEntity::class,
        ChunkEmbeddingEntity::class,
        MemoryEntity::class,
        RoutineEntity::class,
        RoutineTriggerEntity::class,
        RoutineActionEntity::class,
        JournalEntryEntity::class,
        AuditEventEntity::class,
        EmergencyContactEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LumiDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun documentDao(): DocumentDao
    abstract fun routineDao(): RoutineDao
    abstract fun memoryDao(): MemoryDao
    abstract fun journalDao(): JournalDao
    abstract fun auditDao(): AuditDao
    abstract fun emergencyContactDao(): EmergencyContactDao

    companion object {
        const val NAME = "lumi.db"
    }
}
