package com.lumi.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.data.local.ReminderDao
import com.lumi.data.local.ReminderEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the reminders list and its two clearing actions.
 *
 * Completion and dismissal are deliberately separate: done means the item happened, dismiss
 * means take it away. Both go through the DAO's conditional updates, so clearing a row that
 * just fired cannot resurrect it and a worker firing in the background cannot undo a clear.
 */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val dao: ReminderDao,
) : ViewModel() {

    /**
     * Everything not yet cleared, soonest due first. Null until Room's first emission so the
     * screen can tell "nothing stored" from "not read yet" without inventing a loading state
     * for a query that answers in one frame.
     */
    val items: StateFlow<List<ReminderEntity>?> = dao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun complete(id: Long) {
        viewModelScope.launch { dao.markDone(id, System.currentTimeMillis()) }
    }

    fun dismiss(id: Long) {
        viewModelScope.launch { dao.markDismissed(id, System.currentTimeMillis()) }
    }
}
