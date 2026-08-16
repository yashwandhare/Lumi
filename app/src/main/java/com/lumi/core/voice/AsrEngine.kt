package com.lumi.core.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * Where the speech engine stands.
 *
 * Kept separate from the session states because the two answer different questions: this one
 * says whether listening is *possible* right now (model downloaded, mic permitted), the
 * session says what Lumi is doing with it. A voice UI built on one flow would blur "still
 * fetching the recognition model" into "listening", which is how a silent button happens.
 */
sealed interface AsrState {

    /** Engine present but not prepared. Listening requires [prepare] first. */
    data object Idle : AsrState

    /** The recognition model is being fetched from the network. Carries its progress. */
    data class Preparing(val fraction: Float?, val downloadedBytes: Long) : AsrState

    /** Ready to listen. */
    data object Ready : AsrState

    /**
     * Cannot recognize. Unlike the model, this is not fatal — typed input still works, and
     * the honest failure path is to say so and offer the keyboard, not to hang.
     */
    data class Unavailable(val reason: String, val recoverable: Boolean) : AsrState
}

/**
 * Speech-to-text for voice input.
 *
 * Streaming is the contract, not an optimisation: v1 produced text only after the recording
 * stopped, and the seconds of dead air that created were the single reason its voice stack
 * felt broken. Implementations deliver [Partial] events as words settle and one [Final] when
 * the session ends.
 */
interface AsrEngine {

    val state: StateFlow<AsrState>

    /**
     * Fetches and loads the recognition model if needed. Idempotent, never throws; failures
     * land in [state]. The first call on a fresh phone is a ~41MB download, which is why the
     * state carries progress instead of being a boolean.
     */
    suspend fun prepare()

    /**
     * Runs one push-to-talk session and suspends until it ends.
     *
     * [onPartial] receives the running transcript as it grows, on every settled word. The
     * session ends when the speaker stops talking, the user releases the button, or
     * [cancel] is called; the return value says how.
     */
    suspend fun listen(onPartial: (String) -> Unit): AsrSessionResult

    /**
     * Ends a live session without waiting for an endpoint.
     *
     * The one call that makes "type and send while the mic is live" safe: the recording loop
     * stops promptly and [listen] returns [AsrSessionResult.Cancelled]. In v1 the mic simply
     * stayed live in the background, which is the bug this method exists to prevent.
     */
    fun cancel()
}

/** How a listening session ended. */
sealed interface AsrSessionResult {

    /** Recognised text, possibly blank when the utterance was silence. */
    data class Final(val text: String) : AsrSessionResult

    /** Stopped before the user finished. The partial transcript belongs to nobody. */
    data object Cancelled : AsrSessionResult

    /** The engine could not run. [reason] is in user language, recovery optional. */
    data class Failed(val reason: String, val recovery: String? = null) : AsrSessionResult
}
