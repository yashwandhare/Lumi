package com.lumi.core

import com.lumi.core.model.CapabilityId
import kotlinx.coroutines.flow.Flow

/**
 * Where a request came from.
 *
 * Carried end to end because behaviour genuinely differs: text-to-speech must speak only
 * turns the user spoke, and a routine firing in the background must never try to show a
 * dialog. v1 lost track of this and read every reply aloud.
 */
enum class InteractionOrigin {
    TEXT,
    VOICE,
    WIDGET,
    QUICK_INVOKE,
    ROUTINE,
}

/**
 * What the dispatcher hands a capability.
 *
 * Normalized: by this point it no longer matters whether the user typed, spoke, or tapped
 * a widget. Every input source converges here.
 */
data class CapabilityInput(
    val intent: StructuredIntent,
    val origin: InteractionOrigin,
)

/**
 * What a capability gives back.
 *
 * The variants exist to satisfy a specific product requirement: errors must say what
 * happened, what the user can do, and whether the action partly completed. A single
 * boolean success flag cannot express "the siren is sounding but the message did not
 * send", which is exactly the case that matters most.
 *
 * [userMessage] is shown and possibly spoken. Write it in plain language. Never put an
 * exception message, a class name, or an Android internal in it.
 */
sealed interface CapabilityResult {

    val userMessage: String

    data class Ok(override val userMessage: String) : CapabilityResult

    /** Response arrives incrementally. Used by chat and grounded generation. */
    data class Streaming(
        override val userMessage: String,
        val chunks: Flow<String>,
    ) : CapabilityResult

    /**
     * Some of it worked. [whatFailed] names the part that did not, in the user's language.
     */
    data class Partial(
        override val userMessage: String,
        val whatFailed: String,
    ) : CapabilityResult

    /** [recovery] tells the user what to do next, when there is something they can do. */
    data class Failed(
        override val userMessage: String,
        val recovery: String? = null,
    ) : CapabilityResult

    /**
     * The action has real-world consequence and needs an explicit yes.
     *
     * Required before anything irreversible or externally visible. A suggestion raised from a
     * notification always needs one, because the user did not ask for it — see decisions.md.
     */
    data class NeedsConfirmation(
        override val userMessage: String,
        val confirmLabel: String,
    ) : CapabilityResult

    /**
     * Blocked on a permission. [permission] is the Android manifest constant so the UI can
     * request exactly that grant instead of sending the user to app settings to guess.
     */
    data class NeedsPermission(
        override val userMessage: String,
        val permission: String,
    ) : CapabilityResult
}

/**
 * One thing Lumi can do.
 *
 * Every capability is independently testable: give it a [CapabilityInput], assert on the
 * [CapabilityResult]. No Android framework types cross this boundary, so the tests are
 * plain JVM tests.
 *
 * Implementations are responsible for writing their own audit entry. The dispatcher cannot
 * do it for them because only the capability knows what actually happened.
 */
interface Capability {

    val id: CapabilityId

    suspend fun execute(input: CapabilityInput): CapabilityResult
}
