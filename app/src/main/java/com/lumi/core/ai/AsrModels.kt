package com.lumi.core.ai

/**
 * The speech-to-text models, pinned the same way as [GemmaModel] and [EmbeddingModel].
 *
 * **Whisper base.en (int8) served by sherpa-onnx**, chosen 2026-08-16 after live testing of the
 * 20M streaming zipformer showed it could not reliably recognise everyday sentences. The owner's
 * ruling: recognition quality is the app's front door and cannot be compromised. base.en is the
 * smallest Whisper that holds ordinary speech — ~160MB across three files, downloaded on first
 * voice use the same way as everything else here.
 *
 * The fourth file is **Silero VAD**, the voice-activity detector that splits mic audio into
 * utterances. It is what keeps latency low: each short utterance is decoded on its own the moment
 * a pause ends it, instead of waiting for one large batch.
 *
 * Every value below was read off the live artefact: the size and SHA-256 of each file were
 * measured from what the URL serves, which is what makes the digest check here trustworthy
 * rather than self-consistent.
 *
 * The runtime itself — the sherpa-onnx native libraries and Kotlin API — ships as a pinned AAR
 * fetched by `tools/fetch-sherpa.sh`; see `decisions_devb.md` for why it is not committed.
 */
object AsrModels {

    /** Whisper and the VAD both work on 16kHz mono. */
    const val SAMPLE_RATE = 16000

    const val FEATURE_DIM = 80

    private const val BASE_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base.en/resolve/main/"

    val decoder = ManagedModel(
        fileName = "base.en-decoder.int8.onnx",
        url = BASE_URL + "base.en-decoder.int8.onnx",
        sizeBytes = 130_669_978,
        sha256 = "f7162ad6db2dbef16cfaeaa7f945b9d7dd9c1b8d472f6aca82f2273d185e4d41",
        humanSize = "125 MB",
    )

    val encoder = ManagedModel(
        fileName = "base.en-encoder.int8.onnx",
        url = BASE_URL + "base.en-encoder.int8.onnx",
        sizeBytes = 29_120_534,
        sha256 = "ef6b936f4c9b1d90a3b68634b60c4ed8576b26172b33c2535ec0e933c9edb823",
        humanSize = "28 MB",
    )

    val tokens = ManagedModel(
        fileName = "base.en-tokens.txt",
        url = BASE_URL + "base.en-tokens.txt",
        sizeBytes = 835_554,
        sha256 = "306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930",
        humanSize = "1 MB",
    )

    val vad = ManagedModel(
        fileName = "silero_vad.onnx",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        sizeBytes = 643_854,
        sha256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
        humanSize = "1 MB",
    )

    /** Largest file first so the combined progress bar moves honestly from the start. */
    val all: List<ManagedModel> = listOf(decoder, encoder, tokens, vad)

    val totalBytes: Long = all.sumOf { it.sizeBytes }
}
