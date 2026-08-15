package com.lumi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.core.ai.ModelHarness
import com.lumi.core.ai.ModelState
import com.lumi.core.settings.ModelBackend
import com.lumi.core.settings.ModelSettings
import com.lumi.data.ai.ModelStore
import com.lumi.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val harness: ModelHarness,
    private val store: ModelStore,
) : ViewModel() {

    val model: StateFlow<ModelSettings> = settings.model
    val modelState: StateFlow<ModelState> = harness.state

    /**
     * What the model actually occupies, weights plus the caches the runtime generates.
     *
     * Worth showing because it is not the download size: the compilation caches add roughly a third
     * again on first load, and a user auditing why the app holds 3.4GB deserves the real number rather
     * than the 2.6GB they agreed to.
     */
    fun modelBytesOnDisk(): Long = store.bytesOnDisk()

    /**
     * True while the engine is being swapped after a backend change.
     *
     * Separate from [ModelState.Loading] because the screen needs to explain *why* it is loading — a
     * spinner that appears when you touch a dropdown is alarming unless it is attributed.
     */
    private val _switchingBackend = MutableStateFlow(false)
    val switchingBackend: StateFlow<Boolean> = _switchingBackend.asStateFlow()

    /** Which backend the engine actually ended up on, which Auto may have decided for the user. */
    fun activeBackend(): ModelBackend? = harness.activeBackend()

    /**
     * Changing the backend requires reloading the model, so it is applied immediately and the reload
     * reported — a setting that silently takes effect on next launch is a setting users think is broken.
     */
    fun setBackend(backend: ModelBackend) {
        if (backend == settings.model.value.backend) return
        settings.setBackend(backend)
        viewModelScope.launch {
            _switchingBackend.value = true
            try {
                harness.reload()
            } finally {
                _switchingBackend.value = false
            }
        }
    }

    /**
     * Sampling changes apply to the **next** conversation, not this one.
     *
     * The runtime fixes sampling when a `Conversation` is created, so the open chat keeps the values it
     * started with. Rebuilding the session on every slider drag would throw away the user's chat
     * history mid-conversation, which is a far worse surprise than a setting that takes effect on the
     * next one. The screen says so rather than pretending otherwise.
     */
    fun setTopK(value: Int) = settings.setTopK(value)
    fun setTopP(value: Float) = settings.setTopP(value)
    fun setTemperature(value: Float) = settings.setTemperature(value)
    fun setMaxTokens(value: Int) = settings.setMaxTokens(value)
    fun setSystemPrompt(value: String) = settings.setSystemPrompt(value)
    fun setShowMetrics(value: Boolean) = settings.setShowMetrics(value)
    fun setLiveMascot(value: Boolean) = settings.setLiveMascot(value)
    fun resetDefaults() = settings.resetModelDefaults()
}
