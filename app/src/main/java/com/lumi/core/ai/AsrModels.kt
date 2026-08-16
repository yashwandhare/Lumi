package com.lumi.core.ai

/**
 * The streaming speech-to-text model, pinned the same way as [GemmaModel] and [EmbeddingModel].
 *
 * **sherpa-onnx streaming zipformer EN, 20M parameters, int8** — chosen because Phase 2 voice is
 * short spoken commands, and this is the smallest streaming English model that is ungated and
 * tokenless: ~41MB across four files, small enough that "download on first voice use" is a
 * moment, not a commitment. The 60-100MB alternatives buy accuracy on long meetings, which is
 * Phase 6's problem, not Phase 2's.
 *
 * Every value below was read off the live artefact: the size and SHA-256 of each file were
 * measured from the Hugging Face repository at `resolve/main`, which is what makes the digest
 * check here trustworthy rather than self-consistent.
 *
 * The runtime itself — the sherpa-onnx native libraries and Kotlin API — ships as a pinned AAR
 * fetched by `tools/fetch-sherpa.sh`; see `decisions_devb.md` for why it is not committed.
 */
object AsrModels {

    /** Every sherpa streaming model here is trained for 16kHz mono input. */
    const val SAMPLE_RATE = 16000

    const val FEATURE_DIM = 80

    private const val BASE_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17/resolve/main/"

    val encoder = ManagedModel(
        fileName = "encoder-epoch-99-avg-1.int8.onnx",
        url = BASE_URL + "encoder-epoch-99-avg-1.int8.onnx",
        sizeBytes = 42_845_182,
        sha256 = "3810755ce7c3ab26b42a8bcf39d191308fa27fb0f53358823ba46141d03b7eb3",
        humanSize = "41 MB",
    )

    val decoder = ManagedModel(
        fileName = "decoder-epoch-99-avg-1.int8.onnx",
        url = BASE_URL + "decoder-epoch-99-avg-1.int8.onnx",
        sizeBytes = 539_499,
        sha256 = "21e2a2acd961b3ac72f55be2f10f1a285e1b0b0ba010d7c0b6eab141411b163c",
        humanSize = "0.5 MB",
    )

    val joiner = ManagedModel(
        fileName = "joiner-epoch-99-avg-1.int8.onnx",
        url = BASE_URL + "joiner-epoch-99-avg-1.int8.onnx",
        sizeBytes = 259_572,
        sha256 = "e085d73b593cf9b0707f370dbd656d58327d3fe36d80d849202ef81df02cb01e",
        humanSize = "0.3 MB",
    )

    val tokens = ManagedModel(
        fileName = "tokens.txt",
        url = BASE_URL + "tokens.txt",
        sizeBytes = 5_048,
        sha256 = "49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb",
        humanSize = "5 KB",
    )

    /** Downloaded in order; the UI reports one combined bar across all four. */
    val all: List<ManagedModel> = listOf(encoder, decoder, joiner, tokens)

    val totalBytes: Long = all.sumOf { it.sizeBytes }
}
