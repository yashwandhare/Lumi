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
        ReminderEntity::class,
    ],
    version = 3,
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
    abstract fun reminderDao(): ReminderDao

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

        /**
         * Adds the `reminders` table for Phase 3. Purely additive: no existing row is touched.
         *
         * Enums are stored as names per the `Converters` convention, so the migration writes
         * TEXT columns with no default — every insert names its own kind and status.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `dueAtMs` INTEGER NOT NULL,
                        `repeatIntervalMs` INTEGER,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `doneAtMs` INTEGER,
                        `firedAtMs` INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_dueAtMs` ON `reminders` (`dueAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_status` ON `reminders` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_kind` ON `reminders` (`kind`)")
            }
        }
    }
}
