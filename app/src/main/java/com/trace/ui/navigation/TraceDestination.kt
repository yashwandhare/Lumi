package com.trace.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The whole navigation graph.
 *
 * Five destinations, one level deep. Individual workflows open as contextual screens or
 * sheets rather than extending this list — the product's own rule is that a user should
 * express intent instead of walking a menu tree.
 *
 * The three middle entries are the product's pillars: what you own, what you automate, and
 * what protects you.
 *
 * These icons come from the small set bundled with Material 3. They are stand-ins: the
 * design system calls for one consistent icon style and stroke weight across the app, which
 * means a purpose-drawn Trace set. Dev B replaces all five. `material-icons-extended` is
 * deliberately not a dependency — it added 40MB of generated classes to the debug APK for
 * five glyphs.
 */
enum class TraceDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Outlined.Home),
    DATA("data", "Data", Icons.Outlined.List),
    TIME("time", "Time", Icons.Outlined.DateRange),
    SAFETY("safety", "Safety", Icons.Outlined.Warning),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}
