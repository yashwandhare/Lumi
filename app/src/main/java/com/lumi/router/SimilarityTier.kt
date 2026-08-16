package com.lumi.router

import com.lumi.core.RouterDecision
import com.lumi.core.RouterTier
import com.lumi.core.StructuredIntent
import com.lumi.core.ai.Embedder
import com.lumi.core.ai.EmbedderState
import com.lumi.core.ai.cosineSimilarity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Router tier 2: embedding similarity over the shared [Embedder].
 *
 * Compares the normalized utterance — embedded as a *query* — against the example phrases in
 * [IntentPhrases] — embedded as *documents*. The two calls are not interchangeable:
 * EmbeddingGemma is trained with task prefixes and using one for both measurably degrades the
 * score. Phrases are embedded once and cached; the utterance is embedded per request.
 *
 * The phrase lists are the router's training data. A misclassification is fixed by editing
 * [IntentPhrases], not by touching this file.
 *
 * **The embedder may be gone.** Model download failed, disk pressure, whatever — the tier must
 * fall through rather than crash. When it is, scoring degrades to deterministic token overlap
 * against the same phrase lists: weaker, but live, and honest about itself in the confidence it
 * reports. Tier 3 (Gemma) is deferred — below threshold the router asks or defaults to chat,
 * it does not escalate to a model it does not route through yet.
 */
