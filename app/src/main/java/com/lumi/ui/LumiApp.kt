package com.lumi.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lumi.ui.components.EmptyState
import com.lumi.ui.components.hairlineBorder
import com.lumi.ui.chat.ChatViewModel
import com.lumi.ui.home.HomeScreen
import com.lumi.ui.navigation.LumiDestination
import com.lumi.ui.settings.SettingsScreen
import com.lumi.ui.theme.LumiShape
import com.lumi.ui.theme.LumiSize
import com.lumi.ui.theme.spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumiApp(
    isDarkTheme: Boolean = false,
    onThemeToggle: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val rightDrawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Hoisted to the shell rather than obtained inside the Home destination. The history drawer and
    // the transcript have to be the same conversation, and a `hiltViewModel()` call inside a
    // `composable {}` is scoped to that back-stack entry — the drawer would have got a second,
    // unrelated instance, so opening a past chat would have changed nothing on screen.
    val chatViewModel: ChatViewModel = hiltViewModel()
    val history by chatViewModel.history.collectAsStateWithLifecycle()
    val chatTurns by chatViewModel.turns.collectAsStateWithLifecycle()
    val chatGenerating by chatViewModel.generating.collectAsStateWithLifecycle()
    val liveMascot by chatViewModel.liveMascot.collectAsStateWithLifecycle()
    val voiceTurnActive by chatViewModel.voiceTurnActive.collectAsStateWithLifecycle()

    // Docked only during a conversation on Home, and only if the user wants a live mascot. On the
    // empty Home screen it is still the centrepiece, so duplicating it in the bar would be two of it.
    //
    // **Never while voice mode is up.** The voice screen's whole subject is one large mascot in the
    // middle; a second miniature of the same character in the corner reads as a duplicate rather
    // than as a status, and the two animate independently, which makes it obvious they are two.
    val dockMascot = liveMascot &&
        chatTurns.isNotEmpty() &&
        !voiceTurnActive &&
        currentRoute == LumiDestination.HOME.route

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = rightDrawerState,
            drawerContent = {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        drawerShape = LumiShape.panel,
                        windowInsets = WindowInsets(0),
                        modifier = Modifier
                            .width(LumiSize.drawer)
                            .hairlineBorder(LumiShape.panel)
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
                                Box(modifier = Modifier.size(LumiSize.avatar).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Center).size(LumiSize.icon))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(Modifier.height(MaterialTheme.spacing.md))
                            if (history.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Spacer(Modifier.height(MaterialTheme.spacing.xl))
                                    Text("No history yet", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                    Spacer(Modifier.height(MaterialTheme.spacing.xs))
                                    Text("Past conversations will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                                }
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
                                ) {
                                    items(history, key = { chat -> chat.id }) { chat ->
                                        HistoryRow(
                                            title = chat.title,
                                            onClick = {
                                                chatViewModel.openConversation(chat.id)
                                                navController.navigate(LumiDestination.HOME.route) {
                                                    popUpTo(LumiDestination.HOME.route) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                                scope.launch { rightDrawerState.close() }
                                            },
                                            onDelete = { chatViewModel.deleteConversation(chat.id) },
                                        )
                                    }
                                }
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
                                    .clickable {
                                        chatViewModel.newConversation()
                                        navController.navigate(LumiDestination.HOME.route) {
                                            popUpTo(LumiDestination.HOME.route) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                        scope.launch { rightDrawerState.close() }
                                    }
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
                            drawerShape = LumiShape.panel,
                            windowInsets = WindowInsets(0),
                            modifier = Modifier
                                .width(LumiSize.drawer)
                                .hairlineBorder(LumiShape.panel)
                        ) {
                            AppDrawerContent(
                                currentRoute = currentRoute,
                                onNavigate = { destination ->
                                    navController.navigate(destination.route) {
                                        popUpTo(LumiDestination.HOME.route) { saveState = true }
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
                                title = {
                                    // The mascot docks here once a conversation starts, so it stays
                                    // present without occupying the middle of a screen the user is now
                                    // reading. It keeps reacting — the same composable, so the same
                                    // breathing and the same motion gating, just a smaller slot.
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = dockMascot,
                                        enter = androidx.compose.animation.fadeIn() +
                                            androidx.compose.animation.expandHorizontally(),
                                        exit = androidx.compose.animation.fadeOut() +
                                            androidx.compose.animation.shrinkHorizontally(),
                                    ) {
                                        com.lumi.ui.components.LumiBlob(
                                            modifier = Modifier.size(LumiSize.avatar),
                                            isTyping = chatGenerating,
                                        )
                                    }
                                },
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
                startDestination = LumiDestination.HOME.route,
                modifier = Modifier.padding(contentPadding),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) {
                composable(LumiDestination.HOME.route) {
                    HomeScreen(viewModel = chatViewModel)
                }
                composable(LumiDestination.MEMORY.route) {
                    EmptyState(
                        headline = "Memory",
                        invitation = "Things Lumi remembers.",
                    )
                }
                composable(LumiDestination.ROUTINE.route) {
                    EmptyState(
                        headline = "Routine",
                        invitation = "Scheduled tasks and automations.",
                    )
                }
                composable(LumiDestination.JOURNAL.route) {
                    EmptyState(
                        headline = "Journal",
                        invitation = "Your daily log.",
                    )
                }
                composable(LumiDestination.REMINDERS.route) {
                    EmptyState(
                        headline = "Reminders",
                        invitation = "Reminders, todos, and calendar items will show up here once you ask Lumi to keep track of something.",
                    )
                }
                composable(LumiDestination.SEARCH_SCOPE.route) {
                    EmptyState(headline = "Search Scope", invitation = "Manage search sources.")
                }
                composable(LumiDestination.SETTINGS.route) {
                    SettingsScreen(
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = onThemeToggle,
                    )
                }
            } // NavHost
        } // Scaffold
    } // ModalNavigationDrawer (Left)
    } // CompositionLocalProvider (Left)
    } // ModalNavigationDrawer (Right)
    } // CompositionLocalProvider (Right)
} // LumiApp

