package com.example.floatingaiorb

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "START_CAPTURE"
        const val ACTION_STOP = "STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"

        @Volatile var isRunning = false
            private set

        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 1001
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var orbView: FrameLayout? = null
    private var windowManager: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_menu_view)
                        .setContentTitle("Floating AI Orb")
                        .setContentText("Screen capture aktif")
                        .setOngoing(true)
                        .build()
                )
                startCapture(
                    intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED),
                    intent.getParcelableExtraCompat(EXTRA_DATA)
                )
            }
            ACTION_STOP -> stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent?) {
        if (data == null || resultCode != Activity.RESULT_OK) return

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = projection?.createVirtualDisplay(
            "FloatingAIOrbCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        isRunning = true
        showOrb()
    }

    private fun showOrb() {
        if (!SettingsCompat.canDrawOverlays(this)) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val container = FrameLayout(this)

        val orb = TextView(this).apply {
            text = "✦"
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(android.R.drawable.btn_default)
            setPadding(24, 16, 24, 16)
            setOnClickListener { showAiPanel() }
        }

        container.addView(orb)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            x = 24
            y = 220
        }

        try {
            windowManager?.addView(container, params)
            orbView = container
        } catch (_: Exception) {}
    }

    private fun showAiPanel() {
        val panel = android.app.AlertDialog.Builder(this)
            .setTitle("✦ AI Assistant")
            .setMessage(
                "Screen capture aktif.\n\n" +
                "Tahap berikutnya dapat mengirim frame layar ke model Vision " +
                "untuk menganalisis game dan memberikan saran."
            )
            .setPositiveButton("OK", null)
            .create()

        panel.setOnShowListener {
            panel.window?.setType(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE
            )
        }
        panel.window?.setType(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE
        )
        panel.show()
    }

    private fun stopCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null

        orbView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        orbView = null

        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating AI screen capture",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}

private object SettingsCompat {
    fun canDrawOverlays(context: Context): Boolean =
        android.provider.Settings.canDrawOverlays(context)
}

private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(
    key: String
): T? {
    return if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
}
