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
import com.alfread.alfdownloader.shizuku.ShizukuHelper
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
    private lateinit var barParams: WindowManager.LayoutParams
    private lateinit var barView: ComposeView
    private lateinit var barLifecycle: OverlayLifecycleOwner
    private lateinit var settings: SettingsStore
    private lateinit var api: Api
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var barAttached = false
    private var sideLeft = false

    private var browserParams: WindowManager.LayoutParams? = null
    private var browserView: ComposeView? = null
    private var browserLifecycle: OverlayLifecycleOwner? = null
    private var currentShortcut: AppShortcut? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        settings = SettingsStore(this)
        val prefs0 = settings.load()
        sideLeft = prefs0.bubbleSide == 0
        api = Api(prefs0.serverUrl)
        startForeground(NOTIF_ID, buildNotification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        if (prefs0.useShizuku && ShizukuHelper.hasPermission()) {
            runCatching {
                ShizukuHelper.whitelistBattery(packageName)
                ShizukuHelper.whitelistBattery("com.termux")
            }
        }

        addBar(prefs0)
    }

    // ---------------- bar + panel window

    private fun addBar(prefs0: com.alfread.alfdownloader.model.Prefs) {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        barParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or (if (sideLeft) Gravity.START else Gravity.END)
            x = 0
            y = if (prefs0.bubbleY >= 0) prefs0.bubbleY else 420
        }

        barLifecycle = OverlayLifecycleOwner()
        barView = ComposeView(this)
        barLifecycle.attachToView(barView)

        barView.setContent {
            val accent = AccentOptions[prefs0.accent.coerceIn(0, AccentOptions.lastIndex)].color
            AlfTheme(accent) {
                OverlayBubble(
                    sideLeft = sideLeft,
                    pollHealth = { runCatching { api.health() }.getOrNull() },
                    pollJobs = { runCatching { api.jobs() }.getOrNull().orEmpty() },
                    onDownload = { url -> if (url.isNotBlank()) scope.launch { runCatching { api.createJob(CreateJobRequest(url = url)) } } },
                    onOpenApp = { openApp() },
                    onClose = { stopSelf() },
                    onDragY = { dy -> moveBarBy(dy) },
                    onDragEnd = { persistBarPosition() },
                    onExpandedChange = { expanded -> setBarFocusable(expanded) },
                    onSwitchSide = { switchSide() },
                    onOpenShortcut = { shortcut -> openBrowser(shortcut) }
                )
            }
        }

        barLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        barLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        barLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        runCatching { wm.addView(barView, barParams); barAttached = true }.onFailure { stopSelf() }
    }

    private fun moveBarBy(dy: Int) {
        val metrics = resources.displayMetrics
        barParams.y = (barParams.y + dy).coerceIn(0, (metrics.heightPixels - 220).coerceAtLeast(0))
        if (barAttached) runCatching { wm.updateViewLayout(barView, barParams) }
    }

    private fun persistBarPosition() {
        settings.save(settings.load().copy(bubbleY = barParams.y))
    }

    private fun setBarFocusable(focusable: Boolean) {
        barParams.flags = if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (barAttached) runCatching { wm.updateViewLayout(barView, barParams) }
    }

    private fun switchSide() {
        sideLeft = !sideLeft
        settings.save(settings.load().copy(bubbleSide = if (sideLeft) 0 else 1))
        if (barAttached) runCatching { wm.removeView(barView) }
        barAttached = false
        addBar(settings.load())
    }

    // ---------------- mini browser window

    private fun openBrowser(shortcut: AppShortcut) {
        currentShortcut = shortcut
        if (browserView != null) {
            browserView?.setContent { browserContent(shortcut) }
            return
        }
        val density = resources.displayMetrics.density
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            (320 * density).toInt(), (480 * density).toInt(), overlayType,
            0, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((resources.displayMetrics.widthPixels - width) / 2).coerceAtLeast(0)
            y = ((resources.displayMetrics.heightPixels - height) / 3).coerceAtLeast(0)
        }
        browserParams = params

        val lifecycle = OverlayLifecycleOwner()
        val view = ComposeView(this)
        lifecycle.attachToView(view)
        view.setContent { browserContent(shortcut) }
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        runCatching {
            wm.addView(view, params)
            browserView = view
            browserLifecycle = lifecycle
        }
    }

    @androidx.compose.runtime.Composable
    private fun browserContent(shortcut: AppShortcut) {
        val prefs0 = settings.load()
        val accent = AccentOptions[prefs0.accent.coerceIn(0, AccentOptions.lastIndex)].color
        AlfTheme(accent) {
            MiniBrowserOverlay(
                shortcut = shortcut,
                onSwitch = { s -> openBrowser(s) },
                onOpenNative = { openNativeOrFreeform(shortcut) },
                onMinimize = { closeBrowser() },
                onClose = { closeBrowser() },
                onDragHeader = { dx, dy -> moveBrowserBy(dx, dy) },
                onDragHeaderEnd = {},
                onResize = { dx, dy -> resizeBrowserBy(dx, dy) },
                onResizeEnd = {}
            )
        }
    }

    private fun moveBrowserBy(dx: Int, dy: Int) {
        val p = browserParams ?: return
        val metrics = resources.displayMetrics
        p.x = (p.x + dx).coerceIn(0, (metrics.widthPixels - p.width).coerceAtLeast(0))
        p.y = (p.y + dy).coerceIn(0, (metrics.heightPixels - p.height).coerceAtLeast(0))
        browserView?.let { runCatching { wm.updateViewLayout(it, p) } }
    }

    private fun resizeBrowserBy(dx: Int, dy: Int) {
        val p = browserParams ?: return
        val density = resources.displayMetrics.density
        p.width = (p.width + dx).coerceIn((220 * density).toInt(), resources.displayMetrics.widthPixels)
        p.height = (p.height + dy).coerceIn((300 * density).toInt(), resources.displayMetrics.heightPixels)
        browserView?.let { runCatching { wm.updateViewLayout(it, p) } }
    }

    private fun closeBrowser() {
        browserView?.let { runCatching { wm.removeView(it) } }
        browserLifecycle?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        browserLifecycle?.destroy()
        browserView = null
        browserLifecycle = null
        browserParams = null
    }

    private fun openNativeOrFreeform(shortcut: AppShortcut) {
        val pkg = shortcut.installedPackage(this@OverlayService)
        if (pkg == null) { openApp(); return }
        val prefs0 = settings.load()
        val freeformTried = prefs0.useShizuku && ShizukuHelper.hasPermission() &&
            runCatching { ShizukuHelper.launchFreeform(pkg) }.getOrDefault(false)
        if (!freeformTried) launchNativeApp(this@OverlayService, pkg)
    }

    // ---------------- lifecycle / notif

    override fun onDestroy() {
        running = false
        closeBrowser()
        if (barAttached) runCatching { wm.removeView(barView) }
        barLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        barLifecycle.destroy()
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
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Jendela mengambang", NotificationManager.IMPORTANCE_MIN))
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
