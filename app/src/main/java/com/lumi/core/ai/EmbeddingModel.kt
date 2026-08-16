package com.lumi.core.ai

/**
 * The embedding model, pinned by name, size, and digest — the same discipline as [GemmaModel].
 *
 * **EmbeddingGemma 300m, from Google's MediaPipe CDN rather than Hugging Face.** That distinction is the
 * whole reason this model is usable at all: the Hugging Face repositories for EmbeddingGemma are gated
 * behind accepting terms and an access token, which `decisions.md` forbids. Google's own copy of the
 * `.task` file is served openly — verified 200 with no `Authorization` header, and 206 on a range
 * request, so it resumes like the main model does.
 *
 * Every value below was read off the live artefact. The MD5 recorded in the CDN's own `x-goog-hash`
 * header matched the downloaded bytes, which is what makes the SHA-256 here trustworthy rather than
 * merely self-consistent.
 */
object EmbeddingModel {

    const val FILE_NAME = "embedding_gemma.task"

    const val URL =
        "https://storage.googleapis.com/mediapipe-models/text_embedder/embedding_gemma/" +
            "int4int8/latest/$FILE_NAME"

    /** 175 MiB. Mixed int4+int8 quantisation, 512-token maximum sequence. */
    const val SIZE_BYTES = 183_816_181L

    const val SHA256 = "913b7a1edc7c7c3d1da3979ec1d0648ed9e0a370f181bb59ab177ca4b97707ad"

    const val HUMAN_SIZE = "180 MB"

    /**
     * Vector length. Fixed by the model, asserted at load rather than assumed, because a mismatch here
     * would silently corrupt every stored embedding — chunks indexed at one width cannot be compared
     * against a query at another, and the failure looks like bad retrieval rather than a bug.
     */
    const val DIMENSIONS = 768
}
