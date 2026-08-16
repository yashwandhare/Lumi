package com.lumi.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.core.CapabilityInput
import com.lumi.core.CapabilityResult
import com.lumi.core.Dispatcher
import com.lumi.core.InteractionOrigin
import com.lumi.core.Router
import com.lumi.core.RouterOutcome
import com.lumi.core.StructuredIntent
import com.lumi.core.ai.GenerationRequest
import com.lumi.core.ai.ModelHarness
import com.lumi.core.ai.ModelState
import com.lumi.core.ai.SessionId
import com.lumi.core.audit.AuditLog
import com.lumi.core.model.AuditOutcome
import com.lumi.core.model.CapabilityId
import com.lumi.core.model.MessageRole
import com.lumi.core.voice.AsrEngine
import com.lumi.core.voice.AsrSessionResult
import com.lumi.core.voice.AsrState
import com.lumi.core.voice.ReplySpeaker
import com.lumi.data.chat.ChatRepository
import com.lumi.data.local.ChatEntity
import com.lumi.data.settings.SettingsStore
import com.lumi.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
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
    private val asr: AsrEngine,
    private val speaker: ReplySpeaker,
    private val router: Router,
    private val dispatcher: Dispatcher,
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

    /** The job running the current mic session, if one is live. */
    private var listeningJob: Job? = null

    /**
     * Speech is buffered to sentence-ish fragments rather than spoken per token. The queue rule
     * still holds — chunks are appended with QUEUE_ADD and the speak job is never cancelled at the
     * arrival of the next one — but a TTS flush on every five-token delta reads as stuttering
     * instead of talking.
     */
    private var speechBuffer = StringBuilder()

    /** So the next reply does not draw the same verb twice in a row. */
    private var lastVerb: String? = null

    /**
     * The running transcript while the mic is live. Null means not listening; an empty string
     * means listening with nothing recognised yet. The voice UI renders partial words from
     * this so the user can correct course before Lumi acts.
     */
    private val _listening = MutableStateFlow<String?>(null)
    val listening: StateFlow<String?> = _listening.asStateFlow()

    /** Engine availability — downloading the model, ready, or unavailable — for the UI. */
    val asrState: StateFlow<AsrState> = asr.state

    /** True while a reply is being read aloud. */
    val speaking: StateFlow<Boolean> = speaker.speaking

    /** The origin of the turn in flight. TTS speaks only turns the user spoke. */
    private var turnOrigin: InteractionOrigin = InteractionOrigin.TEXT

    /**
     * A non-chat intent that has been understood but not acted on. While non-null the UI shows
     * the confirmation surface; the dispatcher does nothing until the user answers it.
     */
    private val _pendingIntent = MutableStateFlow<PendingIntent?>(null)
    val pendingIntent: StateFlow<PendingIntent?> = _pendingIntent.asStateFlow()

    /**
     * True while a voice turn is live — from the spoken send through the reply being spoken.
     * The voice UI anchors on this: once the transcript is flowing and the mascot's voice is
     * done, the overlay gives the room back. Cleared by a typed send or a new conversation.
     */
    private val _voiceTurnActive = MutableStateFlow(false)
    val voiceTurnActive: StateFlow<Boolean> = _voiceTurnActive.asStateFlow()

    init {
        // The voice overlay ends itself when there is nothing left to do: not generating and
        // not speaking. Doing this here, in one place, keeps the overlay's exit condition from
        // being a guess scattered across the UI.
        //
        // **The settle delay is what makes it correct.** Generation finishing and speech starting
        // are not simultaneous: `flushSpeech` queues the last sentence, then the TTS engine reports
        // `speaking` only once it has actually begun. For that gap both flags read false, and the
        // previous version took that single idle sample as the end of the turn — so the overlay
        // closed the instant the model stopped writing and dropped the user into the chat screen
        // while the reply was still to be read aloud.
        //
        // `collectLatest` gives the delay for free: if either flag goes busy again while the timer
        // is running, this block is cancelled before it can end the turn. That is `debounce`'s
        // behaviour without depending on a preview API.
        viewModelScope.launch {
            combine(_generating, speaker.speaking) { generating, speaking ->
                generating || speaking
            }.collectLatest { busy ->
                if (!busy && _voiceTurnActive.value) {
                    delay(VOICE_TURN_SETTLE_MS)
                    _voiceTurnActive.value = false
                }
            }
        }
    }

    fun startVoiceSession() {
        if (listeningJob?.isActive == true || generation?.isActive == true) return
        speaker.stop()

        listeningJob = viewModelScope.launch {
            asr.prepare()
            if (asr.state.value !is AsrState.Ready) {
                // prepare() recorded why in its state; the UI renders it. The honest fallback
                // is typed input, not a mic button that silently does nothing.
                _listening.value = null
                return@launch
            }

            _listening.value = ""
            when (val result = asr.listen { partial -> _listening.value = partial }) {
                is AsrSessionResult.Final -> {
                    _listening.value = null
                    val transcript = result.text.trim()
                    if (transcript.isNotEmpty()) {
                        // Sherpa is local by construction, so the entry can say so — the privacy
                        // tension the decisions_devb entry flagged is gone by design.
                        recordVoiceSession(
                            AuditOutcome.SUCCESS,
                            detail = "Recognised on-device, no network.",
                        )
                        send(transcript, origin = InteractionOrigin.VOICE)
                    }
                }
                is AsrSessionResult.Cancelled -> {
                    // A partial transcript belongs to nobody — discard it, not store it.
                    _listening.value = null
                }
                is AsrSessionResult.Failed -> {
                    _listening.value = null
                    recordVoiceSession(AuditOutcome.FAILURE, detail = result.reason)
                }
            }
        }
    }

    /**
     * Ends a live listening session. **This is the cancel-on-send fix from v1**: if the mic is
     * live and the user types and hits send, the recording loop stops cleanly instead of
     * staying live in the background.
     */
    fun stopVoiceSession() {
        if (listeningJob?.isActive != true) return
        asr.cancel()
        listeningJob?.cancel()
        listeningJob = null
        _listening.value = null
    }

    /** Silences a reply already being read. Separate from [stop] because generation is done by then. */
    fun stopSpeaking() {
        speaker.stop()
        speechBuffer.setLength(0)
    }

    /**
     * Runs the confirmed intent through the dispatcher and reports the outcome in the
     * transcript.
     *
     * The capability writes its own audit entry — the dispatcher cannot, because only the
     * capability knows what actually happened (Capability contract) — so nothing is recorded
     * here unless dispatch itself fails to reach one.
     */
    fun confirmPendingIntent() {
        val pending = _pendingIntent.value ?: return
        _pendingIntent.value = null

        val origin = turnOrigin
        _turns.value += ChatTurn(TurnRole.MODEL, "", streaming = true)
        generation = viewModelScope.launch {
            try {
                val result = dispatcher.dispatch(
                    CapabilityInput(intent = pending.intent, origin = origin)
                )
                updateLastModelTurn { it.copy(text = result.userMessage, streaming = false) }
            } catch (t: Throwable) {
                updateLastModelTurn { current ->
                    current.copy(
                        text = "Lumi could not do that right now. Try again.",
                        streaming = false,
                    )
                }
                recordIntent(
                    pending.intent.capability,
                    AuditOutcome.FAILURE,
                    "Could not run: ${t::class.simpleName ?: "error"}.",
                )
            }
        }
    }

    /**
     * The user saw what Lumi understood and said no. Nothing ran, so nothing needs an audit
     * entry — the exchange still gets a closing line so the transcript does not end on a
     * dangling request.
     */
    fun cancelPendingIntent() {
        if (_pendingIntent.value == null) return
        _pendingIntent.value = null
        _turns.value += ChatTurn(TurnRole.MODEL, "Okay, I will not do that.")
    }

    /** The router could not settle it. Show the question, ask in plain words. */
    private suspend fun showClarification(question: String, conversationId: Long?) {
        finishReply()
        updateLastModelTurn { it.copy(text = question, streaming = false) }
        if (conversationId != null) {
            chats.appendModelMessage(conversationId, question, System.currentTimeMillis())
        }
        recordTurn(AuditOutcome.SUCCESS, "Asked a clarifying question")
    }

    /**
     * Runs ordinary chat generation by dispatching the chat capability, so chat travels the
     * same one dispatch path as every other capability instead of calling the harness on a
     * private road. The reply streams back as [com.lumi.core.CapabilityResult.Streaming] and is
     * collected here; conversation persistence and the turn's audit entry stay with the view
     * model because they need the conversation id this capability is not built to hold.
     */
    private suspend fun runChatGeneration(prompt: String, conversationId: Long?) {
        val result = dispatcher.dispatch(
            CapabilityInput(
                intent = StructuredIntent(
                    capability = CapabilityId.CHAT,
                    rawText = prompt,
                    slots = mapOf(StructuredIntent.SLOT_SESSION to sessionId.value),
                ),
                origin = turnOrigin,
            )
        )

        if (result is CapabilityResult.Streaming) {
            result.chunks.collect { delta ->
                appendToReply(delta)
                feedSpeech(delta)
            }
        } else {
            updateLastModelTurn { it.copy(text = result.userMessage, streaming = false) }
            return
        }

        val reply = currentReplyText()
        finishReply()
        if (conversationId != null && reply.isNotBlank()) {
            chats.appendModelMessage(conversationId, reply, System.currentTimeMillis())
        }
        recordTurn(AuditOutcome.SUCCESS, "Answered a message")
    }

    /**
     * The chat turn's placeholder goes away when a pending intent replaces it: the transcript
     * shows the user's request, and the confirmation dialog owns the reply until the user
     * answers.
     */
    private fun removeLastModelTurn() {
        _turns.value = _turns.value.dropLast(1)
    }

    /**
     * Writes the fact of a dispatched action to the audit log. Capabilities write their own
     * details; this covers reaching one.
     */
    private fun recordIntent(capability: CapabilityId, outcome: AuditOutcome, detail: String) {
        applicationScope.launch {
            audit.record(capability = capability, summary = "Acted on a routed request", outcome = outcome, detail = detail)
        }
    }

    fun send(text: String, origin: InteractionOrigin = InteractionOrigin.TEXT) {
        val prompt = text.trim()
        if (prompt.isEmpty() || generation?.isActive == true) return

        // v1's mic bug, fixed at the door: a typed send while the mic is live cancels the
        // recording loop cleanly rather than leaving it running in the background.
        stopVoiceSession()
        // A spoken reply already being read out must not keep talking over the new turn.
        speaker.stop()

        turnOrigin = origin
        // The voice overlay anchors on this: a spoken turn shows listening → thinking → speaking,
        // a typed turn keeps the transcript as the whole screen.
        _voiceTurnActive.value = origin == InteractionOrigin.VOICE
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
                when (val outcome = router.route(prompt, origin)) {
                    is RouterOutcome.Ambiguous ->
                        // Ask the short question instead of guessing a risky action: guessing
                        // is the worse behaviour for a product that can flip settings and
                        // fetch mail.
                        showClarification(outcome.question, conversationId)

                    is RouterOutcome.Routed -> {
                        if (outcome.decision.intent.capability == CapabilityId.CHAT) {
                            runChatGeneration(prompt, conversationId)
                        } else {
                            // Consequence-bearing route: show what was understood, act only on a yes.
                            // No audit entry yet — showing a dialog is not an action against the
                            // world; the capability writes its own when it actually runs.
                            _pendingIntent.value = PendingIntent(
                                intent = outcome.decision.intent,
                                description = describeIntent(outcome.decision.intent),
                            )
                            removeLastModelTurn()
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                // Stopping is something the user did, not a failure. This has to be caught before
                // Throwable and rethrown: catching it as a failure appended "could not finish that
                // reply" to a reply the user chose to end, and swallowing it would leave the parent
                // scope believing the job completed.
                //
                // The partial text is kept, so it has to be stored too. Leaving it on screen but out of
                // the database means the answer is there until the user reopens the conversation and
                // then silently is not — the same class of bug as history not working at all.
                speaker.stop()
                speechBuffer.setLength(0)
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
                speaker.stop()
                speechBuffer.setLength(0)
                replaceReplyWithFailure()
                recordTurn(AuditOutcome.FAILURE, "A reply could not be generated", t)
            } finally {
                flushSpeech()
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
        speaker.stop()
        speechBuffer.setLength(0)
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

    /**
     * Hands a streamed delta to TTS when the turn was spoken.
     *
     * **Two v1 rules hold here.** Speak only turns whose origin is VOICE — a typed turn keeps
     * the phone silent. And the queue appends: [speakChunk] uses QUEUE_ADD and never cancels
     * the speak job at the arrival of the next chunk, which is the fix for the engine
     * interrupting its own sentence and skipping words.
     *
     * Deltas are buffered until a sentence boundary so TTS gets speakable fragments rather
     * than one token at a time.
     */
    private fun feedSpeech(delta: String) {
        if (turnOrigin != InteractionOrigin.VOICE) return
        speechBuffer.append(delta)
        val text = speechBuffer.toString()

        val cut = text.lastIndexOfAny(SENTENCE_BREAKS)
        if (cut >= MIN_SPEECH_CHUNK) {
            speakChunk(text.substring(0, cut + 1))
            speechBuffer = StringBuilder(text.substring(cut + 1))
            return
        }

        // **A fallback break on length, not only on punctuation.** A model that answers in one long
        // unpunctuated run — a list, a code line, a sentence still in progress — produced no
        // sentence mark at all, so nothing was spoken until the reply finished and then the whole
        // thing arrived at once. Past this length, break at the last word boundary instead so
        // speech keeps pace with the text. Breaking on a space and never mid-word: a chunk cut
        // through a word is pronounced as two non-words.
        if (text.length >= MAX_SPEECH_CHUNK) {
            val space = text.lastIndexOf(' ')
            if (space >= MIN_SPEECH_CHUNK) {
                speakChunk(text.substring(0, space))
                speechBuffer = StringBuilder(text.substring(space + 1))
            }
        }
    }

    /** Queues whatever is left once the reply finishes. Only for spoken turns. */
    private fun flushSpeech() {
        if (turnOrigin != InteractionOrigin.VOICE) return
        val remainder = speechBuffer.toString()
        speechBuffer.setLength(0)
        if (remainder.isNotBlank()) speakChunk(remainder)
    }

    private fun speakChunk(text: String) {
        // Markdown syntax is noise when spoken. Strip the common markers rather than reading
        // asterisks and backticks aloud — the transcript still renders them.
        val spoken = text
            .replace(Regex("`{1,3}"), "")
            .replace(Regex("\\*{1,2}"), "")
            .trim()
        if (spoken.isNotEmpty()) speaker.speak(spoken)
    }

    /**
     * Audit a voice session the same way a chat turn is audited: the fact and the outcome,
     * never what was said.
     */
    private fun recordVoiceSession(outcome: AuditOutcome, detail: String? = null) {
        applicationScope.launch {
            audit.record(
                capability = CapabilityId.CHAT,
                summary = "Listened for a spoken request",
                outcome = outcome,
                detail = detail,
            )
        }
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

        /** Where TTS may break a streaming reply into speakable chunks. */
        val SENTENCE_BREAKS = charArrayOf('.', '!', '?', ';', '\n')
        /**
         * Below this many characters a buffered fragment is not worth speaking — a lone "I"
         * after a period is a false sentence boundary that would interrupt naturally as part
         * of the next fragment.
         */
        const val MIN_SPEECH_CHUNK = 12

        /**
         * The length at which a reply is broken at a word boundary even with no punctuation in
         * sight, so speech keeps pace with a long unpunctuated run instead of arriving all at once
         * when the reply ends. Roughly a spoken breath's worth of text.
         */
        const val MAX_SPEECH_CHUNK = 160

        /**
         * How long generating and speaking must *both* stay false before a voice turn is over.
         * Covers the gap between the last token and the TTS engine reporting that it has started —
         * see the note in `init`. Long enough for engine start-up, short enough that the overlay
         * does not linger once the reply really has finished.
         */
        const val VOICE_TURN_SETTLE_MS = 700L
    }
}
