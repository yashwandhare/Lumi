package com.lumi.core.settings

/**
 * Which accelerator the model should load on.
 *
 * [AUTO] tries GPU and falls back to CPU. [CPU] is the default rather than [AUTO], because on the
 * first test device the GPU backend failed to load the model outright and a silent multi-second
 * failure on every launch is worse than starting slower. Users whose GPU is stronger can and should
 * change this — hence the setting existing at all.
 */
enum class ModelBackend(val label: String, val detail: String) {
    CPU(
        label = "CPU",
        detail = "Slower, works everywhere. The safe default.",
    ),
    GPU(
        label = "GPU",
        detail = "Much faster where it works. Fails to load on some devices.",
    ),
    AUTO(
        label = "Auto",
        detail = "Try the GPU, fall back to the CPU if it will not load.",
    ),
}

/**
 * Everything the user can change about how the model behaves.
 *
 * Defaults come from Trace v1's shipped model allowlist, which had them tuned against this same Gemma
 * build — topK 64, topP 0.95, temperature 1.0, 4096 tokens — rather than being invented here.
 *
 * [maxTokens] is the per-reply ceiling, not the engine's context window. The engine is configured
 * separately and is the larger of the two.
 */
data class ModelSettings(
    val backend: ModelBackend = ModelBackend.CPU,
    val topK: Int = DEFAULT_TOP_K,
    val topP: Float = DEFAULT_TOP_P,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val systemPrompt: String = LumiPersona.CHAT,
    /** Whether replies show how long they took and how fast they decoded. */
    val showMetrics: Boolean = true,
    /**
     * Whether the mascot docks beside the menu button during a conversation and reacts to what is
     * happening. Off means it stays put and stays quiet — some people find a moving companion in the
     * corner of a screen they are reading distracting, and that is a preference, not a bug.
     */
    val liveMascot: Boolean = true,
    /**
     * Whether Lumi may draw on the user's own documents and fetched mail when answering.
     *
     * On by default, and deliberately not a [NetworkSettings] feature: retrieval over the user's own
     * content happens entirely on-device, so it reaches nothing and needs no network opt-in. It is
     * consent for Lumi to *read what is already here*, which is what makes asking for a document by
     * description work at all — off, such a question can only be answered from the model's weights.
     */
    val personalContext: Boolean = true,
) {
    companion object {
        const val DEFAULT_TOP_K = 64
        const val DEFAULT_TOP_P = 0.95f
        const val DEFAULT_TEMPERATURE = 1.0f
        const val DEFAULT_MAX_TOKENS = 4096

        /** Ranges the settings UI clamps to. Outside these the model's output stops being useful. */
        val TOP_K_RANGE = 1..128
        val TOP_P_RANGE = 0.1f..1.0f
        val TEMPERATURE_RANGE = 0.0f..2.0f
        val MAX_TOKENS_RANGE = 128..4096
    }
}
