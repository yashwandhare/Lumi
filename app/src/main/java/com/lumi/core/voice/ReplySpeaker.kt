package com.lumi.core.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * Reads replies aloud.
 *
 * **Speaks only turns that were spoken.** [com.lumi.core.InteractionOrigin] travels from the
 * mic to the reply for this reason: v1 read every reply aloud, including ones answering
 * typed questions, which made the phone narrate itself at random moments. A reply to a typed
 * turn is text; a reply to a spoken turn is speech. Both, never.
 *
 * The queue appends, it never restarts. Streamed chunks arrive while the engine is still
 * speaking the previous ones, and cancelling the speak job per chunk — v1's behaviour — made
 * the engine interrupt its own sentence, skip words, and start over mid-phrase.
 */
interface ReplySpeaker {

    /** Whether the engine initialized. False means speaking is unavailable, not an error. */
    val available: Boolean

    /** True while speech is playing. The voice UI anchors its speaking state on this. */
    val speaking: StateFlow<Boolean>

    /**
     * Readies the engine, fetching any models it needs. Idempotent, never throws; a caller
     * simply observes [available] afterwards. The platform engine needs no preparation; an
     * on-device neural voice uses this for its one-time download on first voice use.
     */
    suspend fun prepare() {
        // Default: the engine initializes itself at construction.
    }

    /**
     * Appends [text] to the speech queue. A blank addition is ignored — an empty reply has
     * nothing to say.
     */
    fun speak(text: String, queueAdd: Boolean = true)

    /** Stops speech now. Used when the user sends the next turn or stops a reply. */
    fun stop()

    /** Release the engine. Call from process teardown, never from a screen. */
    fun shutdown()
}
