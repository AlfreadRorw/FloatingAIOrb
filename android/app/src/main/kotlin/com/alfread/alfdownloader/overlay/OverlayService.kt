package com.alfread.alfdownloader.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import com.alfread.alfdownloader.MainActivity
import com.alfread.alfdownloader.R
import com.alfread.alfdownloader.data.SettingsStore
import com.alfread.alfdownloader.model.CreateJobRequest
import com.alfread.alfdownloader.network.Api
import com.alfread.alfdownloader.ui.AccentOptions
import com.alfread.alfdownloader.ui.AlfTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayService : Service() {

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 42
        @Volatile var running = false
    }

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var composeView: ComposeView
    private lateinit var lifecycleOwner: OverlayLifecycleOwner
    private lateinit var settings: SettingsStore
    private lateinit var api: Api
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var attached = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        settings = SettingsStore(this)
        val prefs0 = settings.load()
        api = Api(prefs0.serverUrl)
        startForeground(NOTIF_ID, buildNotification())

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (prefs0.bubbleX >= 0) prefs0.bubbleX else 24
            y = if (prefs0.bubbleY >= 0) prefs0.bubbleY else 420
        }

        lifecycleOwner = OverlayLifecycleOwner()
        composeView = ComposeView(this)
        lifecycleOwner.attachToView(composeView)

        composeView.setContent {
            val accent = AccentOptions[prefs0.accent.coerceIn(0, AccentOptions.lastIndex)].color
            AlfTheme(accent) {
                OverlayBubble(
                    pollHealth = { runCatching { api.health() }.getOrNull() },
                    pollJobs = { runCatching { api.jobs() }.getOrNull().orEmpty() },
                    onDownload = { url ->
                        if (url.isNotBlank()) scope.launch { runCatching { api.createJob(CreateJobRequest(url = url)) } }
                    },
                    onOpenApp = { openApp() },
                    onClose = { stopSelf() },
                    onDrag = { dx, dy -> moveBy(dx, dy) },
                    onDragEnd = { settings.save(settings.load().copy(bubbleX = params.x, bubbleY = params.y)) },
                    onExpandedChange = { expanded -> setFocusable(expanded) }
                )
            }
        }

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        runCatching { wm.addView(composeView, params); attached = true }.onFailure { stopSelf() }
    }

    private fun moveBy(dx: Int, dy: Int) {
        val metrics = resources.displayMetrics
        params.x = (params.x + dx).coerceIn(0, (metrics.widthPixels - 160).coerceAtLeast(0))
        params.y = (params.y + dy).coerceIn(0, (metrics.heightPixels - 160).coerceAtLeast(0))
        if (attached) runCatching { wm.updateViewLayout(composeView, params) }
    }

    private fun setFocusable(focusable: Boolean) {
        params.flags = if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (attached) runCatching { wm.updateViewLayout(composeView, params) }
    }

    override fun onDestroy() {
        running = false
        if (attached) runCatching { wm.removeView(composeView) }
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        scope.cancel()
        super.onDestroy()
    }

    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(i)
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Jendela mengambang", NotificationManager.IMPORTANCE_MIN)
        )
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alf)
            .setContentTitle("ALF mengambang aktif")
            .setContentText("Ketuk untuk membuka aplikasi, atau matikan di Pengaturan")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
