package com.trace.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.ui.graphics.vector.ImageVector

enum class TraceDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Rounded.Home),
    MEMORY("memory", "Memory", Icons.Rounded.Storage),
    NOTES("notes", "Notes", Icons.Rounded.Edit),
    ROUTINE("routine", "Routine", Icons.Rounded.AccessTime),
    JOURNAL("journal", "Journal", Icons.Rounded.Book),
    LISTS("lists", "Lists", Icons.AutoMirrored.Rounded.List),
    SEARCH_SCOPE("search_scope", "Search Scope", Icons.Rounded.FindInPage),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings),
}
