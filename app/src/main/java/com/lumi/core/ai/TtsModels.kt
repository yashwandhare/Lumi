package com.lumi.core.ai

/**
 * The text-to-speech models, pinned the same way as [AsrModels] and [GemmaModel].
 *
 * **Kokoro-82M (int8) served by sherpa-onnx**, chosen 2026-08-17 after the owner judged the
 * platform engine's voice as nothing human-like or near. Kokoro is the best-sounding open TTS
 * in its size class — near-human rather than robotic — and it runs fully on-device through the
 * same sherpa-onnx AAR that already hosts recognition, so the swap adds no dependency and
 * sends no reply text anywhere. Cloud TTS was ruled out: it would put an on-device model's
 * replies on a server and break the privacy claim.
 *
 * The voice is `af_heart` — warm and expressive — selected as Lumi's voice; she is written as
 * female throughout. Speaker id 3 in the multi-lang bundle, of 53 available voices.
 *
 * Five files: the model, the voice styles, the token table, the English pronunciation lexicon,
 * and the espeak data used for phone synthesis. Four come from the sherpa-onnx mirror of the
 * Kokoro conversion; they are byte-identical to the official release bundle. The espeak data
 * ships as a small zip because its 355 files cannot each be pinned affordably — the archive is
 * pinned, extracted once, and the extracted tree verified against the archive's digest.
 *
 * Every size and SHA-256 below was read off the live artefact, and the four individual files
 * were additionally hashed and compared against the official release bundle to prove they are
 * the same bytes, which is what makes the digest check trustworthy rather than self-consistent.
 */
object TtsModels {

    /** Kokoro generates speech at a fixed 24kHz. */
    const val SAMPLE_RATE = 24000

    /**
     * Speaker id 3 in the 53-voice bundle is `af_heart`. Changing this selects a different
     * bundled voice; the ids are listed in the sherpa-onnx Kokoro documentation.
     */
    const val SPEAKER_ID = 3

    private const val BASE_URL =
        "https://huggingface.co/csukuangfj/kokoro-int8-multi-lang-v1_0/resolve/main/"

    val model = ManagedModel(
        fileName = "kokoro-int8.onnx",
        url = BASE_URL + "model.int8.onnx",
        sizeBytes = 114_298_054,
        sha256 = "77ef4f0513401d508ed7831f8504c7042df58bc75e004ec9666894590f999b1d",
        humanSize = "109 MB",
    )

    val voices = ManagedModel(
        fileName = "kokoro-voices.bin",
        url = BASE_URL + "voices.bin",
        sizeBytes = 27_678_720,
        sha256 = "8a77c0d397026208d22211f37670b5b3b11e03f190756b25a1d24041fced82a9",
        humanSize = "26 MB",
    )

    val lexicon = ManagedModel(
        fileName = "kokoro-lexicon-en.txt",
        url = BASE_URL + "lexicon-us-en.txt",
        sizeBytes = 5_956_885,
        sha256 = "7daaab53a181be9885b853a8582bf1838186317e5dadacbcef9c426d6fa0da14",
        humanSize = "6 MB",
    )

    val tokens = ManagedModel(
        fileName = "kokoro-tokens.txt",
        url = BASE_URL + "tokens.txt",
        sizeBytes = 687,
        sha256 = "6ebb6bb288f20f3ae8d004d3c2ca27697da27c037d75e81a60e2a6a663f95425",
        humanSize = "1 KB",
    )

    /**
     * The espeak-ng phoneme data Kokoro needs, as one archive from the sherpa-onnx release.
     * Verified byte-identical to the directory shipped inside the official model bundle.
     */
    val espeakData = ManagedModel(
        fileName = "espeak-ng-data.zip",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.zip",
        sizeBytes = 9_037_020,
        sha256 = "bc4525eafe31b4e3f5e43aea495f3169e97dd2544f1bbfe95514ce8a61baee39",
        humanSize = "9 MB",
    )

    /** Largest file first so the combined progress bar moves honestly from the start. */
    val all: List<ManagedModel> = listOf(model, voices, lexicon, tokens, espeakData)

    val totalBytes: Long = all.sumOf { it.sizeBytes }
}
