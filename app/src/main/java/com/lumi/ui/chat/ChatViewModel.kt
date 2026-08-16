package com.lumi.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.core.ai.GenerationRequest
import com.lumi.core.ai.ModelHarness
import com.lumi.core.ai.ModelState
import com.lumi.core.ai.SessionId
import com.lumi.core.audit.AuditLog
import com.lumi.core.model.AuditOutcome
import com.lumi.core.model.CapabilityId
import com.lumi.core.model.MessageRole
import com.lumi.data.chat.ChatRepository
import com.lumi.data.local.ChatEntity
import com.lumi.data.settings.SettingsStore
import com.lumi.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * Drives the chat loop: text in, streamed text out, both ends persisted.
 *
 * **One session id per conversation, and it changes when the conversation does.** The harness keeps a
 * `Conversation` per [SessionId] and that conversation holds the KV cache, so reusing an id is what
 * makes the model remember the last few turns. It follows that starting a new chat has to both clear
 * the screen and reset that session — clearing only the screen would leave the model still primed with
 * a conversation the user believes they ended, which is the bug "new chat doesn't work" describes.
 *
 * Every turn is written to the [AuditLog], including the ones that fail or get stopped. Chat is the
 * first capability to do so, and the pattern here is the one the rest follow: record the fact and the
 * backend, never the content. See [recordTurn].
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val harness: ModelHarness,
    private val chats: ChatRepository,
    private val settings: SettingsStore,
    private val audit: AuditLog,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    /** The verb for the reply in flight. Null when idle. One per reply, not a rotation. */
    private val _thinkingVerb = MutableStateFlow<String?>(null)
    val thinkingVerb: StateFlow<String?> = _thinkingVerb.asStateFlow()

    /** Every stored conversation, newest first. The history drawer renders this. */
    val history: StateFlow<List<ChatEntity>> = chats.observeChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * True when the composer should accept a send: model loaded, nothing already decoding.
     *
     * A send button that looks alive before the model is ready produces a tap that silently does
     * nothing, which is exactly the bug this screen shipped with.
     */
    val canSend: StateFlow<Boolean> =
        combine(harness.state, _generating) { model, generating ->
            model is ModelState.Ready && !generating
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val modelState: StateFlow<ModelState> = harness.state

    /** Whether the mascot should dock and react during a conversation. A user preference. */
    val liveMascot: StateFlow<Boolean> = settings.model
        .map { it.liveMascot }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Null until the first message, because a chat row is only created when there is one. */
    private var chatId: Long? = null
    private var sessionId: SessionId = newSessionId()
    private var generation: Job? = null

    /** So the next reply does not draw the same verb twice in a row. */
    private var lastVerb: String? = null

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || generation?.isActive == true) return

        _turns.value += ChatTurn(TurnRole.USER, prompt)
        _turns.value += ChatTurn(TurnRole.MODEL, "", streaming = true)
        _generating.value = true
        // One verb for this reply, chosen now and held until the reply lands. Rotating it mid-wait
        // drew the eye to the label instead of the answer, and made a slow reply feel like several
        // failed attempts rather than one in progress.
        _thinkingVerb.value = THINKING_VERBS.filter { it != lastVerb }.random(random)
            .also { lastVerb = it }

        generation = viewModelScope.launch {
            val now = System.currentTimeMillis()
            val conversationId = runCatching { chats.appendUserMessage(chatId, prompt, now) }
                .getOrNull()
                ?.also { chatId = it }

            try {
                harness.generate(
                    GenerationRequest(prompt = prompt, sessionId = sessionId),
                ).collect { delta -> appendToReply(delta) }

                val reply = currentReplyText()
                finishReply()
                if (conversationId != null && reply.isNotBlank()) {
                    chats.appendModelMessage(conversationId, reply, System.currentTimeMillis())
                }
                recordTurn(AuditOutcome.SUCCESS, "Answered a message")
            } catch (cancellation: CancellationException) {
                // Stopping is something the user did, not a failure. This has to be caught before
                // Throwable and rethrown: catching it as a failure appended "could not finish that
                // reply" to a reply the user chose to end, and swallowing it would leave the parent
                // scope believing the job completed.
                //
                // The partial text is kept, so it has to be stored too. Leaving it on screen but out of
                // the database means the answer is there until the user reopens the conversation and
                // then silently is not — the same class of bug as history not working at all.
                persistOutsideThisJob(conversationId, currentReplyText())
                recordTurn(AuditOutcome.PARTIAL, "Reply stopped before it finished")
                throw cancellation
            } catch (t: Throwable) {
                // Surfaced in the transcript rather than swallowed. A chat that silently stops
                // producing text is indistinguishable from one that is still thinking.
                //
                // Deliberately *not* persisted, unlike the stopped case. A truncated answer stored
                // without the failure notice beside it reads as a complete one on reopen, and a reply
                // that misrepresents itself is worse than a reply that is missing.
                replaceReplyWithFailure()
                recordTurn(AuditOutcome.FAILURE, "A reply could not be generated", t)
            } finally {
                _generating.value = false
                _thinkingVerb.value = null
            }
        }
    }

    /** Abandons the current reply, keeping whatever text already arrived. */
    fun stop() {
        generation?.cancel()
        generation = null
        _generating.value = false
        _thinkingVerb.value = null
        finishReply()
    }

    /**
     * Start a fresh conversation.
     *
     * Three things have to happen together, and the previous stub did none of them: the screen clears,
     * the next message starts a new chat row rather than appending to the last one, and the model's
     * session is reset so it is not still holding the old conversation's cache.
     */
    fun newConversation() {
        stop()
        _turns.value = emptyList()
        val ending = sessionId
        chatId = null
        sessionId = newSessionId()
        viewModelScope.launch { harness.resetSession(ending) }
    }

    /**
     * Reopen a stored conversation.
     *
     * The transcript is restored from disk, but the model's session is **not** — its KV cache did not
     * survive the process and cannot be rebuilt without replaying every turn through it, which would
     * cost as long as the original conversation took. So the model sees a fresh session while the user
     * sees their history. That is the honest trade; the alternative is a minute of silent prefill.
     */
    fun openConversation(id: Long) {
        stop()
        chatId = id
        sessionId = newSessionId()
        viewModelScope.launch {
            val stored = chats.observeMessages(id).first()
            _turns.value = stored.map { message ->
                ChatTurn(
                    role = if (message.role == MessageRole.USER) TurnRole.USER else TurnRole.MODEL,
                    text = message.text,
                )
            }
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            chats.deleteChat(id)
            if (chatId == id) newConversation()
        }
    }

    private fun currentReplyText(): String =
        _turns.value.lastOrNull { it.role == TurnRole.MODEL }?.text.orEmpty()

    private fun appendToReply(delta: String) = updateLastModelTurn { current ->
        current.copy(text = current.text + delta, streaming = true)
    }

    private fun finishReply() = updateLastModelTurn { current ->
        current.copy(
            text = current.text.ifBlank { EMPTY_REPLY },
            streaming = false,
            metrics = harness.lastMetrics.value.takeIf { settings.model.value.showMetrics },
        )
    }

    private fun replaceReplyWithFailure() = updateLastModelTurn { current ->
        // Keep partial output if any arrived — half an answer is more useful than none, and discarding
        // it would also discard the evidence of where it stopped.
        val prefix = if (current.text.isBlank()) "" else current.text.trimEnd() + "\n\n"
        current.copy(text = prefix + GENERATION_FAILED, streaming = false)
    }

    private inline fun updateLastModelTurn(transform: (ChatTurn) -> ChatTurn) {
        val turns = _turns.value
        val index = turns.indexOfLast { it.role == TurnRole.MODEL }
        if (index < 0) return
        _turns.value = turns.toMutableList().also { it[index] = transform(it[index]) }
    }

    private fun newSessionId() = SessionId("chat-${System.currentTimeMillis()}")

    /**
     * Store a reply from outside the generation job.
     *
     * Needed because the caller is a cancelled coroutine, and a cancelled coroutine cannot suspend —
     * a Room write from inside the `catch` would be dropped.
     */
    private fun persistOutsideThisJob(conversationId: Long?, reply: String) {
        if (conversationId == null || reply.isBlank()) return
        applicationScope.launch {
            runCatching { chats.appendModelMessage(conversationId, reply, System.currentTimeMillis()) }
        }
    }

    /**
     * Write the turn to the audit log.
     *
     * **On the application scope, not [viewModelScope].** A stopped reply cancels the generation job,
     * and a cancelled coroutine cannot suspend — a Room insert from inside the `catch` would be
     * dropped precisely in the case worth recording.
     *
     * **No prompt text and no reply text, ever.** The conversation is already stored once, in the chat
     * tables the user can read and delete. Copying it into the audit table would give the same words a
     * second home with different retention and put them on a screen whose purpose is the opposite —
     * showing *that* Lumi acted, not repeating what was said. What belongs here is the fact of the turn
     * and the backend it ran on, because that backend is the evidence the inference was local.
     */
    private fun recordTurn(
        outcome: AuditOutcome,
        summary: String,
        error: Throwable? = null,
    ) {
        val backend = settings.model.value.backend.label
        val metrics = harness.lastMetrics.value
        val timing = metrics?.let { " ${it.approxTokens} tokens in ${it.totalMs / 1000.0}s." } ?: ""
        val cause = error?.let { " ${it::class.simpleName ?: "Error"}." } ?: ""
        applicationScope.launch {
            audit.record(
                capability = CapabilityId.CHAT,
                summary = summary,
                outcome = outcome,
                detail = "On this device, $backend.$timing$cause",
            )
        }
    }

    private val random = Random(System.currentTimeMillis())

    private companion object {
        const val EMPTY_REPLY = "Lumi had nothing to add to that."
        const val GENERATION_FAILED = "Lumi could not finish that reply. Try asking again."
    }
}
