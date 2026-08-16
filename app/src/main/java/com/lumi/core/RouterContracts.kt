package com.lumi.core

import com.lumi.core.model.CapabilityId

/**
 * A request after the router has understood it.
 *
 * [slots] holds whatever the chosen capability needs — a filename, a query, a device
 * toggle and its value. Deliberately a string map rather than a sealed hierarchy per
 * capability: the router is edited constantly during a short build, and a map lets a new
 * slot be added without a schema change rippling through every branch.
 *
 * Routines are the exception. Their structure is persisted as real rows, because a routine
 * outlives the sentence that created it and must never be re-parsed at fire time.
 */
data class StructuredIntent(
    val capability: CapabilityId,
    val rawText: String,
    val slots: Map<String, String> = emptyMap(),
) {
    operator fun get(slot: String): String? = slots[slot]

    companion object {
        const val SLOT_QUERY = "query"
        const val SLOT_TARGET = "target"
        const val SLOT_VALUE = "value"

        /**
         * The conversation a chat turn belongs to, when the dispatcher routes through a
         * session-aware path. Carries as a slot because the capability set is shared across
         * conversations and nothing besides chat needs it.
         */
        const val SLOT_SESSION = "session"
    }
}

/**
 * Which stage of the router produced a decision.
 *
 * Recorded so the router can be tuned with evidence instead of guesses: if most traffic is
 * escalating to [MODEL], the cheaper stages need better coverage.
 */
enum class RouterTier {
    /** Regex and keywords. Zero latency, no model. Exact device commands. */
    RULES,

    /** Cosine similarity against labelled example phrases, using the bundled embedder. */
    SIMILARITY,

    /** Gemma. Reasoning, generation, and anything the cheaper tiers cannot settle. */
    MODEL,
}

data class RouterDecision(
    val intent: StructuredIntent,
    val confidence: Float,
    val tier: RouterTier,
)

/**
 * What the router concluded.
 *
 * [Ambiguous] is not a failure. Asking a short question beats confidently running the
 * wrong action — and for a product that can silence a phone or send an emergency message,
 * guessing is the worse behaviour.
 */
sealed interface RouterOutcome {

    data class Routed(val decision: RouterDecision) : RouterOutcome

    data class Ambiguous(
        val question: String,
        val candidates: List<CapabilityId>,
    ) : RouterOutcome
}

/**
 * Turns text into an intent.
 *
 * One router serves chat, routines, files, device actions, web search, and mail, so the app
 * cannot grow two incompatible command systems.
 */
interface Router {

    suspend fun route(text: String, origin: InteractionOrigin): RouterOutcome
}

/**
 * Sends a routed intent to the capability that owns it.
 *
 * Keeps a registry of [Capability] instances keyed by [CapabilityId], so adding a
 * capability does not mean editing a dispatch `when`.
 */
interface Dispatcher {

    suspend fun dispatch(input: CapabilityInput): CapabilityResult
}
