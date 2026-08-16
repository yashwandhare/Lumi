package com.lumi.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The two consents the attachment sheet offers, and nothing else.
 *
 * Its own view model rather than a pair of parameters threaded down from `HomeScreen`: both switches
 * are persisted preferences the sheet reads and writes directly, and hoisting them into the chat view
 * model would put storage settings on the object that runs the chat loop.
 *
 * Initial values come from the store's current snapshot rather than from a literal, so a switch never
 * renders in the wrong position for a frame while the flow starts collecting. A toggle that flickers
 * is a toggle the user does not trust.
 */
@HiltViewModel
class AttachmentSheetViewModel @Inject constructor(
    private val settings: SettingsStore,
) : ViewModel() {

    /**
     * Whether a search may leave the device. Off by default and off until asked for.
     *
     * This is the same value `DefaultNetworkGate` checks at request time, so the switch is the
     * opt-in itself rather than a display of it — turning it off genuinely closes the door.
     */
    val webSearch: StateFlow<Boolean> = settings.network
        .map { it.webSearch }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            settings.network.value.webSearch,
        )

    /** Whether Lumi may answer from the user's own files and mail. Local, so on by default. */
    val personalContext: StateFlow<Boolean> = settings.model
        .map { it.personalContext }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            settings.model.value.personalContext,
        )

    fun setWebSearch(enabled: Boolean) = settings.setWebSearch(enabled)

    fun setPersonalContext(enabled: Boolean) = settings.setPersonalContext(enabled)
}
