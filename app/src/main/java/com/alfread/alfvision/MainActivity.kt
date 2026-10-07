package com.alfread.alfvision

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.alfread.alfvision.service.ScreenCaptureService
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.navigation.ALFNavHost
import com.alfread.alfvision.ui.navigation.navigateTo
import com.alfread.alfvision.ui.theme.ALFVisionTheme
import com.alfread.alfvision.ui.theme.isDarkTheme
import com.alfread.alfvision.vision.VoiceState

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            val settings by vm.settings.collectAsState()
            val dark = isDarkTheme(settings)
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            ALFVisionTheme(settings) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()

                    LaunchedEffect(pendingRoute) {
                        pendingRoute?.let {
                            navController.navigateTo(it)
                            pendingRoute = null
                        }
                    }

                    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        val data = result.data
                        if (result.resultCode == Activity.RESULT_OK && data != null) {
                            val intent = Intent(this@MainActivity, ScreenCaptureService::class.java)
                                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                                .putExtra(ScreenCaptureService.EXTRA_DATA, data)
                            runCatching { ContextCompat.startForegroundService(this@MainActivity, intent) }
                                .onFailure { vm.session.setError("Screen capture gagal dimulai. Coba lagi.") }
                        } else {
                            vm.session.setError("Screen capture permission belum diberikan.")
                        }
                    }

                    val beginVision: () -> Unit = {
                        vm.startFloating()
                        if (!ScreenCaptureService.isRunning) {
                            val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
                            if (manager != null) captureLauncher.launch(manager.createScreenCaptureIntent())
                            else vm.session.setError("MediaProjection tidak tersedia di perangkat ini.")
                        }
                    }

                    // Izin notifikasi diminta DULU, baru dialog screen capture. Dua dialog sekaligus
                    // sebelumnya saling membatalkan.
                    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
                        beginVision()
                    }
                    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) vm.voice() else vm.session.setError("Izin mikrofon ditolak.")
                    }

                    ALFNavHost(
                        navController = navController,
                        vm = vm,
                        onStartVision = {
                            if (!Settings.canDrawOverlays(this@MainActivity)) {
                                openOverlaySettings()
                            } else if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                beginVision()
                            }
                        },
                        onStopVision = { vm.stopVision() },
                        onRequestMicrophone = {
                            when {
                                vm.session.voiceState.value == VoiceState.LISTENING -> vm.stopVoice()
                                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> vm.voice()
                                else -> microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onOverlay = { openOverlaySettings() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(EXTRA_ROUTE)
    }

    private fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }
    }

    companion object {
        const val EXTRA_ROUTE = "alf_route"
    }
}
