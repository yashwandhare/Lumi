package com.lumi.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the 1→2 migration on a real v1 database rather than trusting that a `DROP TABLE` is obviously
 * safe.
 *
 * The rule this database is built on is that no schema change may be destructive, and the only way that
 * rule means anything is if the migration is executed against real v1 bytes and the surviving tables are
 * checked afterwards. A migration that silently took `chats` or `journal_entries` with it would look
 * identical in the diff.
 *
 * `MigrationTestHelper` reads the committed `app/schemas/` JSON, which is exactly why those files are
 * version-controlled.
 */
@RunWith(AndroidJUnit4::class)
class LumiDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LumiDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratingFromOneToTwoDropsEmergencyContactsAndKeepsEverythingElse() {
        // A v1 database with a row in the table about to be dropped and a row in one that must survive.
        helper.createDatabase(TEST_DB, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO emergency_contacts (name, phoneNumber, ordinal) VALUES ('Someone', '+10000000000', 0)"
            )
            v1.execSQL(
                "INSERT INTO chats (title, createdAtMs, updatedAtMs) VALUES ('kept', 1000, 1000)"
            )
            v1.execSQL(
                "INSERT INTO journal_entries (body, createdAtMs) VALUES ('also kept', 1000)"
            )
        }

        val v2 = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            LumiDatabase.MIGRATION_1_2,
        )

        assertFalse("emergency_contacts should be gone", v2.hasTable("emergency_contacts"))

        // The point of the test: the drop took only what it was aimed at.
        assertTrue("chats must survive", v2.hasTable("chats"))
        assertTrue("journal_entries must survive", v2.hasTable("journal_entries"))
        assertEquals(1, v2.countRows("chats"))
        assertEquals(1, v2.countRows("journal_entries"))
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.hasTable(name: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use {
            it.count > 0
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.countRows(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun assertEquals(expected: Int, actual: Int) =
        org.junit.Assert.assertEquals(expected.toLong(), actual.toLong())

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
