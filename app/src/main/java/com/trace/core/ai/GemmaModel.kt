package com.trace.core.ai

/**
 * The exact model Trace runs, pinned by name, size, and digest.
 *
 * Every value here was read off the live artefact on 2026-08-15 rather than copied from a document,
 * because two of them are load-bearing: [sizeBytes] is what tells an interrupted download from a
 * finished one, and [sha256] is what tells a finished download from a corrupted one.
 *
 * **Ungated and tokenless**, which `decisions.md` makes a hard requirement — a token in an APK is a
 * leaked credential, and asking a rural or low-literacy user to make a Hugging Face account makes
 * the app unusable for exactly the people it is for. The repository resolves `gated: false` and the
 * CDN redirect carries `user_id=public`. If a candidate URL ever demands an `Authorization` header,
 * it is the wrong URL.
 *
 * The GPU build is chosen over the 2.41GB generic one: it is 580MB smaller and it is the build that
 * matches `Backend.GPU()`. The vendor-specific builds in the same repository target Tensor G5,
 * Qualcomm, and Intel parts and are deliberately not used — the test device is a MediaTek mt6855,
 * and shipping a per-SoC matrix is a Phase 8 problem, not a hackathon one.
 */
object GemmaModel {

    const val FILE_NAME = "gemma-4-E2B-it-gpu.litertlm"

    const val URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$FILE_NAME"

    /** 1.87 GiB. Verified against `content-length` on the resolved artefact. */
    const val SIZE_BYTES = 2_008_432_640L

    /**
     * SHA-256 of the file contents, from the repository's own `x-linked-etag`.
     *
     * Checked once after a download completes, never on a subsequent launch — hashing 1.87GB costs
     * real seconds, so the result is recorded and the fast path is a size check plus that record.
     */
    const val SHA256 = "a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5"

    /** Shown during download, so the user can decide whether they want to spend the data. */
    const val HUMAN_SIZE = "1.9 GB"

    /**
     * Licence the user is agreeing to. Gemma ships under its own terms and its Prohibited Use
     * Policy, not Apache 2.0 — that applies to the weights regardless of how the app is licensed,
     * and it is a model-distribution obligation rather than a code one.
     */
    const val TERMS_URL = "https://ai.google.dev/gemma/terms"
}
