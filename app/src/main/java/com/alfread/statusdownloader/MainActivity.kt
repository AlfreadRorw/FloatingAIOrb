package com.alfread.statusdownloader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.alfread.statusdownloader.data.Permissions
import com.alfread.statusdownloader.data.ThemeMode
import com.alfread.statusdownloader.ui.AlfreadApp
import com.alfread.statusdownloader.ui.theme.AlfreadTheme
import com.alfread.statusdownloader.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.checkPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // false-edge-to-edge: konten dimulai DI BAWAH status bar (sinyal & baterai tetap terlihat)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            val settings by vm.settings.collectAsState()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AlfreadTheme(dark = dark) {
                val bg = MaterialTheme.colorScheme.background
                SideEffect {
                    window.statusBarColor = bg.toArgb()
                    window.navigationBarColor = bg.toArgb()
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }
                AlfreadApp(
                    vm = vm,
                    onRequestPermission = ::requestStoragePermission,
                    onRequestNotifAccess = ::requestNotificationAccess,
                    onOpenAppInfo = ::openAppInfo
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.checkPermission() // sekaligus memuat ulang status saat kembali dari WhatsApp
    }

    private fun requestNotificationAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            openAppInfo()
        }
    }

    private fun openAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            permissionLauncher.launch(Permissions.legacyPermissions)
        }
    }
}
