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
import android.app.KeyguardManager
import android.os.PowerManager
import android.view.View
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
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
import com.alfread.alfdownloader.termux.ServerGuard
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
import kotlinx.coroutines.flow.MutableStateFlow
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

    // ---- bingkai + bar judul bertema di sekeliling jendela aplikasi
    private var borderView: ComposeView? = null
    private var borderLife: OverlayLifecycleOwner? = null
    private var borderParams: WindowManager.LayoutParams? = null
    private var titleView: ComposeView? = null
    private var titleLife: OverlayLifecycleOwner? = null
    private var titleParams: WindowManager.LayoutParams? = null
    private var frameApp by mutableStateOf<NativeApp?>(null)
    private var guardJob: Job? = null
    private var dragging = false
    private var lastRaiseAt = 0L
    private var presetIndex = 1
    private var isNativeMaximized by mutableStateOf(false)
    private var isNativeMinimized by mutableStateOf(false)
    private var preMaximizeBounds: Rect? = null
    private var minimizedBounds: Rect? = null
    // Antrian 1-slot: setiap perubahan bentuk jendela (geser/resize/maximize/minimize) cukup menulis
    // ke sini, lalu satu worker di bawah yang benar-benar memanggil Shizuku secara berurutan.
    // Ini mencegah beberapa panggilan "am task resize" yang ditembak bertubi-tubi selesai
    // tidak berurutan (race) saat diseret cepat, yang sebelumnya bikin jendela kelihatan
    // "lompat"/numpuk sebentar dengan posisi lama.
    private val resizeRequest = MutableStateFlow<Rect?>(null)
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
            settings.update { it.copy(floatingEnabled = false) }
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
        startForeground(NOTIF_ID, buildNotification("Alfread Tools aktif"))
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        nativeApps = loadLaunchableApps(this)

        if (prefs0.useShizuku && ShizukuHelper.hasPermission()) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    ShizukuHelper.whitelistBattery(packageName)
                    ShizukuHelper.hardenTermux(packageName)   // anti-mati: phantom killer, Doze, background
                }
            }
        }
        addBar(prefs0)
        startWatchdog()
        startResizeWorker()
        startTermuxHardenLoop()
    }

    /** Satu-satunya tempat yang benar-benar memanggil ShizukuHelper.resizeTask, berurutan. */
    private fun startResizeWorker() {
        scope.launch(Dispatchers.IO) {
            resizeRequest.collect { r ->
                val task = nativeTaskId
                if (r != null && task != null && !r.isEmpty) {
                    runCatching { ShizukuHelper.resizeTask(task, r.left, r.top, r.right, r.bottom) }
                }
            }
        }
    }

    /**
     * Ulangi "anti-mati" Termux secara berkala (bukan cuma sekali di onCreate). Beberapa ROM
     * (MIUI, ColorOS, dll.) diam-diam mengembalikan standby bucket / pembatasan baterai Termux
     * setelah beberapa waktu idle, sehingga server Termux bisa mati-nyala sendiri di latar
     * belakang walau sudah pernah "dibebaskan" sekali.
     */
    private fun startTermuxHardenLoop() {
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(15 * 60 * 1000L)
                val p = settings.load()
                if (p.useShizuku && ShizukuHelper.hasPermission()) {
                    runCatching {
                        ShizukuHelper.whitelistBattery(packageName)
                        ShizukuHelper.hardenTermux(packageName)
                    }
                }
            }
        }
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
            AlfTheme(accent, p.fontIndex) {
                OverlayBubble(
                    sideLeft = sideLeft,
                    prefs = p,
                    pollHealth = { runCatching { api.health() }.getOrNull() },
                    pollJobs = { runCatching { api.jobs() }.getOrNull().orEmpty() },
                    pollLog = { runCatching { api.log(60) }.getOrElse { listOf("Server offline — log tidak bisa dibaca.") } },
                    onDragY = { dy -> moveBarBy(dy) },
                    onDragX = { dx ->
                        val task = nativeTaskId
                        if (task != null) resizeCurrentNativeBy(dxPx = dx) else resizeBarThicknessBy(dx)
                    },
                    onDragEnd = {
                        persistBarPosition()
                        if (nativeTaskId != null && !isNativeMaximized && !isNativeMinimized) {
                            settings.update { it.copy(nativeWindowWidthDp = nativeWindowWidthDp) }
                            saveNativeBoundsFor(frameApp?.packageName, nativeBounds)
                        }
                    },
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
                        onStartServer = { ServerGuard.stoppedByUser = false; startServerNow(true) },
                        onStopServer = { ServerGuard.stoppedByUser = true; scope.launch { runCatching { api.shutdown() }; toast("Server dimatikan") } },
                        onRestartServer = { restartServer() },
                        onFixServer = { fixServerNow() },
                        onOpenFolder = { openDownloadsFolder() },
                        onToggleLock = { savePrefs { it.copy(windowLock = !it.windowLock) } },
                        onToggleFrame = { savePrefs { it.copy(windowFrame = !it.windowFrame) } },
                        onCloseWindow = { closeNativeWindow() },
                        onSetFont = { i -> savePrefs { it.copy(fontIndex = i) } },
                        onCaptionHeight = { v -> savePrefs { it.copy(captionHeightDp = v.coerceIn(28, 64)) }; updateFrameLayout() },
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
        panelPrefs = settings.update(block)
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
                    val shouldStart = (p.autoStart || p.keepServerAlive) && !ServerGuard.stoppedByUser &&
                        now - lastServerStart > 15_000 && now - offlineSince > 1_500
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
        settings.update { it.copy(bubbleThicknessDp = dp) }
    }

    private fun persistBarPosition() {
        val y = barParams.y
        val th = (barParams.width / resources.displayMetrics.density).toInt()
        settings.update { it.copy(bubbleY = y, bubbleThicknessDp = th) }
    }

    private fun switchSide() {
        sideLeft = !sideLeft
        settings.update { it.copy(bubbleSide = if (sideLeft) 0 else 1) }
        if (barAttached) runCatching { wm.removeView(barView) }
        barAttached = false
        addBar(settings.load())
    }

    private fun togglePin(app: NativeApp) {
        pinned = if (app.packageName in pinned) pinned - app.packageName else pinned + app.packageName
        val list = pinned.toList()
        settings.update { it.copy(pinnedPackages = list) }
    }

    private fun applyPreset(i: Int) {
        presetIndex = i
        val (w, h) = when (i) { 0 -> 280 to 440; 2 -> 440 to 720; 3 -> 340 to 820; else -> 360 to 560 }
        updateNativeSize(w, h)
    }

    private fun setAnchor(i: Int) {
        nativeAnchor = i
        settings.update { it.copy(nativeAnchor = i) }
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
            // Kalau app ini pernah diatur posisi/ukurannya, pakai itu lagi (per app, tidak global).
            loadNativeBoundsFor(app.packageName)?.let { saved ->
                nativeBounds.set(saved)
                resizeRequest.value = Rect(nativeBounds)
            }
            withContext(Dispatchers.Main) {
                frameApp = app
                isNativeMaximized = false
                isNativeMinimized = false
                if (panelPrefs.windowFrame) showFrame()
                startWindowGuard(app)
            }
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
        if (nativeTaskId == null) return
        nativeBounds.set(computeBounds())
        resizeRequest.value = Rect(nativeBounds)
    }

    /**
     * Resize lewat drag horizontal di bar mengambang. Dulu fungsi ini TIDAK memanggil
     * updateFrameLayout(), jadi bingkai/bar judul tema tetap di ukuran lama sementara jendela
     * aslinya sudah berubah ukuran → kelihatan numpuk/robek sesaat. Juga dulu menulis ke
     * SharedPreferences di SETIAP frame drag (jank); sekarang cuma mengubah nilai di memori,
     * penyimpanan permanennya terjadi sekali saat drag selesai (lihat onDragEnd di addBar()).
     */
    private fun resizeCurrentNativeBy(dxPx: Int) {
        if (nativeTaskId == null || nativeBounds.isEmpty) return
        val minW = (240 * resources.displayMetrics.density).toInt()
        val maxW = resources.displayMetrics.widthPixels - nativeBounds.left
        val width = (nativeBounds.width() + dxPx * if (sideLeft) 1 else -1).coerceIn(minW, maxW)
        nativeBounds.right = nativeBounds.left + width
        updateFrameLayout()
        resizeRequest.value = Rect(nativeBounds)
        nativeWindowWidthDp = (width / resources.displayMetrics.density).toInt()
    }

    private fun updateNativeSize(widthDp: Int?, heightDp: Int?) {
        val old = settings.load()
        nativeWindowWidthDp = (widthDp ?: old.nativeWindowWidthDp).coerceIn(240, 600)
        nativeWindowHeightDp = (heightDp ?: old.nativeWindowHeightDp).coerceIn(320, 900)
        val wDp = nativeWindowWidthDp
        val hDp = nativeWindowHeightDp
        settings.update { it.copy(nativeWindowWidthDp = wDp, nativeWindowHeightDp = hDp) }
        scope.launch(Dispatchers.IO) {
            if (nativeTaskId != null) applyPreferredNativeBounds()
        }
    }

    override fun onDestroy() {
        running = false
        runCatching { getSharedPreferences("alf_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener) }
        guardJob?.cancel()
        removeFrame()
        if (barAttached) runCatching { wm.removeView(barView) }
        barLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        barLifecycle.destroy()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- aksi Alat

    private fun restartServer() {
        ServerGuard.stoppedByUser = false
        scope.launch {
            runCatching { api.shutdown() }
            toast("Memulai ulang server…")
            delay(1800)
            startServerNow(true)
        }
    }

    /** Anti-mati penuh lewat Shizuku (phantom killer, Doze, background) + nyalakan server bila mati. */
    private fun fixServerNow() {
        ServerGuard.stoppedByUser = false
        scope.launch(Dispatchers.IO) {
            ShizukuHelper.refresh()
            if (ShizukuHelper.hasPermission()) {
                val ok = ShizukuHelper.hardenTermux(packageName)
                toast(if (ok) "Anti-mati aktif: Termux dibebaskan dari pembatasan latar belakang" else "Sebagian perintah gagal — coba lagi")
            } else {
                toast("Butuh Shizuku aktif & diizinkan untuk anti-mati penuh. Penjaga tetap menyalakan ulang server otomatis.")
            }
            withContext(Dispatchers.Main) { if (!serverOnline) startServerNow(false) }
        }
    }

    private fun openDownloadsFolder() {
        val uri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FALF%20Downloader")
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(view) }.onFailure {
            runCatching {
                startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { toast("Buka folder: Download/ALF Downloader") }
        }
    }

    // ---------------------------------------------------------------- bingkai & penjaga jendela aplikasi

    private fun ensureFrame() {
        if (borderView != null) return
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        // garis tepi: tidak bisa disentuh, jadi sentuhan tembus ke aplikasi
        val bp = WindowManager.LayoutParams(1, 1, overlayType(), base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START }
        val bl = OverlayLifecycleOwner()
        val bv = ComposeView(this)
        bl.attachToView(bv)
        bv.setContent {
            val p = panelPrefs
            val accent = AccentOptions[p.accent.coerceIn(0, AccentOptions.lastIndex)].color
            AlfTheme(accent, p.fontIndex) { WindowBorderOverlay(p) }
        }

        // bar judul: menutupi bar judul polos ROM, bisa diseret, punya tombol kunci/ukuran/X
        val tp = WindowManager.LayoutParams(1, 1, overlayType(), base, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START }
        val tl = OverlayLifecycleOwner()
        val tv = ComposeView(this)
        tl.attachToView(tv)
        tv.setContent {
            val p = panelPrefs
            val accent = AccentOptions[p.accent.coerceIn(0, AccentOptions.lastIndex)].color
            AlfTheme(accent, p.fontIndex) {
                WindowTitleOverlay(
                    prefs = p, app = frameApp,
                    isMaximized = isNativeMaximized, isMinimized = isNativeMinimized,
                    onDrag = { dx, dy -> moveNativeBy(dx, dy) },
                    onDragEnd = { finishNativeDrag() },
                    onToggleLock = { savePrefs { it.copy(windowLock = !it.windowLock) } },
                    onCycleSize = { applyPreset((presetIndex + 1) % 4) },
                    onToggleMaximize = { toggleMaximizeNative() },
                    onToggleMinimize = { toggleMinimizeNative() },
                    onClose = { closeNativeWindow() }
                )
            }
        }

        for ((lf, v) in listOf(bl to bv, tl to tv)) {
            lf.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lf.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lf.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        borderView = bv; borderLife = bl; borderParams = bp
        titleView = tv; titleLife = tl; titleParams = tp
        updateFrameGeometry()
        val okB = runCatching { wm.addView(bv, bp) }.isSuccess
        val okT = runCatching { wm.addView(tv, tp) }.isSuccess
        if (!okB || !okT) removeFrame()
    }

    private fun showFrame() {
        if (nativeBounds.isEmpty) nativeBounds.set(computeBounds())
        ensureFrame()
        updateFrameLayout()
        setFrameVisible(true)
    }

    private fun updateFrameGeometry() {
        val d = resources.displayMetrics.density
        val b = nativeBounds
        borderParams?.let { it.x = b.left; it.y = b.top; it.width = b.width().coerceAtLeast(1); it.height = b.height().coerceAtLeast(1) }
        titleParams?.let {
            it.x = b.left; it.y = b.top
            it.width = b.width().coerceAtLeast(1)
            it.height = (panelPrefs.captionHeightDp * d).toInt().coerceAtLeast(1)
        }
    }

    private fun updateFrameLayout() {
        if (borderView == null) return
        updateFrameGeometry()
        runCatching { borderView?.let { wm.updateViewLayout(it, borderParams) } }
        runCatching { titleView?.let { wm.updateViewLayout(it, titleParams) } }
    }

    private fun setFrameVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        borderView?.visibility = v
        titleView?.visibility = v
    }

    private fun removeFrame() {
        borderView?.let { runCatching { wm.removeView(it) } }
        titleView?.let { runCatching { wm.removeView(it) } }
        for (lf in listOf(borderLife, titleLife)) {
            lf?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            lf?.destroy()
        }
        borderView = null; borderLife = null; borderParams = null
        titleView = null; titleLife = null; titleParams = null
    }

    /** Seret bar judul → pindahkan jendela aplikasi. Bingkai ikut seketika; resize task asli
     * dikirim lewat [resizeRequest] (diantre, tidak langsung) supaya tidak ada panggilan Shizuku
     * yang tabrakan/selesai tidak berurutan saat diseret cepat. */
    private fun moveNativeBy(dx: Int, dy: Int) {
        if (nativeBounds.isEmpty) return
        dragging = true
        val m = resources.displayMetrics
        val d = m.density
        val w = nativeBounds.width()
        val capPx = (panelPrefs.captionHeightDp * d).toInt()
        val l = (nativeBounds.left + dx).coerceIn(0, (m.widthPixels - w).coerceAtLeast(0))
        val t = (nativeBounds.top + dy).coerceIn(0, (m.heightPixels - capPx).coerceAtLeast(0))
        nativeBounds.offsetTo(l, t)
        updateFrameLayout()
        resizeRequest.value = Rect(nativeBounds)
    }

    private fun finishNativeDrag() {
        resizeRequest.value = Rect(nativeBounds)
        if (!isNativeMaximized && !isNativeMinimized) saveNativeBoundsFor(frameApp?.packageName, nativeBounds)
        scope.launch { delay(250); dragging = false }
    }

    /** Maximize/restore jendela aplikasi (seperti tombol maximize di desktop). */
    private fun toggleMaximizeNative() {
        if (nativeTaskId == null || nativeBounds.isEmpty) return
        if (isNativeMinimized) {
            minimizedBounds?.let { nativeBounds.set(it) }
            minimizedBounds = null
            isNativeMinimized = false
        }
        if (isNativeMaximized) {
            preMaximizeBounds?.let { nativeBounds.set(it) }
            preMaximizeBounds = null
            isNativeMaximized = false
        } else {
            preMaximizeBounds = Rect(nativeBounds)
            val m = resources.displayMetrics
            val margin = (6 * m.density).toInt()
            nativeBounds.set(margin, margin, m.widthPixels - margin, m.heightPixels - margin)
            isNativeMaximized = true
        }
        updateFrameLayout()
        resizeRequest.value = Rect(nativeBounds)
    }

    /** Minimize jendela aplikasi jadi cuma bar judul (tetap bisa diseret), tanpa menutup task. */
    private fun toggleMinimizeNative() {
        if (nativeTaskId == null || nativeBounds.isEmpty) return
        if (isNativeMaximized) {
            preMaximizeBounds?.let { nativeBounds.set(it) }
            preMaximizeBounds = null
            isNativeMaximized = false
        }
        if (isNativeMinimized) {
            minimizedBounds?.let { nativeBounds.set(it) }
            minimizedBounds = null
            isNativeMinimized = false
        } else {
            minimizedBounds = Rect(nativeBounds)
            val capPx = (panelPrefs.captionHeightDp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
            nativeBounds.bottom = nativeBounds.top + capPx
            isNativeMinimized = true
        }
        updateFrameLayout()
        resizeRequest.value = Rect(nativeBounds)
    }

    /** Simpan posisi+ukuran terakhir per paket aplikasi, supaya dibuka lagi nanti di tempat yang sama. */
    private fun saveNativeBoundsFor(pkg: String?, r: Rect) {
        if (pkg == null || r.isEmpty) return
        val v = "${r.left},${r.top},${r.width()},${r.height()}"
        settings.update { it.copy(nativeBoundsByApp = it.nativeBoundsByApp + (pkg to v)) }
    }

    private fun loadNativeBoundsFor(pkg: String): Rect? {
        val parts = settings.load().nativeBoundsByApp[pkg]?.split(",")?.mapNotNull { it.toIntOrNull() } ?: return null
        if (parts.size != 4) return null
        val (l, t, w, h) = parts
        if (w <= 0 || h <= 0) return null
        val m = resources.displayMetrics
        val left = l.coerceIn(0, (m.widthPixels - w).coerceAtLeast(0))
        val top = t.coerceIn(0, (m.heightPixels - h).coerceAtLeast(0))
        return Rect(left, top, left + w, top + h)
    }

    /**
     * Penjaga jendela: memantau task freeform setiap ~0,8 dtk.
     *  - menyelaraskan bingkai dengan posisi/ukuran jendela yang sebenarnya,
     *  - bila "Kunci di atas" aktif dan jendela tersembunyi (tertutup aplikasi lain karena mengetuk di luar),
     *    bawa lagi ke depan — jendela hanya tertutup lewat tombol X,
     *  - bila task benar-benar hilang (ditutup dari tempat lain) bingkai dibersihkan.
     */
    private fun startWindowGuard(app: NativeApp) {
        guardJob?.cancel()
        guardJob = scope.launch {
            var missing = 0
            delay(700)
            while (nativeTaskId != null) {
                val info = withContext(Dispatchers.IO) { ShizukuHelper.findTask(app.packageName) }
                var visible = true
                if (info == null) {
                    if (++missing >= 3) { endNativeSession(); break }
                } else {
                    missing = 0
                    nativeTaskId = info.id
                    val freeform = info.mode == null || info.mode == "freeform"
                    visible = info.visible != false
                    val m = resources.displayMetrics
                    val b = info.bounds
                    if (b != null && freeform && !dragging && b.width() >= 200 && b.height() >= 200 &&
                        (b.width() < m.widthPixels || b.height() < m.heightPixels)
                    ) nativeBounds.set(b)

                    if (!visible && panelPrefs.windowLock && screenAwake() &&
                        System.currentTimeMillis() - lastRaiseAt > 2500
                    ) {
                        lastRaiseAt = System.currentTimeMillis()
                        withContext(Dispatchers.IO) { ShizukuHelper.bringToFront(app.packageName, app.activityName) }
                    }

                    if (panelPrefs.windowFrame && freeform) {
                        if (borderView == null) showFrame()
                        updateFrameLayout()
                        setFrameVisible(visible)
                    } else if (borderView != null) {
                        removeFrame()
                    }
                }
                delay(if (visible) 800 else 450)
            }
        }
    }

    private fun screenAwake(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        return pm?.isInteractive != false && km?.isKeyguardLocked != true
    }

    private fun endNativeSession() {
        nativeTaskId = null
        isNativeMaximized = false
        isNativeMinimized = false
        preMaximizeBounds = null
        minimizedBounds = null
        removeFrame()
        frameApp = null
    }

    /** Tombol X: satu-satunya cara menutup jendela. Coba hapus task; bila masih ada, hentikan paksa aplikasinya. */
    private fun closeNativeWindow() {
        val app = frameApp
        val task = nativeTaskId
        if (app == null && task == null) { toast("Tidak ada jendela aplikasi yang terbuka"); return }
        scope.launch {
            withContext(Dispatchers.IO) {
                if (task != null) ShizukuHelper.removeTask(task)
                delay(650)
                if (app != null && ShizukuHelper.findTask(app.packageName) != null) ShizukuHelper.forceStop(app.packageName)
            }
            endNativeSession()
        }
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
            .setContentTitle("Alfread Tools")
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(0, "Tutup bar", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
