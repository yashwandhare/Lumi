package com.lumi.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lumi.core.model.ActionType
import com.lumi.core.model.AuditOutcome
import com.lumi.core.model.CapabilityId
import com.lumi.core.model.ReminderKind
import com.lumi.core.model.ReminderStatus
import com.lumi.core.model.TriggerType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the schema against real SQLite on a real device.
 *
 * The unit tests cover the converters in isolation. These cover what only a database can:
 * that the two transactions hold, that the relation graph loads, that cascades fire, and
 * that a vector survives a round trip through an actual BLOB column.
 */
@RunWith(AndroidJUnit4::class)
class LumiDatabaseTest {

    private lateinit var database: LumiDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LumiDatabase::class.java,
        ).build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun ingestingADocumentStoresItsChunksAndVectorsTogether() = runTest {
        val dao = database.documentDao()
        val converters = Converters()
        val vectors = listOf(
            converters.fromFloatArray(floatArrayOf(0.1f, -0.2f, 0.3f))!!,
            converters.fromFloatArray(floatArrayOf(-0.4f, 0.5f, -0.6f))!!,
        )

        val documentId = dao.saveIndexedDocument(
            document = newDocument(uri = "content://notes/dbms.pdf"),
            chunkTexts = listOf("first chunk", "second chunk"),
            vectors = vectors,
            nowMs = 1_000L,
        )

        val chunks = dao.chunksOf(documentId)
        assertEquals(listOf("first chunk", "second chunk"), chunks.map { it.text })
        assertEquals(2, dao.observeChunkCount().first())

        val embeddings = dao.allEmbeddings().sortedBy { it.chunkId }
        assertEquals(2, embeddings.size)
        assertArrayEquals(
            floatArrayOf(0.1f, -0.2f, 0.3f),
            converters.toFloatArray(embeddings.first().vector),
            1e-6f,
        )
    }

    @Test
    fun deletingADocumentRemovesItsChunksAndVectors() = runTest {
        val dao = database.documentDao()
        val documentId = dao.saveIndexedDocument(
            document = newDocument(uri = "content://notes/gone.pdf"),
            chunkTexts = listOf("orphan candidate"),
            vectors = listOf(Converters().fromFloatArray(floatArrayOf(1f))!!),
            nowMs = 1_000L,
        )

        dao.deleteDocument(documentId)

        assertEquals(emptyList<DocumentChunkEntity>(), dao.chunksOf(documentId))
        assertEquals(emptyList<ChunkEmbeddingEntity>(), dao.allEmbeddings())
    }

    @Test
    fun aRoutineWithTwoTriggersOfOneKindIsReturnedOnceSoItCannotFireTwice() = runTest {
        val dao = database.routineDao()
        val routineId = dao.saveRoutine(
            routine = RoutineEntity(
                name = "College arrival",
                sourceText = "when I get to college turn on wifi and silence my phone",
                createdAtMs = 1_000L,
                updatedAtMs = 1_000L,
            ),
            triggers = listOf(
                RoutineTriggerEntity(routineId = 0, type = TriggerType.WIFI, value = "CollegeWifi"),
                RoutineTriggerEntity(routineId = 0, type = TriggerType.WIFI, value = "CollegeGuest"),
            ),
            actions = listOf(
                RoutineActionEntity(routineId = 0, ordinal = 0, type = ActionType.WIFI, value = "on"),
                RoutineActionEntity(routineId = 0, ordinal = 1, type = ActionType.SILENT_MODE, value = "on"),
            ),
        )

        val matches = dao.enabledRoutinesFor(TriggerType.WIFI)

        assertEquals(1, matches.size)
        assertEquals(routineId, matches.single().routine.id)
        assertEquals(2, matches.single().triggers.size)
        assertEquals(
            listOf(ActionType.WIFI, ActionType.SILENT_MODE),
            matches.single().orderedActions.map { it.type },
        )
    }

    @Test
    fun aDisabledRoutineNeverMatchesATrigger() = runTest {
        val dao = database.routineDao()
        val routineId = dao.saveRoutine(
            routine = RoutineEntity(
                name = "Bedtime",
                sourceText = "at 11pm turn on silent",
                createdAtMs = 1_000L,
                updatedAtMs = 1_000L,
            ),
            triggers = listOf(RoutineTriggerEntity(routineId = 0, type = TriggerType.TIME, value = "23:00")),
            actions = listOf(RoutineActionEntity(routineId = 0, ordinal = 0, type = ActionType.SILENT_MODE, value = "on")),
        )

        dao.setEnabled(routineId, enabled = false, nowMs = 2_000L)

        assertEquals(emptyList<RoutineWithGraph>(), dao.enabledRoutinesFor(TriggerType.TIME))
    }

    @Test
    fun theAuditLogKeepsOnlyTheNewestRowsWhenTrimmed() = runTest {
        val dao = database.auditDao()
        repeat(5) { index ->
            dao.insert(
                AuditEventEntity(
                    occurredAtMs = index.toLong(),
                    capability = CapabilityId.ROUTINE,
                    summary = "fired $index",
                    outcome = AuditOutcome.SUCCESS,
                ),
            )
        }

        dao.trimTo(keep = 2)

        val remaining = dao.observeRecent(limit = 10).first()
        assertEquals(listOf("fired 4", "fired 3"), remaining.map { it.summary })
    }

    @Test
    fun aDocumentIsNotIndexedUntilItsChunksLand() = runTest {
        val dao = database.documentDao()
        dao.insertDocument(newDocument(uri = "content://notes/pending.pdf"))

        assertNull(dao.findByUri("content://notes/pending.pdf")?.indexedAtMs)
    }

    @Test
    fun anActiveListShowsBothKindsSoonestFirstAndNeverDueLast() = runTest {
        val dao = database.reminderDao()
        dao.insert(newReminder(kind = ReminderKind.TODO, text = "buy charger", dueAtMs = Long.MAX_VALUE))
        dao.insert(newReminder(kind = ReminderKind.REMINDER, text = "call mom", dueAtMs = 6_000))
        dao.insert(newReminder(kind = ReminderKind.REMINDER, text = "standup", dueAtMs = 2_000))

        val active = dao.observeActive().first()

        assertEquals(listOf("standup", "call mom", "buy charger"), active.map { it.text })
    }

    @Test
    fun completedAndDismissedItemsLeaveTheActiveList() = runTest {
        val dao = database.reminderDao()
        val doneId = dao.insert(newReminder(text = "one", dueAtMs = 1_000))
        val dismissedId = dao.insert(newReminder(text = "two", dueAtMs = 2_000))

        dao.markDone(doneId, nowMs = 3_000)
        dao.markDismissed(dismissedId, nowMs = 3_000)

        assertEquals(emptyList<ReminderEntity>(), dao.observeActive().first())
        val done = dao.find(doneId)!!
        assertEquals(ReminderStatus.DONE, done.status)
        assertEquals(3_000L, done.doneAtMs)
        val dismissed = dao.find(dismissedId)!!
        assertEquals(ReminderStatus.DISMISSED, dismissed.status)
    }

    @Test
    fun markFiredSucceedsExactlyOnceSoARetriedWorkerCannotNotifyTwice() = runTest {
        val dao = database.reminderDao()
        val reminderId = dao.insert(newReminder(text = "once", dueAtMs = 1_000))

        assertEquals(1, dao.markFired(reminderId, nowMs = 2_000))
        assertEquals(0, dao.markFired(reminderId, nowMs = 3_000))

        val row = dao.find(reminderId)!!
        assertEquals(ReminderStatus.FIRED, row.status)
        assertEquals(2_000L, row.firedAtMs)
    }

    @Test
    fun markingAUserDismissedReminderFiredUpdatesNothing() = runTest {
        val dao = database.reminderDao()
        val reminderId = dao.insert(newReminder(text = "race", dueAtMs = 1_000))
        dao.markDismissed(reminderId, nowMs = 2_000)

        assertEquals(0, dao.markFired(reminderId, nowMs = 3_000))

        assertEquals(ReminderStatus.DISMISSED, dao.find(reminderId)!!.status)
    }

    @Test
    fun dueAtOrBeforeReturnsOnlyPendingRowsWhoseTimeHasCome() = runTest {
        val dao = database.reminderDao()
        dao.insert(newReminder(text = "due", dueAtMs = 1_000))
        dao.insert(newReminder(text = "future", dueAtMs = 9_000))
        val firedId = dao.insert(newReminder(text = "already fired", dueAtMs = 500))
        dao.markFired(firedId, nowMs = 600)

        val due = dao.dueAtOrBefore(nowMs = 2_000)

        assertEquals(listOf("due"), due.map { it.text })
    }

    @Test
    fun reschedulingAReminderMovesDueTimeForwardOnlyFromTheFiredState() = runTest {
        val dao = database.reminderDao()
        val reminderId = dao.insert(
            newReminder(text = "water plants", dueAtMs = 1_000).copy(repeatIntervalMs = 86_400_000),
        )
        dao.markFired(reminderId, nowMs = 2_000)

        assertEquals(1, dao.reschedule(reminderId, nextDueAtMs = 87_400_000, repeatIntervalMs = 86_400_000, nowMs = 2_000))

        val row = dao.find(reminderId)!!
        assertEquals(ReminderStatus.PENDING, row.status)
        assertEquals(87_400_000L, row.dueAtMs)
        // Second reschedule attempt fails — the row is no longer FIRED.
        assertEquals(0, dao.reschedule(reminderId, nextDueAtMs = 87_400_000, repeatIntervalMs = 86_400_000, nowMs = 3_000))
    }

    private fun newReminder(
        kind: ReminderKind = ReminderKind.REMINDER,
        text: String,
        dueAtMs: Long,
    ) = ReminderEntity(
        kind = kind,
        text = text,
        status = ReminderStatus.PENDING,
        dueAtMs = dueAtMs,
        createdAtMs = 1_000L,
        updatedAtMs = 1_000L,
    )

    private fun newDocument(uri: String) = DocumentEntity(
        uri = uri,
        displayName = uri.substringAfterLast('/'),
        mimeType = "application/pdf",
        sizeBytes = 2_048L,
        addedAtMs = 1_000L,
    )
}
