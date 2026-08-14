package com.trace.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trace.ui.components.EmptyState
import com.trace.ui.home.HomeScreen
import com.trace.ui.navigation.TraceDestination

/**
 * The app shell: one navigation bar, one host, five destinations.
 *
 * The section screens below render real empty states rather than placeholders. Dev B
 * replaces each one with its actual content in the phase that owns it — Data in Phase 4,
 * Time in Phase 3, Safety in Phase 5, Settings in Phase 5.
 */
@Composable
fun TraceApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                TraceDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { navController.switchTo(destination) },
                        // The label names the item; a content description here would make
                        // screen readers announce it twice.
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = TraceDestination.HOME.route,
            modifier = Modifier.padding(contentPadding),
        ) {
            composable(TraceDestination.HOME.route) {
                HomeScreen()
            }
            composable(TraceDestination.DATA.route) {
                EmptyState(
                    headline = "Nothing saved yet.",
                    invitation = "Attach a note or a document and Trace can answer from it.",
                )
            }
            composable(TraceDestination.TIME.route) {
                EmptyState(
                    headline = "No routines yet.",
                    invitation = "Tell Trace what you want automated.",
                )
            }
            composable(TraceDestination.SAFETY.route) {
                EmptyState(
                    headline = "SOS is not set up.",
                    invitation = "Add someone Trace should send your location to.",
                )
            }
            composable(TraceDestination.SETTINGS.route) {
                EmptyState(
                    headline = "Settings",
                    invitation = "Which folders Trace can read, and what it is allowed to do.",
                )
            }
        }
    }
}

/**
 * Switches tabs without stacking them.
 *
 * Each destination keeps its own scroll position and state, and the back button leaves the
 * app from any tab rather than walking backwards through every tab visited.
 */
private fun NavHostController.switchTo(destination: TraceDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
