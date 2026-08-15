package com.lumi.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lumi.core.model.ActionType
import com.lumi.core.model.AuditOutcome
import com.lumi.core.model.CapabilityId
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

    private fun newDocument(uri: String) = DocumentEntity(
        uri = uri,
        displayName = uri.substringAfterLast('/'),
        mimeType = "application/pdf",
        sizeBytes = 2_048L,
        addedAtMs = 1_000L,
    )
}
