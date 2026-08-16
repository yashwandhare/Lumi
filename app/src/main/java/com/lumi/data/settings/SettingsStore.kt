package com.lumi.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.lumi.core.network.NetworkFeature
import com.lumi.core.network.NetworkToggles
import com.lumi.core.settings.LumiPersona
import com.lumi.core.settings.ModelBackend
import com.lumi.core.settings.ModelSettings
import com.lumi.core.settings.NetworkSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one owner of user preferences.
 *
 * `SharedPreferences` rather than Room or DataStore. `decisions.md` puts entities in Room, and these
 * are not entities — they are a dozen scalars read at startup and written when a slider moves. Room
 * would mean a table with one row; DataStore would mean a new dependency for the same result.
 * `MainActivity` was already using `SharedPreferences` for the theme, so this absorbs that rather than
 * adding a second mechanism beside it.
 *
 * Reads are synchronous and off a single in-memory snapshot, because the model needs its parameters at
 * load time and a suspending read there would mean either blocking or a race.
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) : NetworkToggles {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _model = MutableStateFlow(readModel())
    val model: StateFlow<ModelSettings> = _model.asStateFlow()

    /**
     * Null means "follow the system", which is what a first launch should do. Stored as an int
     * because `SharedPreferences` has no nullable boolean.
     */
    private val _darkTheme = MutableStateFlow(readTheme())
    val darkTheme: StateFlow<Boolean?> = _darkTheme.asStateFlow()

    private val _network = MutableStateFlow(readNetwork())
    val network: StateFlow<NetworkSettings> = _network.asStateFlow()

    /** The gate's read path. The gate decides from this at request time, never from a cache. */
    override fun isEnabled(feature: NetworkFeature): Boolean = when (feature) {
        NetworkFeature.WEB_SEARCH -> _network.value.webSearch
        NetworkFeature.GMAIL -> _network.value.gmail
    }

    fun setWebSearch(enabled: Boolean) = updateNetwork { copy(webSearch = enabled) }

    fun setGmail(enabled: Boolean) = updateNetwork { copy(gmail = enabled) }

    /** The one kill switch the settings panel exposes: off means nothing reaches the network. */
    fun setAllNetworkFeatures(enabled: Boolean) =
        updateNetwork { copy(webSearch = enabled, gmail = enabled) }

    fun setBackend(backend: ModelBackend) = update { copy(backend = backend) }

    fun setTopK(topK: Int) =
        update { copy(topK = topK.coerceIn(ModelSettings.TOP_K_RANGE)) }

    fun setTopP(topP: Float) = update {
        copy(topP = topP.coerceIn(ModelSettings.TOP_P_RANGE.start, ModelSettings.TOP_P_RANGE.endInclusive))
    }

    fun setTemperature(temperature: Float) = update {
        copy(
            temperature = temperature.coerceIn(
                ModelSettings.TEMPERATURE_RANGE.start,
                ModelSettings.TEMPERATURE_RANGE.endInclusive,
            ),
        )
    }

    fun setMaxTokens(maxTokens: Int) =
        update { copy(maxTokens = maxTokens.coerceIn(ModelSettings.MAX_TOKENS_RANGE)) }

    /** Blank restores the shipped persona rather than leaving the model with no instruction at all. */
    fun setSystemPrompt(prompt: String) = update {
        copy(systemPrompt = prompt.ifBlank { LumiPersona.CHAT })
    }

    fun setShowMetrics(show: Boolean) = update { copy(showMetrics = show) }

    fun setLiveMascot(enabled: Boolean) = update { copy(liveMascot = enabled) }

    fun resetModelDefaults() {
        val backend = _model.value.backend
        // The backend is a device capability rather than a tuning choice, so a "reset parameters"
        // action leaves it alone — resetting it could put a working GPU device back on the CPU.
        write(ModelSettings(backend = backend))
    }

    fun setDarkTheme(dark: Boolean) {
        prefs.edit().putInt(KEY_THEME, if (dark) 1 else 0).apply()
        _darkTheme.value = dark
    }

    private inline fun update(transform: ModelSettings.() -> ModelSettings) {
        write(_model.value.transform())
    }

    private inline fun updateNetwork(transform: NetworkSettings.() -> NetworkSettings) {
        val settings = _network.value.transform()
        prefs.edit()
            .putBoolean(KEY_WEB_SEARCH, settings.webSearch)
            .putBoolean(KEY_GMAIL, settings.gmail)
            .apply()
        _network.value = settings
    }

    private fun write(settings: ModelSettings) {
        prefs.edit()
            .putString(KEY_BACKEND, settings.backend.name)
            .putInt(KEY_TOP_K, settings.topK)
            .putFloat(KEY_TOP_P, settings.topP)
            .putFloat(KEY_TEMPERATURE, settings.temperature)
            .putInt(KEY_MAX_TOKENS, settings.maxTokens)
            .putString(KEY_SYSTEM_PROMPT, settings.systemPrompt)
            .putBoolean(KEY_SHOW_METRICS, settings.showMetrics)
            .putBoolean(KEY_LIVE_MASCOT, settings.liveMascot)
            .apply()
        _model.value = settings
    }

    private fun readModel(): ModelSettings {
        val defaults = ModelSettings()
        return ModelSettings(
            backend = prefs.getString(KEY_BACKEND, null)
                ?.let { name -> ModelBackend.entries.firstOrNull { it.name == name } }
                ?: defaults.backend,
            topK = prefs.getInt(KEY_TOP_K, defaults.topK),
            topP = prefs.getFloat(KEY_TOP_P, defaults.topP),
            temperature = prefs.getFloat(KEY_TEMPERATURE, defaults.temperature),
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, defaults.maxTokens),
            systemPrompt = prefs.getString(KEY_SYSTEM_PROMPT, null) ?: defaults.systemPrompt,
            showMetrics = prefs.getBoolean(KEY_SHOW_METRICS, defaults.showMetrics),
            liveMascot = prefs.getBoolean(KEY_LIVE_MASCOT, defaults.liveMascot),
        )
    }

    private fun readTheme(): Boolean? =
        prefs.getInt(KEY_THEME, THEME_FOLLOW_SYSTEM).takeIf { it != THEME_FOLLOW_SYSTEM }?.let { it == 1 }

    private fun readNetwork(): NetworkSettings =
        NetworkSettings(
            webSearch = prefs.getBoolean(KEY_WEB_SEARCH, false),
            gmail = prefs.getBoolean(KEY_GMAIL, false),
        )

    private companion object {
        /** The name MainActivity already used, so an existing theme choice is not lost. */
        const val NAME = "lumi_settings"

        const val KEY_THEME = "theme_mode"
        const val KEY_BACKEND = "model_backend"
        const val KEY_TOP_K = "model_top_k"
        const val KEY_TOP_P = "model_top_p"
        const val KEY_TEMPERATURE = "model_temperature"
        const val KEY_MAX_TOKENS = "model_max_tokens"
        const val KEY_SYSTEM_PROMPT = "model_system_prompt"
        const val KEY_SHOW_METRICS = "show_metrics"
        const val KEY_LIVE_MASCOT = "live_mascot"
        const val KEY_WEB_SEARCH = "network_web_search"
        const val KEY_GMAIL = "network_gmail"

        const val THEME_FOLLOW_SYSTEM = -1
    }
}
