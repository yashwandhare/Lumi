package com.trace.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trace.ui.components.EmptyState
import com.trace.ui.components.hairlineBorder
import com.trace.ui.home.HomeScreen
import com.trace.ui.navigation.TraceDestination
import com.trace.ui.theme.TraceShape
import com.trace.ui.theme.TraceSize
import com.trace.ui.theme.spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraceApp(
    isDarkTheme: Boolean = false,
    onThemeToggle: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val rightDrawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = rightDrawerState,
            drawerContent = {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        drawerShape = TraceShape.panel,
                        windowInsets = WindowInsets(0),
                        modifier = Modifier
                            .width(TraceSize.drawer)
                            .hairlineBorder(TraceShape.panel)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = MaterialTheme.spacing.md)
                                .padding(
                                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + MaterialTheme.spacing.md,
                                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.md
                                )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = MaterialTheme.spacing.sm, bottom = MaterialTheme.spacing.md)) {
                                Box(modifier = Modifier.size(TraceSize.avatar).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Center).size(TraceSize.icon))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(Modifier.height(MaterialTheme.spacing.md))
                            // Empty history placeholder
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(Modifier.height(MaterialTheme.spacing.xl))
                                Text("No history yet", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                Spacer(Modifier.height(MaterialTheme.spacing.xs))
                                Text("Past conversations will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                            }
                            Spacer(Modifier.weight(1f))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .hairlineBorder(RoundedCornerShape(12.dp))
                                    .clickable { }
                                    .padding(horizontal = MaterialTheme.spacing.md, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                                Text("New Chat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                            drawerShape = TraceShape.panel,
                            windowInsets = WindowInsets(0),
                            modifier = Modifier
                                .width(TraceSize.drawer)
                                .hairlineBorder(TraceShape.panel)
                        ) {
                            AppDrawerContent(
                                currentRoute = currentRoute,
                                onNavigate = { destination ->
                                    navController.navigate(destination.route) {
                                        popUpTo(TraceDestination.HOME.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                ) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        topBar = {
                            TopAppBar(
                                title = { },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Rounded.Menu, contentDescription = "Menu")
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { scope.launch { rightDrawerState.open() } }) {
                                        Icon(Icons.Rounded.History, contentDescription = "History")
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            )
                        }
                    ) { contentPadding ->
            NavHost(
                navController = navController,
                startDestination = TraceDestination.HOME.route,
                modifier = Modifier.padding(contentPadding),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) {
                composable(TraceDestination.HOME.route) {
                    HomeScreen()
                }
                composable(TraceDestination.MEMORY.route) {
                    EmptyState(
                        headline = "Memory",
                        invitation = "Things Trace remembers.",
                    )
                }
                composable(TraceDestination.NOTES.route) {
                    EmptyState(
                        headline = "Notes",
                        invitation = "Your saved notes.",
                    )
                }
                composable(TraceDestination.ROUTINE.route) {
                    EmptyState(
                        headline = "Routine",
                        invitation = "Scheduled tasks and automations.",
                    )
                }
                composable(TraceDestination.JOURNAL.route) {
                    EmptyState(
                        headline = "Journal",
                        invitation = "Your daily log.",
                    )
                }
                composable(TraceDestination.LISTS.route) {
                    EmptyState(
                        headline = "Lists",
                        invitation = "Checklists and notes.",
                    )
                }
                composable(TraceDestination.MODEL_PARAMETERS.route) {
                    EmptyState(headline = "Model Parameters", invitation = "Tune model settings.")
                }
                composable(TraceDestination.SEARCH_SCOPE.route) {
                    EmptyState(headline = "Search Scope", invitation = "Manage search sources.")
                }
                composable(TraceDestination.SETTINGS.route) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Settings", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(MaterialTheme.spacing.xl))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Dark Mode", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.width(MaterialTheme.spacing.md))
                            Box(modifier = Modifier.scale(0.78f)) {
                            androidx.compose.material3.Switch(
                                checked = isDarkTheme,
                                onCheckedChange = { onThemeToggle() }
                            )
                            }
                        }
                    }
                }
            } // NavHost
        } // Scaffold
    } // ModalNavigationDrawer (Left)
    } // CompositionLocalProvider (Left)
    } // ModalNavigationDrawer (Right)
    } // CompositionLocalProvider (Right)
} // TraceApp

@Composable
private fun AppDrawerContent(
    currentRoute: String?,
    onNavigate: (TraceDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.md)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + MaterialTheme.spacing.md,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.md
            )
    ) {
        // Profile Section
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = MaterialTheme.spacing.sm, bottom = MaterialTheme.spacing.md)) {
            Box(modifier = Modifier.size(TraceSize.avatar).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Text("U", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.width(12.dp))
            Text("User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Spacer(Modifier.height(MaterialTheme.spacing.md))

        DrawerRow(label = "Home", selected = currentRoute == TraceDestination.HOME.route, onClick = { onNavigate(TraceDestination.HOME) }, icon = Icons.Rounded.Home)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        DrawerRow(label = "Memory", selected = currentRoute == TraceDestination.MEMORY.route, onClick = { onNavigate(TraceDestination.MEMORY) }, icon = Icons.Rounded.NoteAlt)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        DrawerRow(label = "Notes", selected = currentRoute == TraceDestination.NOTES.route, onClick = { onNavigate(TraceDestination.NOTES) }, icon = Icons.Rounded.Edit)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        DrawerRow(label = "Routine", selected = currentRoute == TraceDestination.ROUTINE.route, onClick = { onNavigate(TraceDestination.ROUTINE) }, icon = Icons.Rounded.Event)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        DrawerRow(label = "Journal", selected = currentRoute == TraceDestination.JOURNAL.route, onClick = { onNavigate(TraceDestination.JOURNAL) }, icon = Icons.Rounded.Book)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        DrawerRow(label = "Lists", selected = currentRoute == TraceDestination.LISTS.route, onClick = { onNavigate(TraceDestination.LISTS) }, icon = Icons.AutoMirrored.Rounded.List)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        DrawerRow(label = "Model Parameters", selected = currentRoute == TraceDestination.MODEL_PARAMETERS.route, onClick = { onNavigate(TraceDestination.MODEL_PARAMETERS) }, icon = Icons.Rounded.Tune)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        DrawerRow(label = "Search Scope", selected = currentRoute == TraceDestination.SEARCH_SCOPE.route, onClick = { onNavigate(TraceDestination.SEARCH_SCOPE) }, icon = Icons.Rounded.FindInPage)

        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Spacer(Modifier.height(MaterialTheme.spacing.sm))
        DrawerRow(label = "Settings", selected = currentRoute == TraceDestination.SETTINGS.route, onClick = { onNavigate(TraceDestination.SETTINGS) }, icon = Icons.Rounded.Settings)
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            // DESIGN_LANGUAGE §2 lists the selected drawer row among the four places the accent is
            // allowed. Selection is still carried by the row's fill and the label's weight, so the
            // colour is a third channel rather than the only one — §3 and for_devb.md both require
            // that nothing be conveyed by colour alone.
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            },
            modifier = Modifier.size(TraceSize.icon),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun NavHostController.switchTo(destination: TraceDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
