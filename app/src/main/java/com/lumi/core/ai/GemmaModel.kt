package com.lumi.core.ai

/**
 * The exact model Lumi runs, pinned by name, size, and digest.
 *
 * Every value here was read off the live artefact rather than copied from a document, because two of
 * them are load-bearing: [SIZE_BYTES] is what tells an interrupted download from a finished one, and
 * [SHA256] is what tells a finished download from a corrupted one.
 *
 * **Ungated and tokenless**, which `decisions.md` makes a hard requirement — a token in an APK is a
 * leaked credential, and asking a rural or low-literacy user to make a Hugging Face account makes the
 * app unusable for exactly the people it is for. The repository resolves `gated: false` and the CDN
 * redirect carries `user_id=public`. If a candidate URL ever demands an `Authorization` header, it is
 * the wrong URL.
 *
 * **This is the generic build, not the `-gpu` one.** The `-gpu` file is 580MB smaller and looks like
 * the obvious choice to pair with `Backend.GPU()`, but it does not contain a `TF_LITE_PREFILL_DECODE`
 * section, so `Engine.initialize()` fails with `NOT_FOUND` on every backend and the app cannot start.
 * The generic build serves both GPU and CPU — the model card's own Android table quotes the same
 * 2583MB file size for both backend rows. See `decisions.md`.
 *
 * The vendor-specific builds in the same repository target Tensor G5, Qualcomm, and Intel parts and
 * are deliberately not used: shipping a per-SoC matrix is a Phase 8 problem, not a hackathon one.
 */
object GemmaModel {

    const val FILE_NAME = "gemma-4-E2B-it.litertlm"

    const val URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$FILE_NAME"

    /** 2.59 GB. Verified against `x-linked-size` on the resolved artefact. */
    const val SIZE_BYTES = 2_588_147_712L

    /**
     * SHA-256 of the file contents, from the repository's own `x-linked-etag`.
     *
     * Checked once after a download completes, never on a subsequent launch — hashing 2.6GB costs
     * real seconds, so the result is recorded and the fast path is a size check plus that record.
     */
    const val SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"

    /**
     * Shown during download, so the user can decide whether they want to spend the data.
     *
     * Decimal GB, matching how the progress readout formats bytes and how connections are sold. This
     * said 2.4 GB before, which was the same file measured in binary GiB — so the download counted up
     * past its own stated total and looked broken right at the finish line. Every number on that
     * screen has to be in one unit system.
     */
    const val HUMAN_SIZE = "2.6 GB"

    /**
     * Licence the user is agreeing to. Gemma ships under its own terms and its Prohibited Use
     * Policy, not Apache 2.0 — that applies to the weights regardless of how the app is licensed,
     * and it is a model-distribution obligation rather than a code one.
     */
    const val TERMS_URL = "https://ai.google.dev/gemma/terms"
}
