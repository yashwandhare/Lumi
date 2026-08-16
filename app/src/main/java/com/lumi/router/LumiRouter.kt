package com.lumi.router

import com.lumi.core.InteractionOrigin
import com.lumi.core.Router
import com.lumi.core.RouterDecision
import com.lumi.core.RouterOutcome
import com.lumi.core.RouterTier
import com.lumi.core.StructuredIntent
import com.lumi.core.ai.Embedder
import com.lumi.core.model.CapabilityId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one router: rules first, then embedding similarity, then chat as the resting place.
 *
 * Tier order is cheapest-first by design. A device command decided by a regex never pays for an
 * embedding; an embedding match never pays for a model call; and tier 3 — Gemma settling genuine
 * ambiguity — is deliberately unwired this phase: tiers 1 and 2 cover the demo, and the cost of a
 * router inference through the resident model on every fuzzy line is a price worth not paying until
 * there is evidence the cheaper tiers are not enough. When tier 2 cannot settle, the router asks
 * or falls back to chat — it does not guess an action.
 *
 * **The ambiguity path is a feature, not a failure mode.** Two close intents return
 * [RouterOutcome.Ambiguous] and let the UI ask; a product that can silence a phone answers a
 * question before it acts.
 */
@Singleton
class LumiRouter @Inject constructor(
    embedder: Embedder,
) : Router {

    private val similarity = SimilarityTier(embedder)

    override suspend fun route(text: String, origin: InteractionOrigin): RouterOutcome {
        val rawText = text.trim()
        if (rawText.isEmpty()) return chatDecision(rawText).toOutcome()

        val normalized = InputNormalizer.normalize(rawText)

        RulesTier.route(normalized, rawText, origin)?.let { ruleDecision ->
            return RouterOutcome.Routed(ruleDecision)
        }

        return when (val answer = similarity.route(normalized, rawText)) {
            null -> chatDecision(rawText).toOutcome()
            is TierTwoAnswer -> when {
                answer.decision != null -> RouterOutcome.Routed(answer.decision)
                answer.ambiguousWith != null -> ambiguousOutcome(answer.ambiguousWith)
                else -> chatDecision(rawText).toOutcome()
            }
        }
    }

    private fun chatDecision(rawText: String): RouterDecision =
        RouterDecision(
            intent = StructuredIntent(
                capability = CapabilityId.CHAT,
                rawText = rawText,
                slots = mapOf(StructuredIntent.SLOT_QUERY to rawText),
            ),
            confidence = CHAT_FALLBACK_CONFIDENCE,
            tier = RouterTier.SIMILARITY,
        )

    /**
     * The question is built from the two groups' labels, not from capability ids — the user
     * hears "a reminder or your to-do list", not "TOOLS or TOOLS".
     */
    private fun ambiguousOutcome(groups: List<PhraseGroup>): RouterOutcome.Ambiguous {
        val labels = groups.map { it.ambiguityLabel }.distinct()
        val question = when (labels.size) {
            2 -> "Do you want ${labels[0]} or ${labels[1]}?"
            else -> "Which of these do you mean: ${labels.joinToString(", ")}?"
        }
        return RouterOutcome.Ambiguous(
            question = question,
            candidates = groups.map { it.capability }.distinct(),
        )
    }

    private fun RouterDecision.toOutcome(): RouterOutcome = RouterOutcome.Routed(this)

    private companion object {
        /**
         * Deliberately low: chat is the resting place, not a confident classification, and the
         * number travels with the intent for whoever tunes routing later.
         */
        const val CHAT_FALLBACK_CONFIDENCE = 0.30f
    }
}

private val PhraseGroup.ambiguityLabel: String
    get() = when (this) {
        PhraseGroup.REMINDER -> "a reminder"
        PhraseGroup.TODO -> "your to-do list"
        PhraseGroup.ROUTINE -> "a routine"
        PhraseGroup.RAG -> "an answer from your notes"
        PhraseGroup.FILES -> "a file"
        PhraseGroup.SEARCH -> "a web search"
        PhraseGroup.MAIL -> "your mail"
        PhraseGroup.CHAT -> "a chat"
    }
