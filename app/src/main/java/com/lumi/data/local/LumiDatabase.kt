package com.lumi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Lumi's only database. Everything the user owns lives here and nowhere else.
 *
 * **Migrations.** `exportSchema` is on and `app/schemas/` is committed. Every schema change needs a real
 * [Migration] — never `fallbackToDestructiveMigration`. A destructive migration on this database deletes
 * the user's notes, journal, and conversations, which is not a recoverable mistake.
 *
 * There is no `User` table. Design Spec §17 lists one, but Lumi is single-user with no accounts and no
 * sign-in, so a table with exactly one row would only add joins. Recorded in decisions.md.
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
    ],
    version = 2,
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

    companion object {
        const val NAME = "lumi.db"

        /**
         * Drops `emergency_contacts`, left behind when SOS was cut per strategy brief §7.
         *
         * Written as a real migration rather than a version bump with a destructive fallback, because
         * the rule above has no exception for "the table was empty anyway" — any device that ran v1 of
         * this schema has the table, and the whole point of forbidding destructive migrations is that
         * it is never obvious from the diff which other tables would go with it.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS emergency_contacts")
            }
        }
    }
}