internal class SimilarityTier(
    private val embedder: Embedder,
) {

    private val cacheLock = Mutex()

    /** phrase embedding by group; null entries are phrases that failed to embed. */
    @Volatile
    private var phraseEmbeddings: Map<PhraseGroup, List<FloatArray>>? = null

    /**
     * @return a routed decision, or null when nothing clears [ROUTE_THRESHOLD] strongly enough
     *   to act on. The caller decides between its own ambiguity question and the chat default.
     */
    suspend fun route(normalized: String, rawText: String): TierTwoAnswer? {
        if (normalized.isBlank()) return null

        val scores = scoreGroups(normalized) ?: return null
        val ranked = scores.entries.sortedByDescending { it.value }
        val best = ranked.first()
        val runnerUp = ranked.getOrNull(1)

        // A marker word in the utterance settles the "add" collisions the brief calls out:
        // "add milk to my list" scores close to reminder phrases and todo phrases alike, and
        // the word the user already said must not be asked back at them. The marker group wins
        // when it clears the action threshold *and* is within the ambiguity gap of the best
        // score — it resolves a near-tie in its favour. A marker cannot override a clearly
        // dominant, unrelated match, and a weak marker cannot drag anything over the line.
        val markerGroup = IntentPhrases.markers.entries
            .firstOrNull { it.value.containsMatchIn(normalized) }
            ?.key
        if (markerGroup != null) {
            val markerScore = scores[markerGroup] ?: 0f
            val bestScore = ranked.first().value
            if (markerScore >= ROUTE_THRESHOLD && bestScore - markerScore < AMBIGUITY_GAP) {
                return TierTwoAnswer(decisionFor(markerGroup, rawText, markerScore), null)
            }
        }

        if (best.value < ROUTE_THRESHOLD) return null

        // The runner-up only matters if it is itself actionable and close enough to be a real tie.
        // Narrowing it to a single nullable up front is what lets the branch below read it without a
        // second null check the compiler already knows is redundant.
        val tiedRunnerUp = runnerUp?.takeIf {
            it.value >= ROUTE_THRESHOLD && best.value - it.value < AMBIGUITY_GAP
        }
        return if (tiedRunnerUp != null) {
            TierTwoAnswer(null, listOf(best.key, tiedRunnerUp.key))
        } else {
            TierTwoAnswer(decisionFor(best.key, rawText, best.value), null)
        }
    }

    /** Group scores, or null when no scorer could run at all. */
    private suspend fun scoreGroups(normalized: String): Map<PhraseGroup, Float>? {
        val embedded = embeddedPhrases()
        return if (embedded != null && embedded.any { it.value.isNotEmpty() }) {
            val query = embedder.embedQuery(normalized) ?: return lexicalScores(normalized)
            embedded.mapValues { (_, vectors) ->
                vectors.maxOfOrNull { vector -> cosineSimilarity(query, vector) } ?: 0f
            }
        } else {
            lexicalScores(normalized)
        }
    }

    /**
     * Deterministic token-overlap fallback for when the embedder is down.
     *
     * Jaccard-ish between the utterance's tokens and each phrase's tokens, per group max. Scores
     * far below what real embeddings produce, which is exactly right: the router should not act
     * confidently on a degraded signal, and the confidence travels with the decision.
     */
    private fun lexicalScores(normalized: String): Map<PhraseGroup, Float> {
        val utteranceTokens = normalized.split(TOKEN_SPLIT).toSet()
        if (utteranceTokens.isEmpty()) return emptyMap()
        return IntentPhrases.byGroup.mapValues { (_, phrases) ->
            phrases.maxOf { phrase ->
                val phraseTokens = phrase.split(TOKEN_SPLIT).toSet()
                val overlap = utteranceTokens.intersect(phraseTokens).size
                val union = utteranceTokens.union(phraseTokens).size
                if (union == 0) 0f else overlap.toFloat() / union
            }
        }
    }

    /**
     * Document embeddings for every example phrase, computed once and cached for the process.
     *
     * Returns null when the embedder is not usable — its state is not ready, or every embed
     * call fails — which sends scoring to the lexical fallback.
     */
    private suspend fun embeddedPhrases(): Map<PhraseGroup, List<FloatArray>>? {
        phraseEmbeddings?.let { return it }
        cacheLock.withLock {
            phraseEmbeddings?.let { return it }

            if (embedder.state.value != EmbedderState.Ready) {
                embedder.prepare()
            }
            if (embedder.state.value != EmbedderState.Ready) return null

            val embeddings = IntentPhrases.byGroup.mapValues { (_, phrases) ->
                phrases.mapNotNull { phrase -> embedder.embedDocument(phrase) }
            }
            if (embeddings.values.flatten().isEmpty()) return null

            phraseEmbeddings = embeddings
            return embeddings
        }
    }

    private fun decisionFor(group: PhraseGroup, rawText: String, confidence: Float): RouterDecision {
        val slots = buildMap<String, String> {
            put(StructuredIntent.SLOT_QUERY, rawText)
            group.slotKind?.let { kind -> put(SLOT_KIND, kind) }
        }
        return RouterDecision(
            intent = StructuredIntent(
                capability = group.capability,
                rawText = rawText,
                slots = slots,
            ),
            confidence = confidence,
            tier = RouterTier.SIMILARITY,
        )
    }

    private companion object {
        const val SLOT_KIND = "kind"

        /**
         * Minimum cosine to act on. Written down because tuning happens by measuring real
         * phrasings, not by guessing: raise it and misrouting drops but valid requests fall
         * through to chat; lower it and the opposite. 0.38 sits where same-style sentence
         * pairs separated cleanly from unrelated pairs on the reference phrasings. The
         * lexical fallback reports Jaccard overlap against the same bar, which keeps the
         * degraded mode honest — it acts only on near-exact wording.
         */
        const val ROUTE_THRESHOLD = 0.38f

        /**
         * Top-two groups closer than this ask instead of acting. Tight on purpose: an
         * ambiguity question costs one extra turn, a wrong action costs trust, and asking is
         * the product's designed behaviour — see RouterOutcome.Ambiguous.
         */
        const val AMBIGUITY_GAP = 0.05f

        val TOKEN_SPLIT = Regex("[\\s\\p{Punct}]+")
    }
}

internal class TierTwoAnswer(
    val decision: RouterDecision?,
    val ambiguousWith: List<PhraseGroup>?,
)
