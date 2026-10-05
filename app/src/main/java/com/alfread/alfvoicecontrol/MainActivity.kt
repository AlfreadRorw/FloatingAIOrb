package com.alfread.alfvoicecontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alfread.alfvoicecontrol.data.AlfSettings
import com.alfread.alfvoicecontrol.ui.AlfRoutes
import com.alfread.alfvoicecontrol.ui.commands.AddCommandScreen
import com.alfread.alfvoicecontrol.ui.commands.VoiceCommandsScreen
import com.alfread.alfvoicecontrol.ui.deviceadmin.DeviceAdminScreen
import com.alfread.alfvoicecontrol.ui.home.HomeScreen
import com.alfread.alfvoicecontrol.ui.onboarding.OnboardingScreen
import com.alfread.alfvoicecontrol.ui.pin.PinScreen
import com.alfread.alfvoicecontrol.ui.settings.SettingsScreen
import com.alfread.alfvoicecontrol.ui.theme.ALFVoiceControlTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = AppContainer.getInstance(applicationContext)

        setContent {
            ALFVoiceControlTheme {
                val settings by container.settingsRepository.settingsFlow.collectAsState(initial = AlfSettings())
                val navController = rememberNavController()
                val scope = rememberCoroutineScopeCompat()

                val startDestination = if (settings.onboardingCompleted) AlfRoutes.HOME else AlfRoutes.ONBOARDING

                NavHost(navController = navController, startDestination = startDestination) {
                    composable(AlfRoutes.ONBOARDING) {
                        OnboardingScreen(onFinished = {
                            scope.launch {
                                container.settingsRepository.setOnboardingCompleted(true)
                            }
                            navController.navigate(AlfRoutes.HOME) {
                                popUpTo(AlfRoutes.ONBOARDING) { inclusive = true }
                            }
                        })
                    }

                    composable(AlfRoutes.HOME) {
                        HomeScreen(
                            onManageCommands = { navController.navigate(AlfRoutes.COMMANDS_LIST) },
                            onOpenSettings = { navController.navigate(AlfRoutes.SETTINGS) }
                        )
                    }

                    composable(AlfRoutes.COMMANDS_LIST) {
                        VoiceCommandsScreen(
                            onBack = { navController.popBackStack() },
                            onAddCommand = { navController.navigate(AlfRoutes.addCommand()) },
                            onEditCommand = { id -> navController.navigate(AlfRoutes.addCommand(id)) }
                        )
                    }

                    composable(
                        route = AlfRoutes.ADD_COMMAND,
                        arguments = listOf(
                            navArgument("commandId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val commandId = backStackEntry.arguments?.getString("commandId")
                        AddCommandScreen(
                            existingCommandId = commandId,
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack() }
                        )
                    }

                    composable(AlfRoutes.SETTINGS) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onManageCommands = { navController.navigate(AlfRoutes.COMMANDS_LIST) },
                            onConfigurePin = { navController.navigate(AlfRoutes.PIN_SETUP) },
                            onConfigureDeviceAdmin = { navController.navigate(AlfRoutes.DEVICE_ADMIN) }
                        )
                    }

                    composable(AlfRoutes.DEVICE_ADMIN) {
                        DeviceAdminScreen(onBack = { navController.popBackStack() })
                    }

                    composable(AlfRoutes.PIN_SETUP) {
                        PinScreen(
                            onBack = { navController.popBackStack() },
                            onPinSaved = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
