package com.lumi.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.core.ai.ModelHarness
import com.lumi.core.ai.ModelState
import com.lumi.core.ai.GemmaModel
import com.lumi.core.ai.managed
import com.lumi.data.ai.ModelStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives first-run model setup.
 *
 * The one decision worth explaining: **a download this large is never started without being asked
 * for.** 1.9GB is minutes of waiting and real money on a metered plan, so [ModelState.Absent] waits
 * for [start]. A model already on disk needs no permission for anything, so loading begins
 * immediately and the user sees a load rather than a prompt.
 *
 * [ModelStore] is injected to answer one read-only question — is the file already there — because
 * [ModelState] starts at [ModelState.Absent] whether or not it is, and prompting a user to download
 * something they already have would be the worse bug.
 */
@HiltViewModel
class ModelSetupViewModel @Inject constructor(
    private val harness: ModelHarness,
    private val store: ModelStore,
) : ViewModel() {

    val state: StateFlow<ModelState> = harness.state

    private val _awaitingConsent = MutableStateFlow(false)
    val awaitingConsent: StateFlow<Boolean> = _awaitingConsent.asStateFlow()

    private var work: Job? = null

    init {
        if (store.isReady(GemmaModel.managed)) start() else _awaitingConsent.value = true
    }

    /** Idempotent: [ModelHarness.prepare] joins existing work rather than starting a second load. */
    fun start() {
        _awaitingConsent.value = false
        if (work?.isActive == true) return
        work = viewModelScope.launch { harness.prepare() }
    }

    /**
     * Retry after a recoverable failure. The partial download is deliberately left on disk by the
     * downloader, so this resumes from where it stopped rather than starting the 1.9GB again.
     */
    fun retry() = start()
}
