package com.alfread.alfvision.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.screens.*

sealed class Dest(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Dest("home", "Home", Icons.Default.Home)
    data object Vision : Dest("vision", "Vision", Icons.Default.Visibility)
    data object Chat : Dest("chat", "Chat", Icons.Default.Chat)
    data object History : Dest("history", "History", Icons.Default.History)
    data object Settings : Dest("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun ALFNavHost(navController: NavHostController, vm: MainViewModel, onStartVision: () -> Unit, onRequestMicrophone: () -> Unit, onOverlay: () -> Unit) {
    val items = listOf(Dest.Home, Dest.Vision, Dest.Chat, Dest.History, Dest.Settings)
    Scaffold(bottomBar = {
        NavigationBar {
            val current = navController.currentBackStackEntryAsState().value?.destination?.route
            items.forEach { item ->
                NavigationBarItem(
                    selected = current == item.route,
                    onClick = { navController.navigate(item.route) { launchSingleTop = true } },
                    icon = { Icon(item.icon, item.label) },
                    label = { Text(item.label) },
                                    )
            }
        }
    }) { padding ->
        NavHost(navController, startDestination = Dest.Home.route, modifier = androidx.compose.ui.Modifier) {
            composable(Dest.Home.route) { HomeScreen(vm, padding, onStartVision, onOverlay) }
            composable(Dest.Vision.route) { VisionScreen(vm, padding) }
            composable(Dest.Chat.route) { ChatScreen(vm, padding, onRequestMicrophone) }
            composable(Dest.History.route) { HistoryScreen(vm, padding) }
            composable(Dest.Settings.route) { SettingsScreen(vm, padding, onOverlay) }
        }
    }
}
