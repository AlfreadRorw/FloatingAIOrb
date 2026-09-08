package com.example.floatingaiorb

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var projectionManager: MediaProjectionManager

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
                }
                ContextCompat.startForegroundService(this, serviceIntent)
            }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            MaterialTheme {
                MainScreen(
                    overlayEnabled = Settings.canDrawOverlays(this),
                    captureEnabled = ScreenCaptureService.isRunning,
                    onOverlay = { openOverlayPermission() },
                    onCapture = { requestCapture() },
                    onStopCapture = {
                        startService(Intent(this, ScreenCaptureService::class.java).apply {
                            action = ScreenCaptureService.ACTION_STOP
                        })
                    }
                )
            }
        }
    }

    private fun openOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun requestCapture() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}

@Composable
private fun MainScreen(
    overlayEnabled: Boolean,
    captureEnabled: Boolean,
    onOverlay: () -> Unit,
    onCapture: () -> Unit,
    onStopCapture: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Floating AI Orb", style = MaterialTheme.typography.headlineMedium)
        Text("AI assistant yang bisa tetap tampil di atas game/aplikasi.")

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Floating Orb")
                Text(if (overlayEnabled) "✓ Izin overlay aktif" else "Izin overlay belum diberikan")
                Button(onClick = onOverlay) {
                    Text(if (overlayEnabled) "Izin Sudah Aktif" else "Aktifkan Floating Orb")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Screen Capture")
                Text(
                    if (captureEnabled)
                        "✓ Screen capture aktif"
                    else
                        "AI belum dapat melihat layar."
                )
                if (!captureEnabled) {
                    Button(onClick = onCapture) { Text("Mulai Screen Capture") }
                } else {
                    OutlinedButton(onClick = onStopCapture) { Text("Hentikan Capture") }
                }
            }
        }

        Text(
            "Catatan: screen capture selalu membutuhkan persetujuan sistem Android. " +
                "Versi ini menyediakan fondasi vision pipeline; sambungkan model AI/Vision " +
                "pilihanmu pada tahap berikutnya."
        )
    }
}
