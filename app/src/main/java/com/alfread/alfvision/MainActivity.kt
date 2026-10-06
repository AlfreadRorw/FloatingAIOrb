package com.alfread.alfvision

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.alfread.alfvision.service.FloatingPanelService
import com.alfread.alfvision.service.ScreenCaptureService
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.navigation.ALFNavHost
import com.alfread.alfvision.ui.theme.ALFVisionTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsState()
            ALFVisionTheme(settings) {
                Surface(Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        val data = result.data
                        if (result.resultCode == Activity.RESULT_OK && data != null) {
                            val intent = Intent(this@MainActivity, ScreenCaptureService::class.java)
                                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                                .putExtra(ScreenCaptureService.EXTRA_DATA, data)
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                        } else vm.session.setError("Screen capture permission belum diberikan.")
                    }
                    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.voice() }

                    ALFNavHost(
                        navController = navController,
                        vm = vm,
                        onStartVision = {
                            startVision(captureLauncher)
                            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onRequestMicrophone = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                        onOverlay = { openOverlaySettings() }
                    )
                }
            }
        }
    }

    private fun startVision(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        vm.startFloating()
        if (!ScreenCaptureService.isRunning) {
            val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
            launcher.launch(manager.createScreenCaptureIntent())
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
    }
}
