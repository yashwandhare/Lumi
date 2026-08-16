package com.lumi.data.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.lumi.core.ai.EmbeddingModel
import com.lumi.core.ai.Embedder
import com.lumi.core.ai.EmbedderState
import com.lumi.core.ai.managed
import com.lumi.di.InferenceDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EmbeddingGemma 300m through MediaPipe's Text Embedder.
 *
 * **Why this model.** It is the best-quality embedder available without an access token, and it is
 * retrieval-tuned rather than general-purpose. `decisions.md` previously ruled EmbeddingGemma out as
 * gated; that was true of the Hugging Face repositories and false of Google's own MediaPipe CDN, which
 * serves the `.task` file openly. See the correction in `decisions.md`.
 *
 * **Why MediaPipe rather than ONNX Runtime.** MediaPipe tokenises internally, including for models
 * without in-graph tokenisation. The alternative — `all-MiniLM-L6-v2` through ONNX — would mean writing
 * a WordPiece tokeniser, mean pooling, and L2 normalisation by hand in Kotlin. A subtly wrong tokeniser
 * does not crash; it produces plausible embeddings that retrieve slightly badly, which is close to
 * undiagnosable. Not a risk worth taking for a 150MB saving.
 *
 * **Cost, stated plainly.** 180MB fetched separately from the generative model, and Google measures
 * 200ms per embed on an S26 Ultra — expect two to three times that on mid-range hardware. That is the
 * price of the quality, and it is why [Embedder] is an interface: Universal Sentence Encoder is 6MB and
 * 10ms, and swapping to it should be one new class.
 *
 * Runs on the same single-thread [InferenceDispatcher] as the generative model. Both are native
 * inference on the same device, and letting them contend for CPU would make each slower at the moment
 * the other is needed most — a RAG query embeds and then immediately generates.
 */
@Singleton
class MediaPipeEmbedder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: ModelStore,
    private val downloader: ModelDownloader,
    @param:InferenceDispatcher private val inference: CoroutineDispatcher,
) : Embedder {

    private val artefact = EmbeddingModel.managed

    private val _state = MutableStateFlow<EmbedderState>(EmbedderState.Absent)
    override val state: StateFlow<EmbedderState> = _state.asStateFlow()

    private val lock = Mutex()
    private var embedder: TextEmbedder? = null
    private var dimensions: Int? = null

    override fun dimensions(): Int? = dimensions

    override suspend fun prepare() {
        lock.withLock {
            if (embedder != null) {
                _state.value = EmbedderState.Ready
                return
            }

            if (!store.isReady(artefact)) {
                _state.value = EmbedderState.Downloading(
                    fraction = fractionOf(store.partialBytes(artefact)),
                    downloadedBytes = store.partialBytes(artefact),
                )
                when (val outcome = downloader.download(artefact) { downloaded, _, _ ->
                    _state.value = EmbedderState.Downloading(fractionOf(downloaded), downloaded)
                }) {
                    is DownloadOutcome.Success -> Unit
                    is DownloadOutcome.Retryable -> {
                        _state.value = EmbedderState.Unavailable(outcome.reason, recoverable = true)
                        return
                    }
                    is DownloadOutcome.Permanent -> {
                        _state.value = EmbedderState.Unavailable(outcome.reason, recoverable = false)
                        return
                    }
                }
            }

            _state.value = EmbedderState.Loading
            _state.value = load()
        }
    }

    private suspend fun load(): EmbedderState = withContext(inference) {
        try {
            val options = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(store.fileFor(artefact).absolutePath)
                        .build()
                )
                // Normalised at the source so every stored vector is unit length and cosine similarity
                // reduces to a dot product. Doing it here rather than per comparison means a chunk
                // embedded today and a query embedded next week are guaranteed comparable.
                .setL2Normalize(true)
                // Left off: quantised output would shrink the stored vectors but costs retrieval
                // precision, and at personal-notes scale the storage saving is irrelevant.
                .setQuantize(false)
                .build()

            val created = TextEmbedder.createFromOptions(context, options)

            // Probe the real width instead of trusting the constant. A mismatch would silently corrupt
            // every stored embedding — chunks indexed at one width cannot be compared against a query
            // at another, and it would surface as bad retrieval rather than as an error.
            val probe = created.embed(DIMENSION_PROBE).embeddingResult().embeddings().first()
            val width = probe.floatEmbedding().size
            if (width != EmbeddingModel.DIMENSIONS) {
                Log.w(TAG, "Embedder width is $width, expected ${EmbeddingModel.DIMENSIONS}")
            }

            embedder = created
            dimensions = width
            Log.i(TAG, "Embedder ready, $width dimensions")
            EmbedderState.Ready
        } catch (t: Throwable) {
            embedder = null
            dimensions = null
            Log.e(TAG, "Embedder failed to load", t)
            EmbedderState.Unavailable(
                reason = "Lumi could not start its search model.",
                recoverable = false,
            )
        }
    }

    /**
     * EmbeddingGemma is trained with task prefixes, and using the wrong one measurably degrades
     * retrieval. A note chunk is a document; a question about it is a query. They are encoded
     * differently on purpose.
     */
    override suspend fun embedDocument(text: String): FloatArray? =
        embed("$DOCUMENT_PREFIX$text")

    override suspend fun embedQuery(text: String): FloatArray? =
        embed("$QUERY_PREFIX$text")

    private suspend fun embed(prompt: String): FloatArray? {
        if (prompt.isBlank()) return null
        val ready = embedder ?: run { prepare(); embedder } ?: return null
        return withContext(inference) {
            try {
                lock.withLock {
                    ready.embed(prompt).embeddingResult().embeddings().first().floatEmbedding()
                }
            } catch (t: Throwable) {
                // Null rather than a throw: a failed embed should degrade retrieval, not take down the
                // caller. The router falls through to its next tier and RAG reports finding nothing.
                Log.w(TAG, "Embed failed", t)
                null
            }
        }
    }

    private fun fractionOf(downloaded: Long): Float =
        (downloaded.toDouble() / artefact.sizeBytes).toFloat().coerceIn(0f, 1f)

    private companion object {
        const val TAG = "LumiEmbedder"

        /** Any short string; only the returned vector's length is used. */
        const val DIMENSION_PROBE = "dimension probe"

        /**
         * EmbeddingGemma's documented templates. `search result` is the retrieval task type, which is
         * what both of this app's uses are — finding relevant notes, and finding the closest intent.
         */
        const val DOCUMENT_PREFIX = "title: none | text: "
        const val QUERY_PREFIX = "task: search result | query: "
    }
}
