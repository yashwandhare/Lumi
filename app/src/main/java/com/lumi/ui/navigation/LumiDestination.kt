package com.lumi.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.ui.graphics.vector.ImageVector

enum class LumiDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Rounded.Home),
    MEMORY("memory", "Memory", Icons.Rounded.Storage),
    ROUTINE("routine", "Routine", Icons.Rounded.AccessTime),
    JOURNAL("journal", "Journal", Icons.Rounded.Book),

    /**
     * Reminders, todos, and calendar items, all in one place.
     *
     * One destination rather than three: they are the same thing to a user — something to be done at
     * a time — and splitting them would mean guessing which of three screens a spoken "remind me
     * tomorrow" belongs on.
     */
    REMINDERS("reminders", "Reminders", Icons.Rounded.Checklist),
    SEARCH_SCOPE("search_scope", "Search Scope", Icons.Rounded.FindInPage),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings),
}
