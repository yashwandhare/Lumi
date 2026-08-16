package com.lumi.core.ai

/**
 * The streaming speech-to-text model, pinned the same way as [GemmaModel] and [EmbeddingModel].
 *
 * **sherpa-onnx streaming zipformer EN, 2023-06-26, chunk-16-left-128.** Roughly 73MB across four
 * files.
 *
 * **This replaced a 20M-parameter model on the owner's ruling of Aug 16: optimise for working, not
 * for download size.** The 20M model was chosen when Phase 2 voice was assumed to be short commands,
 * and on the device it was not usable — "hello" came back as "O", and ambient room noise came back as
 * words. That is not a bug in the session loop, it is the accuracy ceiling of a 20M model, so no
 * amount of fixing the audio path recovers it. This model is the full English streaming zipformer and
 * is what the sherpa project recommends for English.
 *
 * **int8 encoder and joiner, fp32 decoder** — sherpa's own recommended combination. The encoder
 * dominates both size and compute, so quantising it is what keeps streaming real-time on a mid-range
 * phone; the decoder is 2MB and quantising it costs accuracy for nothing. The fp32 encoder exists at
 * 262MB and would be more accurate still, but it has to keep up with live speech on an Exynos 1380 —
 * revisit only with a measured real-time factor, not on the assumption that bigger is better.
 *
 * `left-128` rather than `left-64`: more left context, better accuracy on connected speech. If the
 * transcript is measured lagging behind the speaker on the demo device, `left-64` is the same four
 * files with `-left-64` in their names and is the first thing to try.
 *
 * Every value below was read off the live artefact — sizes and SHA-256 from the Hugging Face LFS
 * metadata, with `tokens.txt` hashed directly because it is stored inline rather than in LFS. That is
 * what makes the digest check trustworthy rather than self-consistent. `tokens.txt` is byte-identical
 * to the 20M model's: the vocabulary did not change, only the acoustic model.
 *
 * The runtime itself — the sherpa-onnx native libraries and Kotlin API — ships as a pinned AAR
 * fetched by `tools/fetch-sherpa.sh`; see `decisions_devb.md` for why it is not committed.
 */
object AsrModels {

    /** Every sherpa streaming model here is trained for 16kHz mono input. */
    const val SAMPLE_RATE = 16000

    const val FEATURE_DIM = 80

    private const val BASE_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/main/"

    val encoder = ManagedModel(
        fileName = "encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
        url = BASE_URL + "encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
        sizeBytes = 71_083_163,
        sha256 = "563fde436d16cf7607cf408cd6b30909819d03162652ef389c2450ced3f45ac1",
        humanSize = "68 MB",
    )

    val decoder = ManagedModel(
        fileName = "decoder-epoch-99-avg-1-chunk-16-left-128.onnx",
        url = BASE_URL + "decoder-epoch-99-avg-1-chunk-16-left-128.onnx",
        sizeBytes = 2_092_621,
        sha256 = "7bf787f90b194b307e5a4ad6a34fadb4e748304c35f78a8d66358a05b13ee6ef",
        humanSize = "2 MB",
    )

    val joiner = ManagedModel(
        fileName = "joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
        url = BASE_URL + "joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
        sizeBytes = 259_335,
        sha256 = "d944208d660d67c8d72cd2acaeac971fa5ceb8c80e76c1968148846fedd6e297",
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
