package com.alfread.alfvision

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.alfread.alfvision.service.FloatingPanelService
import com.alfread.alfvision.service.ScreenCaptureService
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.navigation.ALFNavigation
import com.alfread.alfvision.ui.theme.ALFVisionTheme
import com.alfread.alfvision.util.PermissionUtils

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var projectionManager: MediaProjectionManager
    private var pendingStartVision = false

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startCaptureService(result.resultCode, result.data!!)
            startFloatingPanel()
        }
        pendingStartVision = false
    }

    private val microphoneLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        setContent {
            val settings = viewModel.settings.collectAsStateWithLifecycle().value
            ALFVisionTheme(settings) {
                ALFNavigation(
                    viewModel = viewModel,
                    activity = this,
                    onStartVision = ::startVision,
                    onStopVision = ::stopVision,
                    onOpenOverlaySettings = ::openOverlaySettings,
                    onRequestMicrophone = ::requestMicrophone,
                    onRequestNotifications = ::requestNotifications
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingStartVision && PermissionUtils.overlayGranted(this)) {
            requestProjection()
        }
    }

    private fun startVision() {
        if (!PermissionUtils.overlayGranted(this)) {
            pendingStartVision = true
            openOverlaySettings()
            return
        }
        requestProjection()
    }

    private fun requestProjection() {
        val captureIntent = projectionManager.createScreenCaptureIntent()
        projectionLauncher.launch(captureIntent)
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val metrics = resources.displayMetrics
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            putExtra(ScreenCaptureService.EXTRA_WIDTH, metrics.widthPixels)
            putExtra(ScreenCaptureService.EXTRA_HEIGHT, metrics.heightPixels)
            putExtra(ScreenCaptureService.EXTRA_DENSITY, metrics.densityDpi)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun startFloatingPanel() {
        startService(Intent(this, FloatingPanelService::class.java).setAction("show"))
    }

    private fun stopVision() {
        startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
        stopService(Intent(this, FloatingPanelService::class.java))
    }

    private fun openOverlaySettings() {
        startActivity(PermissionUtils.overlaySettingsIntent(this))
    }

    private fun requestMicrophone() {
        if (Build.VERSION.SDK_INT >= 23) microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
