package com.lumi.ui.reminders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumi.data.local.ReminderDao
import com.lumi.data.local.ReminderEntity
import com.lumi.ui.widget.refreshRemindersWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Each mutation also re-renders the home-screen widget, which reads the same table.
 */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val dao: ReminderDao,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    /**
     * Everything not yet cleared, soonest due first. Null until Room's first emission so the
     * screen can tell "nothing stored" from "not read yet" without inventing a loading state
     * for a query that answers in one frame.
     */
    val items: StateFlow<List<ReminderEntity>?> = dao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun complete(id: Long) {
        viewModelScope.launch {
            if (dao.markDone(id, System.currentTimeMillis()) > 0) {
                refreshRemindersWidget(context)
            }
        }
    }

    fun dismiss(id: Long) {
        viewModelScope.launch {
            if (dao.markDismissed(id, System.currentTimeMillis()) > 0) {
                refreshRemindersWidget(context)
            }
        }
    }
}
