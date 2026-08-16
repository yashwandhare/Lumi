package com.lumi.ui.chat

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lumi.core.ai.GenerationMetrics
import com.lumi.core.ai.GenerationRequest
import com.lumi.core.ai.ModelHarness
import com.lumi.core.ai.ModelState
import com.lumi.core.ai.SessionId
import com.lumi.core.audit.AuditLog
import com.lumi.core.model.AuditOutcome
import com.lumi.core.model.CapabilityId
import com.lumi.core.settings.ModelBackend
import com.lumi.data.chat.ChatRepository
import com.lumi.data.local.LumiDatabase
import com.lumi.data.settings.SettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the chat turn is audited, and audited *truthfully* — a stopped reply is recorded as stopped
 * rather than as a failure, and never as a success.
 *
 * This runs as an instrumented test rather than a JVM one because `ChatRepository` and `SettingsStore`
 * are concrete classes over Room and `SharedPreferences`. Faking them would mean adding a mocking
 * library or opening two classes purely for a test; using the real ones against an in-memory database
 * costs a device but tests the code that actually ships. Only [ModelHarness] is faked, and it is an
 * interface precisely so it can be.
 *
 * The reason this exists at all: **the audit log is how the privacy claim is proved.** A log that says
 * a reply failed when the user stopped it, or that omits a turn entirely, is worse than no log — it
 * makes the record a liar in the one place users are asked to trust it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ChatAuditTest {

    private lateinit var database: LumiDatabase
    private lateinit var audit: AuditLog
    private lateinit var harness: FakeHarness
    private lateinit var viewModel: ChatViewModel

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Room dispatches its suspend queries onto its own executors, which the test scheduler knows
        // nothing about — so `advanceUntilIdle()` would return before an insert had landed and every
        // assertion here would read an empty table. Pointing both executors at the test dispatcher puts
        // Room's work on the same clock as the coroutines under test.
        database = Room.inMemoryDatabaseBuilder(context, LumiDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        audit = AuditLog(database.auditDao())
        harness = FakeHarness()
        viewModel = ChatViewModel(
            harness = harness,
            chats = ChatRepository(database.chatDao(), dispatcher),
            settings = SettingsStore(context),
            audit = audit,
            applicationScope = CoroutineScope(dispatcher),
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun aCompletedReplyIsRecordedAsSuccess() = runTest(dispatcher) {
        harness.emit = listOf("Hello", " there")

        viewModel.send("hi")
        advanceUntilIdle()

        val events = audit.observeRecent().first()
        assertEquals(1, events.size)
        assertEquals(CapabilityId.CHAT, events.first().capability)
        assertEquals(AuditOutcome.SUCCESS, events.first().outcome)
    }

    @Test
    fun aStoppedReplyIsRecordedAsPartialAndNotAsAFailure() = runTest(dispatcher) {
        // A reply that starts, emits once, then waits — so there is something to stop.
        harness.emit = listOf("Half an ans")
        harness.blockAfterEmitting = CompletableDeferred()

        viewModel.send("hi")
        advanceUntilIdle()

        viewModel.stop()
        advanceUntilIdle()

        val events = audit.observeRecent().first()
        assertEquals(1, events.size)
        assertEquals(
            "stopping is the user's action, not a failure",
            AuditOutcome.PARTIAL,
            events.first().outcome,
        )

        // The bug this guards: `catch (Throwable)` caught the cancellation and appended the failure
        // copy, so pressing Stop told the user their reply had broken.
        val transcript = viewModel.turns.value.last().text
        assertTrue("partial text is kept", transcript.contains("Half an ans"))
        assertFalse("no failure copy on a stop", transcript.contains("could not finish"))

        // And it is on disk, not only on screen. A partial answer that vanishes when the conversation
        // is reopened is the same bug as history not working.
        val stored = database.chatDao().observeMessages(1L).first()
        assertTrue(
            "the partial reply is persisted",
            stored.any { it.text.contains("Half an ans") },
        )
    }

    @Test
    fun aFailedReplyIsRecordedAsFailure() = runTest(dispatcher) {
        harness.failWith = IllegalStateException("engine gone")

        viewModel.send("hi")
        advanceUntilIdle()

        val events = audit.observeRecent().first()
        assertEquals(1, events.size)
        assertEquals(AuditOutcome.FAILURE, events.first().outcome)
        assertTrue(viewModel.turns.value.last().text.contains("could not finish"))
    }

    @Test
    fun theAuditEntryDoesNotContainThePromptOrTheReply() = runTest(dispatcher) {
        // The whole point of the content rule: the conversation is stored once, in the chat tables.
        harness.emit = listOf("the capital is Paris")

        viewModel.send("what is the capital of France")
        advanceUntilIdle()

        val event = audit.observeRecent().first().single()
        val recorded = listOf(event.summary, event.subject, event.detail).joinToString(" ")
        assertFalse("the prompt must not be copied", recorded.contains("France"))
        assertFalse("the reply must not be copied", recorded.contains("Paris"))
        // What it must contain instead: evidence the inference was local.
        assertTrue("the backend is the on-device evidence", recorded.contains("On this device"))
    }

    /** Emits what the test tells it to, then optionally waits so the reply can be stopped. */
    private class FakeHarness : ModelHarness {
        var emit: List<String> = emptyList()
        var failWith: Throwable? = null
        var blockAfterEmitting: CompletableDeferred<Unit>? = null

        override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Ready)
        override val lastMetrics: StateFlow<GenerationMetrics?> = MutableStateFlow(
            GenerationMetrics(
                totalMs = 1_200,
                timeToFirstTokenMs = 300,
                approxTokens = 8,
                tokensPerSecond = 6.6,
            ),
        )

        override suspend fun prepare() = Unit
        override suspend fun reload() = Unit
        override fun activeBackend(): ModelBackend = ModelBackend.CPU
        override suspend fun complete(request: GenerationRequest): String = emit.joinToString("")
        override suspend fun resetSession(sessionId: SessionId) = Unit

        override fun generate(request: GenerationRequest): Flow<String> = flow {
            failWith?.let { throw it }
            emit.forEach { emit(it) }
            // Suspends forever until the test cancels it, which is what a real slow reply looks like.
            blockAfterEmitting?.await()
        }
    }
}
