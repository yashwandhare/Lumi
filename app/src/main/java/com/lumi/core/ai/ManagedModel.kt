package com.lumi.core.ai

/**
 * An artefact Lumi fetches once and keeps: a model file pinned by size and digest.
 *
 * Introduced because there are now two of them — the generative model and the embedder — and the store
 * and downloader were written around a single hardcoded one. Parameterising is a smaller change than a
 * second parallel path, and it means the no-redownload guarantee, the digest verification, and the
 * resumable fetch apply to both without being written twice.
 */
data class ManagedModel(
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    /** Shown to the user when asking whether to spend the data. */
    val humanSize: String,
)

/** The generative model. */
val GemmaModel.managed: ManagedModel
    get() = ManagedModel(
        fileName = FILE_NAME,
        url = URL,
        sizeBytes = SIZE_BYTES,
        sha256 = SHA256,
        humanSize = HUMAN_SIZE,
    )

/** The embedder, which serves both router tier 2 and RAG retrieval. */
val EmbeddingModel.managed: ManagedModel
    get() = ManagedModel(
        fileName = FILE_NAME,
        url = URL,
        sizeBytes = SIZE_BYTES,
        sha256 = SHA256,
        humanSize = HUMAN_SIZE,
    )
