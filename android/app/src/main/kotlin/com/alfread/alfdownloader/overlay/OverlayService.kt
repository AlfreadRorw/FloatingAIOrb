package com.alfread.alfdownloader.overlay

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.withContext
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import com.alfread.alfdownloader.MainActivity
import com.alfread.alfdownloader.R
import com.alfread.alfdownloader.data.SettingsStore
import com.alfread.alfdownloader.model.CreateJobRequest
import com.alfread.alfdownloader.model.Prefs
import com.alfread.alfdownloader.termux.TermuxRunner
import com.alfread.alfdownloader.network.Api
import com.alfread.alfdownloader.shizuku.ShizukuHelper
import com.alfread.alfdownloader.ui.AccentOptions
import com.alfread.alfdownloader.ui.AlfTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class OverlayService : Service() {
    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.alfread.alfdownloader.STOP_OVERLAY"
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
    private var nativeApps = emptyList<NativeApp>()

    private var nativeTaskId: Int? = null
    private var nativeBounds = Rect()
    private var nativeWindowWidthDp by mutableIntStateOf(360)
    private var nativeWindowHeightDp by mutableIntStateOf(560)
    private var nativeAnchor by mutableIntStateOf(0)
    private var pinned by mutableStateOf(setOf<String>())
    private var freeformPrepared = false
    private var panelPrefs by mutableStateOf(Prefs())
    private var panelExpanded = false
    private lateinit var termux: TermuxRunner
    private var serverOnline = false
    private var offlineSince = 0L
    private var lastServerStart = 0L
    private var lastNotifText = ""
    // Sinkronkan perubahan dari layar Pengaturan aplikasi (tema, ukuran, dll.) ke panel secara langsung
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runCatching { panelPrefs = settings.load(); pinned = panelPrefs.pinnedPackages.toSet() }
    }

    private fun toast(msg: String) {
        scope.launch(Dispatchers.Main) { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settings.save(settings.load().copy(floatingEnabled = false))
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        settings = SettingsStore(this)
        val prefs0 = settings.load()
        sideLeft = prefs0.bubbleSide == 0
        api = Api(prefs0.serverUrl)
        nativeWindowWidthDp = prefs0.nativeWindowWidthDp
        nativeWindowHeightDp = prefs0.nativeWindowHeightDp
        nativeAnchor = prefs0.nativeAnchor
        pinned = prefs0.pinnedPackages.toSet()
        ShizukuHelper.init()
        panelPrefs = prefs0
        getSharedPreferences("alf_prefs", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
        termux = TermuxRunner(this)
        startForeground(NOTIF_ID, buildNotification("ALF mengambang aktif"))
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        nativeApps = loadLaunchableApps(this)

        if (prefs0.useShizuku && ShizukuHelper.hasPermission()) {
            runCatching {
                ShizukuHelper.whitelistBattery(packageName)
                ShizukuHelper.whitelistBattery("com.termux")
            }
        }
        addBar(prefs0)
        startWatchdog()
    }

    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun addBar(prefs0: com.alfread.alfdownloader.model.Prefs) {
        val d = resources.displayMetrics.density
        val widthPx = (prefs0.bubbleThicknessDp.coerceIn(4, 18) * d).toInt()
        val heightPx = (prefs0.bubbleLengthDp.coerceIn(60, 260) * d).toInt()
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        barParams = WindowManager.LayoutParams(
            widthPx, heightPx, overlayType(), flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or (if (sideLeft) Gravity.START else Gravity.END)
            x = 0
            y = if (prefs0.bubbleY >= 0) prefs0.bubbleY else 420
        }

        barLifecycle = OverlayLifecycleOwner()
        barView = ComposeView(this)
        barLifecycle.attachToView(barView)

        barView.setContent {
            val p = panelPrefs
            val accent = AccentOptions[p.accent.coerceIn(0, AccentOptions.lastIndex)].color
            AlfTheme(accent) {
                OverlayBubble(
                    sideLeft = sideLeft,
                    prefs = p,
                    pollHealth = { runCatching { api.health() }.getOrNull() },
                    pollJobs = { runCatching { api.jobs() }.getOrNull().orEmpty() },
                    onDragY = { dy -> moveBarBy(dy) },
                    onDragX = { dx ->
                        val task = nativeTaskId
                        if (task != null) resizeCurrentNativeBy(dxPx = dx) else resizeBarThicknessBy(dx)
                    },
                    onDragEnd = { persistBarPosition() },
                    onExpandedChange = { expanded -> setBarExpanded(expanded) },
                    apps = nativeApps,
                    pinned = pinned,
                    windowWidthDp = nativeWindowWidthDp,
                    windowHeightDp = nativeWindowHeightDp,
                    anchor = nativeAnchor,
                    actions = PanelActions(
                        onDownload = { url, quality ->
                            if (url.isNotBlank()) scope.launch {
                                runCatching { api.createJob(CreateJobRequest(url = url, quality = quality, audioFormat = "mp3")) }
                                    .onFailure { toast("Gagal: ${it.message?.take(80) ?: "server offline"}") }
                            }
                        },
                        onCancel = { id -> scope.launch { runCatching { api.cancel(id) } } },
                        onOpenApp = { openApp() },
                        onClose = { stopSelf() },
                        onSwitchSide = { switchSide() },
                        onRefreshApps = { nativeApps = loadLaunchableApps(this) },
                        onLaunchNative = { app -> openNativeApp(app) },
                        onTogglePin = { app -> togglePin(app) },
                        onWidth = { v -> updateNativeSize(widthDp = v, heightDp = null) },
                        onHeight = { v -> updateNativeSize(widthDp = null, heightDp = v) },
                        onPreset = { i -> applyPreset(i) },
                        onAnchor = { i -> setAnchor(i) },
                        onStartServer = { startServerNow(true) },
                        onStopServer = { scope.launch { runCatching { api.shutdown() }; toast("Server dimatikan") } },
                        onUpdateYtdlp = { scope.launch { runCatching { api.updateYtdlp() }.onSuccess { toast("Memperbarui yt-dlp…") } } },
                        onClearFinished = { scope.launch { runCatching { api.clearFinished() }; toast("Riwayat selesai dibersihkan") } },
                        onSetTheme = { i -> savePrefs { it.copy(panelTheme = i) } },
                        onSetOpacity = { v -> savePrefs { it.copy(panelOpacity = v) } }
                    )
                )
            }
        }

        barLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        barLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        barLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        runCatching { wm.addView(barView, barParams); barAttached = true }.onFailure { stopSelf() }
    }

    private fun savePrefs(block: (Prefs) -> Prefs) {
        val next = block(settings.load())
        settings.save(next)
        panelPrefs = next
    }

    private fun setBarExpanded(expanded: Boolean) {
        panelExpanded = expanded
        val d = resources.displayMetrics.density
        val p = settings.load()
        val collapsedWidth = (p.bubbleThicknessDp.coerceIn(4, 18) * d).toInt()
        val collapsedHeight = (p.bubbleLengthDp.coerceIn(60, 260) * d).toInt()
        barParams.flags = if (expanded) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        barParams.width = if (expanded) (p.panelWidthDp * d).toInt() else collapsedWidth
        barParams.height = if (expanded) WindowManager.LayoutParams.WRAP_CONTENT else collapsedHeight
        // Blur latar di belakang panel (Android 12+, bila perangkat mendukung)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (expanded && p.panelBlur) {
                barParams.flags = barParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                barParams.blurBehindRadius = (24 * d).toInt()
            } else {
                barParams.flags = barParams.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                barParams.blurBehindRadius = 0
            }
        }
        if (barAttached) runCatching { wm.updateViewLayout(barView, barParams) }
    }

    // ---------------------------------------------------------------- server watchdog

    /** Nyalakan server lewat Termux. Bisa dipanggil tombol manual atau watchdog. */
    private fun startServerNow(manual: Boolean) {
        if (!termux.isInstalled()) { if (manual) toast("Termux belum terpasang"); return }
        lastServerStart = System.currentTimeMillis()
        termux.startServer(settings.load().serverDir)
            .onSuccess { if (manual) toast("Menyalakan server…") }
            .onFailure { if (manual) toast(it.message ?: "Gagal menjalankan Termux") }
    }

    /**
     * Pantau server. Bila mati dan "Jaga server tetap hidup" aktif → nyalakan otomatis.
     * Bar mengambang berstatus jendela terlihat, sehingga Android mengizinkan start Termux dari latar belakang.
     */
    private fun startWatchdog() {
        scope.launch {
            delay(800)
            while (true) {
                val h = runCatching { api.health() }.getOrNull()
                val up = h?.ok == true
                val p = settings.load()
                if (up) {
                    serverOnline = true; offlineSince = 0L
                } else {
                    serverOnline = false
                    val now = System.currentTimeMillis()
                    if (offlineSince == 0L) offlineSince = now
                    val shouldStart = (p.autoStart || p.keepServerAlive) &&
                        now - lastServerStart > 40_000 && now - offlineSince > 1_500
                    if (shouldStart) startServerNow(false)
                }
                updateNotification(
                    when {
                        !up -> "Server offline • menyalakan otomatis…"
                        h?.ready == false -> "Server online • memuat yt-dlp…"
                        else -> "Server online • yt-dlp ${h?.ytdlp ?: ""}"
                    }
                )
                delay(if (up) 4000 else 2500)
            }
        }
    }

    private fun updateNotification(text: String) {
        if (text == lastNotifText) return
        lastNotifText = text
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text)) }
    }

    private fun moveBarBy(dy: Int) {
        val metrics = resources.displayMetrics
        barParams.y = (barParams.y + dy).coerceIn(0, (metrics.heightPixels - barParams.height).coerceAtLeast(0))
        if (barAttached) runCatching { wm.updateViewLayout(barView, barParams) }
    }

    private fun resizeBarThicknessBy(dx: Int) {
        val min = (4 * resources.displayMetrics.density).toInt()
        val max = (18 * resources.displayMetrics.density).toInt()
        val direction = if (sideLeft) 1 else -1
        barParams.width = (barParams.width + dx * direction).coerceIn(min, max)
        if (barAttached) runCatching { wm.updateViewLayout(barView, barParams) }
        val dp = (barParams.width / resources.displayMetrics.density).toInt()
        settings.save(settings.load().copy(bubbleThicknessDp = dp))
    }

    private fun persistBarPosition() {
        val p = settings.load()
        settings.save(p.copy(bubbleY = barParams.y, bubbleThicknessDp = (barParams.width / resources.displayMetrics.density).toInt()))
    }

    private fun switchSide() {
        sideLeft = !sideLeft
        settings.save(settings.load().copy(bubbleSide = if (sideLeft) 0 else 1))
        if (barAttached) runCatching { wm.removeView(barView) }
        barAttached = false
        addBar(settings.load())
    }

    private fun togglePin(app: NativeApp) {
        pinned = if (app.packageName in pinned) pinned - app.packageName else pinned + app.packageName
        settings.save(settings.load().copy(pinnedPackages = pinned.toList()))
    }

    private fun applyPreset(i: Int) {
        val (w, h) = when (i) { 0 -> 280 to 440; 2 -> 440 to 720; 3 -> 340 to 820; else -> 360 to 560 }
        updateNativeSize(w, h)
    }

    private fun setAnchor(i: Int) {
        nativeAnchor = i
        settings.save(settings.load().copy(nativeAnchor = i))
        scope.launch(Dispatchers.IO) { if (nativeTaskId != null) applyPreferredNativeBounds() }
    }

    private fun openNativeApp(app: NativeApp) {
        val prefs0 = settings.load()
        ShizukuHelper.refresh()

        // Tanpa Shizuku: coba tetap jendela kecil lewat launch bounds (jalan bila freeform aktif di Opsi pengembang).
        if (!prefs0.useShizuku || !ShizukuHelper.hasPermission()) {
            val why = when {
                !prefs0.useShizuku -> "Shizuku dimatikan di Pengaturan"
                !ShizukuHelper.isReady() -> "Shizuku belum dijalankan"
                else -> "ALF belum diizinkan di Shizuku"
            }
            toast("$why — aplikasi dibuka dengan cara biasa")
            if (!launchWithBounds(app)) launchNativeApp(this, app.packageName)
            return
        }

        scope.launch(Dispatchers.IO) {
            if (!freeformPrepared) { ShizukuHelper.enableFreeformSupport(); freeformPrepared = true }
            val started = ShizukuHelper.launchFreeform(app.packageName, app.activityName)
            if (!started) {
                withContext(Dispatchers.Main) { launchNativeApp(this@OverlayService, app.packageName) }
                toast("Perintah freeform gagal, dibuka biasa. Cek Pengaturan → Diagnosa.")
                return@launch
            }
            var info: ShizukuHelper.TaskInfo? = null
            for (i in 0 until 15) {
                delay(200)
                info = ShizukuHelper.findTask(app.packageName)
                if (info != null) break
            }
            nativeTaskId = info?.id
            if (info == null) return@launch
            if (info.mode != null && info.mode != "freeform") {
                // Terbuka fullscreen: coba paksa jadi freeform
                ShizukuHelper.setTaskFreeform(info.id)
                delay(250)
                val again = ShizukuHelper.findTask(app.packageName)
                if (again?.mode != null && again.mode != "freeform") {
                    toast("ROM menolak jendela mengambang. Aktifkan \"Enable freeform windows\" & \"Force activities to be resizable\" di Opsi pengembang, lalu restart HP.")
                    return@launch
                }
            }
            applyPreferredNativeBounds()
        }
    }

    private fun computeBounds(): Rect {
        val d = resources.displayMetrics.density
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        val w = (nativeWindowWidthDp * d).toInt().coerceIn((240 * d).toInt(), sw)
        val h = (nativeWindowHeightDp * d).toInt().coerceIn((320 * d).toInt(), sh)
        val m = (10 * d).toInt()
        val top0 = (sh * 0.06f).toInt()
        val (left, top) = when (nativeAnchor) {
            1 -> m to top0
            2 -> (sw - w - m) to top0
            3 -> m to (sh - h - m * 4)
            4 -> (sw - w - m) to (sh - h - m * 4)
            else -> ((sw - w) / 2) to ((sh - h) / 3)
        }
        val l = left.coerceAtLeast(0); val t = top.coerceAtLeast(0)
        return Rect(l, t, (l + w).coerceAtMost(sw), (t + h).coerceAtMost(sh))
    }

    private fun launchWithBounds(app: NativeApp): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opts = ActivityOptions.makeBasic().setLaunchBounds(computeBounds())
        startActivity(intent, opts.toBundle())
        true
    }.getOrDefault(false)

    private fun applyPreferredNativeBounds() {
        val task = nativeTaskId ?: return
        nativeBounds.set(computeBounds())
        ShizukuHelper.resizeTask(task, nativeBounds.left, nativeBounds.top, nativeBounds.right, nativeBounds.bottom)
    }

    private fun resizeCurrentNativeBy(dxPx: Int) {
        val task = nativeTaskId ?: return
        if (nativeBounds.isEmpty) return
        val minW = (240 * resources.displayMetrics.density).toInt()
        val maxW = resources.displayMetrics.widthPixels - nativeBounds.left
        val width = (nativeBounds.width() + dxPx * if (sideLeft) 1 else -1).coerceIn(minW, maxW)
        nativeBounds.right = nativeBounds.left + width
        ShizukuHelper.resizeTask(task, nativeBounds.left, nativeBounds.top, nativeBounds.right, nativeBounds.bottom)
        val dp = (width / resources.displayMetrics.density).toInt()
        settings.save(settings.load().copy(nativeWindowWidthDp = dp))
    }

    private fun updateNativeSize(widthDp: Int?, heightDp: Int?) {
        val old = settings.load()
        nativeWindowWidthDp = (widthDp ?: old.nativeWindowWidthDp).coerceIn(240, 600)
        nativeWindowHeightDp = (heightDp ?: old.nativeWindowHeightDp).coerceIn(320, 900)
        val next = old.copy(
            nativeWindowWidthDp = nativeWindowWidthDp,
            nativeWindowHeightDp = nativeWindowHeightDp
        )
        settings.save(next)
        scope.launch(Dispatchers.IO) {
            if (nativeTaskId != null) applyPreferredNativeBounds()
        }
    }

    override fun onDestroy() {
        running = false
        runCatching { getSharedPreferences("alf_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener) }
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

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Jendela mengambang", NotificationManager.IMPORTANCE_MIN))
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alf)
            .setContentTitle("ALF mengambang")
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(0, "Tutup bar", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
