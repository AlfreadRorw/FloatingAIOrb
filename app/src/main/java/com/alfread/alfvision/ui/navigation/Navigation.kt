package com.alfread.alfvision.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.components.DockBar
import com.alfread.alfvision.ui.components.DockItem
import com.alfread.alfvision.ui.screens.*

sealed class Dest(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Dest("home", "Home", Icons.Default.Home)
    data object Vision : Dest("vision", "Vision", Icons.Default.Visibility)
    data object Chat : Dest("chat", "Chat", Icons.AutoMirrored.Filled.Chat)
    data object History : Dest("history", "History", Icons.Default.History)
    data object Settings : Dest("settings", "Settings", Icons.Default.Settings)
}

/** Navigasi tab: tidak menumpuk back stack dan state tiap tab tersimpan. */
fun NavHostController.navigateTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun ALFNavHost(
    navController: NavHostController,
    vm: MainViewModel,
    onStartVision: () -> Unit,
    onStopVision: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onOverlay: () -> Unit
) {
    val dockItems = remember {
        listOf(Dest.Home, Dest.Vision, Dest.Chat, Dest.History, Dest.Settings).map { DockItem(it.route, it.label, it.icon) }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination?.route
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = !imeVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Box(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DockBar(items = dockItems, selectedRoute = current, onSelect = { navController.navigateTo(it.route) })
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier,
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) }
        ) {
            composable(Dest.Home.route) {
                HomeScreen(vm, padding, onStartVision, onStopVision, onOverlay) { navController.navigateTo(it) }
            }
            composable(Dest.Vision.route) { VisionScreen(vm, padding, onOverlay) }
            composable(Dest.Chat.route) { ChatScreen(vm, padding, onRequestMicrophone) }
            composable(Dest.History.route) { HistoryScreen(vm, padding) { navController.navigateTo(Dest.Chat.route) } }
            composable(Dest.Settings.route) { SettingsScreen(vm, padding, onOverlay) }
        }
    }
}
