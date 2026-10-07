package com.alfread.alfvision.ui.navigation

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.alfread.alfvision.core.CaptureEventBus
import com.alfread.alfvision.core.RegionState
import com.alfread.alfvision.service.FloatingPanelService
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.screens.*
import com.alfread.alfvision.util.PermissionUtils

private data class Destination(val route: String, val label: String, val icon: ImageVector)

@Composable
fun ALFNavigation(
    viewModel: MainViewModel,
    activity: Activity,
    onStartVision: () -> Unit,
    onStopVision: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    val nav = rememberNavController()
    val destinations = listOf(
        Destination("home", "Home", Icons.Default.Home),
        Destination("vision", "Vision", Icons.Default.Visibility),
        Destination("chat", "Chat", Icons.Default.Chat),
        Destination("history", "History", Icons.Default.History),
        Destination("settings", "Settings", Icons.Default.Settings)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val entry by nav.currentBackStackEntryAsState()
                val current = entry?.destination?.route
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination.route,
                        onClick = {
                            nav.navigate(destination.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(
                    settings = viewModel.settings.collectAsStateWithLifecycle().value,
                    apiConfigured = viewModel.apiKeyConfigured.collectAsStateWithLifecycle().value,
                    connectionStatus = viewModel.connectionStatus.collectAsStateWithLifecycle().value,
                    onStartVision = onStartVision,
                    onStopVision = onStopVision,
                    overlayGranted = PermissionUtils.overlayGranted(activity),
                    microphoneGranted = PermissionUtils.microphoneGranted(activity),
                    notificationsGranted = PermissionUtils.notificationsGranted(activity),
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onRequestMicrophone = onRequestMicrophone,
                    onRequestNotifications = onRequestNotifications,
                    onOpenSettings = { nav.navigate("settings") }
                )
            }
            composable("vision") {
                VisionScreen(
                    viewModel = viewModel,
                    onSelectRegion = {
                        if (PermissionUtils.overlayGranted(activity)) {
                            activity.startService(IntentFactory.regionSelector(activity))
                        } else onOpenOverlaySettings()
                    },
                    onCapture = { CaptureEventBus.request(RegionState.current) }
                )
            }
            composable("chat") { ChatScreen(viewModel, onRequestMicrophone) }
            composable("history") { HistoryScreen(viewModel) }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onRequestMicrophone = onRequestMicrophone,
                    onRequestNotifications = onRequestNotifications
                )
            }
        }
    }
}

private object IntentFactory {
    fun regionSelector(activity: Activity) =
        android.content.Intent(activity, FloatingPanelService::class.java).setAction("show_region_selector")
}