/**
 * One stored conversation in the history drawer.
 *
 * The delete control is always visible rather than hidden behind a swipe or a long press: the drawer is
 * narrow, a swipe there competes with the drawer's own dismiss gesture, and a long press is not
 * discoverable.
 */
@Composable
private fun HistoryRow(
    title: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Delete this conversation",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AppDrawerContent(
    currentRoute: String?,
    onNavigate: (LumiDestination) -> Unit
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
            Box(modifier = Modifier.size(LumiSize.avatar).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Text("U", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.width(12.dp))
            Text("User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Spacer(Modifier.height(MaterialTheme.spacing.md))

        DrawerRow(label = "Home", selected = currentRoute == LumiDestination.HOME.route, onClick = { onNavigate(LumiDestination.HOME) }, icon = Icons.Rounded.Home)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        DrawerRow(label = "Memory", selected = currentRoute == LumiDestination.MEMORY.route, onClick = { onNavigate(LumiDestination.MEMORY) }, icon = Icons.Rounded.NoteAlt)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        DrawerRow(label = "Routine", selected = currentRoute == LumiDestination.ROUTINE.route, onClick = { onNavigate(LumiDestination.ROUTINE) }, icon = Icons.Rounded.Event)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        DrawerRow(label = "Journal", selected = currentRoute == LumiDestination.JOURNAL.route, onClick = { onNavigate(LumiDestination.JOURNAL) }, icon = Icons.Rounded.Book)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        DrawerRow(label = "Reminders", selected = currentRoute == LumiDestination.REMINDERS.route, onClick = { onNavigate(LumiDestination.REMINDERS) }, icon = Icons.Rounded.Checklist)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        DrawerRow(label = "Search Scope", selected = currentRoute == LumiDestination.SEARCH_SCOPE.route, onClick = { onNavigate(LumiDestination.SEARCH_SCOPE) }, icon = Icons.Rounded.FindInPage)

        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Spacer(Modifier.height(MaterialTheme.spacing.sm))
        DrawerRow(label = "Settings", selected = currentRoute == LumiDestination.SETTINGS.route, onClick = { onNavigate(LumiDestination.SETTINGS) }, icon = Icons.Rounded.Settings)
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
            // The selected row is a glass panel like every other raised surface in the app — §4's
            // recipe is alpha plus a hairline, and it was carrying the fill without the border.
            .then(
                if (selected) Modifier.hairlineBorder(RoundedCornerShape(10.dp))
                else Modifier
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
            modifier = Modifier.size(LumiSize.icon),
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

private fun NavHostController.switchTo(destination: LumiDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
