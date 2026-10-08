package com.alfread.alfvision.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.alfread.alfvision.AlfVisionApplication
import com.alfread.alfvision.MainActivity
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.core.util.ScreenMetrics
import com.alfread.alfvision.ui.theme.ALFVisionTheme
import com.alfread.alfvision.ui.theme.color
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

class FloatingPanelService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private lateinit var container: com.alfread.alfvision.AppContainer
    private val owner = OverlayLifecycleOwner()

    private var panelView: ComposeView? = null
    private var orbView: ComposeView? = null
    private var regionView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var orbParams: WindowManager.LayoutParams? = null

    private val panelTab = mutableStateOf(PanelTab.CHAT)
    @Volatile private var settingsCache = AppSettings()
    private var minimized = false
    private var expanded = false
    private var savedPanelSize: Pair<Int, Int>? = null
    private var captureSuppressed = false
    private var autoHideJob: Job? = null
    private var persistJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = (application as AlfVisionApplication).container
        wm = getSystemService(WindowManager::class.java)
        owner.create()
        scope.launch {
            settingsCache = container.settingsRepository.flow.first()
            startPanel()
            container.settingsRepository.flow.collect { settingsCache = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> if (minimized) restorePanel() else if (panelView == null) startPanel()
            ACTION_MINIMIZE -> minimize()
            ACTION_CLOSE -> stopSelf()
            ACTION_CAPTURE -> container.controller.capture()
            ACTION_REGION -> showRegionSelector()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        autoHideJob?.cancel()
        persistJob?.cancel()
        removeView(panelView)
        removeView(orbView)
        removeView(regionView)
        panelView = null
        orbView = null
        regionView = null
        scope.cancel()
        owner.destroy()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------------------------------
    // PANEL
    // ---------------------------------------------------------------------------------------

    private fun startPanel() {
        if (panelView != null || minimized) return
        if (!Settings.canDrawOverlays(this)) {
            container.sessionStore.setError("Overlay permission belum aktif.")
            return
        }
        val cfg = settingsCache.floating
        val (sw, sh) = ScreenMetrics.size(this)
        val width = dp(cfg.panelWidthDp).coerceAtMost(sw)
        val height = dp(cfg.panelHeightDp).coerceAtMost(sh)
        // FIX: FLAG_NOT_TOUCH_MODAL supaya sentuhan di luar panel tetap sampai ke aplikasi di bawah,
        // dan NOT_FOCUSABLE default supaya game/app di bawah tidak kehilangan fokus.
        val params = WindowManager.LayoutParams(
            width,
            height,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cfg.x.coerceIn(0, (sw - width).coerceAtLeast(0))
            y = cfg.y.coerceIn(0, (sh - height).coerceAtLeast(0))
        }
        val view = ComposeView(this).apply {
            owner.install(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { PanelContent() }
        }
        panelView = view
        panelParams = params
        expanded = false
        addView(view, params) { panelView = null; panelParams = null }
        applyVisibility()
        scheduleAutoHide()
    }

    @androidx.compose.runtime.Composable
    private fun PanelContent() {
        val settings by container.settingsRepository.flow.collectAsState(initial = settingsCache)
        val lines by container.sessionStore.lines.collectAsState()
        val input by container.sessionStore.input.collectAsState()
        val busy by container.sessionStore.busy.collectAsState()
        val error by container.sessionStore.error.collectAsState()
        val image by container.sessionStore.currentImage.collectAsState()
        val voice by container.sessionStore.voiceState.collectAsState()
        ALFVisionTheme(settings) {
            FloatingPanel(
                settings = settings,
                tab = panelTab.value,
                onTab = { panelTab.value = it; setPanelFocusable(false) },
                lines = lines,
                input = input,
                busy = busy,
                error = error,
                currentImage = image,
                voiceState = voice,
                actions = buildActions()
            )
        }
    }

    private fun buildActions() = PanelActions(
        onInput = container.sessionStore::setInput,
        onSend = { container.controller.ask(container.sessionStore.input.value) },
        onMinimize = { minimize() },
        onMaximize = { toggleMaximize() },
        onClose = { stopSelf() },
        onCapture = { container.controller.capture() },
        onAnswer = { panelTab.value = PanelTab.CHAT; container.controller.answerScreen() },
        onClearImage = { container.sessionStore.clearImage() },
        onSelectRegion = { showRegionSelector() },
        onClearRegion = { container.sessionStore.setRegion(null) },
        onQuickAction = container.controller::quickAction,
        onCompare = container.controller::compare,
        onRetry = container.controller::retryLast,
        onStop = container.controller::cancelRequest,
        onPin = container.sessionStore::pinCurrent,
        onClear = {
            container.sessionStore.clearChat()
            container.controller.resetConversation()
        },
        onVoice = {
            if (container.sessionStore.voiceState.value == com.alfread.alfvision.vision.VoiceState.LISTENING) {
                container.voiceInputManager.stop()
            } else if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                container.voiceInputManager.start()
            } else {
                container.sessionStore.setError("Izin mikrofon belum diberikan. Buka app > Settings > Permissions.")
            }
        },
        onDrag = { dx, dy -> movePanel(dx, dy) },
        onDragEnd = { snapPanel() },
        onResize = { dx, dy -> resizePanel(dx, dy) },
        onResizeEnd = { persistSize() },
        onOpenApp = { route -> openApp(route) },
        onUpdateSettings = { transform -> scope.launch { container.settingsRepository.update(transform) } },
        onInputTouch = { setPanelFocusable(true) },
        onInputFocus = { focused -> if (!focused) setPanelFocusable(false) },
        onInteract = { scheduleAutoHide() }
    )

    private fun setPanelFocusable(focusable: Boolean) {
        val params = panelParams ?: return
        val view = panelView ?: return
        val currentlyFocusable = (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0
        if (currentlyFocusable == focusable) return
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun toggleMaximize() {
        val params = panelParams ?: return
        val (sw, sh) = ScreenMetrics.size(this)
        if (!expanded) {
            savedPanelSize = params.width to params.height
            params.width = (sw * 0.94f).roundToInt()
            params.height = (sh * 0.84f).roundToInt()
            params.x = ((sw - params.width) / 2).coerceAtLeast(0)
            params.y = ((sh - params.height) / 2).coerceAtLeast(0)
        } else {
            savedPanelSize?.let { (w, h) -> params.width = w; params.height = h }
            clampPanel(params)
        }
        expanded = !expanded
        runCatching { wm.updateViewLayout(panelView, params) }
    }

    private fun minimize() {
        if (minimized) return
        minimized = true
        autoHideJob?.cancel()
        setPanelFocusable(false)
        removeView(panelView)
        panelView = null
        showOrb()
    }

    private fun restorePanel() {
        if (!minimized) return
        minimized = false
        removeView(orbView)
        orbView = null
        // posisi panel mengikuti posisi orb terakhir
        orbParams?.let { o ->
            persistPosition(o.x, o.y)
            settingsCache = settingsCache.copy(floating = settingsCache.floating.copy(x = o.x, y = o.y))
        }
        startPanel()
    }

    private fun movePanel(dx: Float, dy: Float) {
        val params = panelParams ?: return
        val view = panelView ?: return
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        clampPanel(params)
        runCatching { wm.updateViewLayout(view, params) }
    }

    /** Snap ke tepi hanya saat jari dilepas (sebelumnya snap terjadi di tiap gerakan sehingga panel "lengket"). */
    private fun snapPanel() {
        val params = panelParams ?: return
        val view = panelView ?: return
        val (sw, _) = ScreenMetrics.size(this)
        if (settingsCache.floating.snapToEdge && !expanded) {
            val threshold = (sw * 0.12f).roundToInt()
            if (params.x <= threshold) params.x = 0
            else if (params.x + params.width >= sw - threshold) params.x = (sw - params.width).coerceAtLeast(0)
            runCatching { wm.updateViewLayout(view, params) }
        }
        persistPosition(params.x, params.y)
    }

    private fun resizePanel(dx: Float, dy: Float) {
        val params = panelParams ?: return
        val view = panelView ?: return
        val (sw, sh) = ScreenMetrics.size(this)
        params.width = (params.width + dx.roundToInt()).coerceIn(dp(280), dp(520).coerceAtMost(sw))
        params.height = (params.height + dy.roundToInt()).coerceIn(dp(300), dp(820).coerceAtMost(sh))
        clampPanel(params)
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun persistSize() {
        val params = panelParams ?: return
        if (expanded) return
        scope.launch { container.settingsRepository.savePanelSize(pxToDp(params.width), pxToDp(params.height)) }
    }

    private fun clampPanel(params: WindowManager.LayoutParams) {
        val (sw, sh) = ScreenMetrics.size(this)
        params.x = params.x.coerceIn(0, (sw - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (sh - params.height).coerceAtLeast(0))
    }

    // ---------------------------------------------------------------------------------------
    // ORB
    // ---------------------------------------------------------------------------------------

    private fun showOrb() {
        if (orbView != null) return
        if (!Settings.canDrawOverlays(this)) return
        val cfg = settingsCache.floating
        val size = dp(cfg.orbSizeDp)
        val (sw, sh) = ScreenMetrics.size(this)
        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (panelParams?.x ?: cfg.x).coerceIn(0, (sw - size).coerceAtLeast(0))
            y = (panelParams?.y ?: cfg.y).coerceIn(0, (sh - size).coerceAtLeast(0))
        }
        orbParams = params
        val view = ComposeView(this).apply {
            owner.install(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val settings by container.settingsRepository.flow.collectAsState(initial = settingsCache)
                val busy by container.sessionStore.busy.collectAsState()
                ALFVisionTheme(settings) {
                    FloatingOrb(
                        accent = settings.accent.color(),
                        busy = busy,
                        onTap = { restorePanel() },
                        onDoubleTap = {
                            // Double tap orb = jawab soal di layar (capture + jawab dalam satu aksi).
                            restorePanel()
                            panelTab.value = PanelTab.CHAT
                            container.controller.answerScreen()
                        },
                        onLongPress = { showRegionSelector() },
                        onDrag = { dx, dy -> moveOrb(dx, dy) },
                        onDragEnd = { snapOrb() }
                    )
                }
            }
        }
        orbView = view
        addView(view, params) { orbView = null }
        applyVisibility()
    }

    private fun moveOrb(dx: Float, dy: Float) {
        val params = orbParams ?: return
        val view = orbView ?: return
        val (sw, sh) = ScreenMetrics.size(this)
        params.x = (params.x + dx.roundToInt()).coerceIn(0, (sw - params.width).coerceAtLeast(0))
        params.y = (params.y + dy.roundToInt()).coerceIn(0, (sh - params.height).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun snapOrb() {
        val params = orbParams ?: return
        val view = orbView ?: return
        val (sw, _) = ScreenMetrics.size(this)
        if (settingsCache.floating.snapToEdge) {
            params.x = if (params.x + params.width / 2 < sw / 2) 0 else (sw - params.width).coerceAtLeast(0)
            runCatching { wm.updateViewLayout(view, params) }
        }
        persistPosition(params.x, params.y)
    }

    // ---------------------------------------------------------------------------------------
    // REGION SELECTOR
    // ---------------------------------------------------------------------------------------

    private fun defaultRegion(sw: Int, sh: Int) = Region(
        x = sw / 5,
        y = sh / 5,
        width = sw * 3 / 5,
        height = sh * 3 / 5,
        screenWidth = sw,
        screenHeight = sh,
        rotation = currentDisplayRotation()
    )

    fun showRegionSelector() {
        if (regionView != null || !Settings.canDrawOverlays(this)) return
        val (sw, sh) = ScreenMetrics.size(this)
        val existing = container.sessionStore.region.value
        container.sessionStore.setRegion(existing?.normalized(sw, sh) ?: defaultRegion(sw, sh))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val view = ComposeView(this).apply {
            owner.install(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val settings by container.settingsRepository.flow.collectAsState(initial = settingsCache)
                val region by container.sessionStore.region.collectAsState()
                ALFVisionTheme(settings) {
                    region?.let { current ->
                        RegionSelectorOverlay(
                            region = current,
                            accent = settings.accent.color(),
                            onRegionChange = { container.sessionStore.setRegion(it) },
                            onReset = { container.sessionStore.setRegion(defaultRegion(sw, sh)) },
                            onCenter = {
                                container.sessionStore.setRegion(current.copy(x = (sw - current.width) / 2, y = (sh - current.height) / 2))
                            },
                            onFullscreen = {
                                container.sessionStore.setRegion(Region(0, 0, sw, sh, sw, sh, rotation = currentDisplayRotation()))
                            },
                            onApply = { closeRegionSelector() },
                            onSave = { saveRegionPreset() },
                            onClose = { closeRegionSelector() }
                        )
                    }
                }
            }
        }
        regionView = view
        addView(view, params) { regionView = null }
        applyVisibility()
    }

    private fun saveRegionPreset() {
        val region = container.sessionStore.region.value ?: return
        scope.launch {
            val name = "Region ${System.currentTimeMillis().toString().takeLast(4)}"
            container.regionRepository.save(name, region)
            closeRegionSelector()
        }
    }

    private fun closeRegionSelector() {
        removeView(regionView)
        regionView = null
        applyVisibility()
        if (!minimized && panelView == null) startPanel()
    }

    // ---------------------------------------------------------------------------------------
    // CAPTURE SUPPRESSION
    // FIX: sebelumnya view dihapus tetapi referensinya tidak di-null-kan sehingga panel TIDAK PERNAH
    // muncul lagi setelah capture atau setelah region selector ditutup. Sekarang view hanya
    // disembunyikan (INVISIBLE) lalu ditampilkan lagi, state chat/tab tetap utuh.
    // ---------------------------------------------------------------------------------------

    fun setCaptureSuppressed(value: Boolean) {
        captureSuppressed = value
        applyVisibility()
    }

    private fun applyVisibility() {
        val hide = captureSuppressed
        panelView?.visibility = if (hide || regionView != null) View.INVISIBLE else View.VISIBLE
        orbView?.visibility = if (hide) View.INVISIBLE else View.VISIBLE
        regionView?.visibility = if (hide) View.INVISIBLE else View.VISIBLE
    }

    // ---------------------------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------------------------

    private fun scheduleAutoHide() {
        autoHideJob?.cancel()
        val cfg = settingsCache.floating
        if (!cfg.autoHide || minimized) return
        autoHideJob = scope.launch {
            delay(cfg.autoHideMillis)
            if (!minimized && regionView == null) minimize()
        }
    }

    private fun persistPosition(x: Int, y: Int) {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(300)
            container.settingsRepository.savePanelPosition(x, y)
        }
    }

    private fun openApp(route: String?) {
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        if (route != null) intent.putExtra(MainActivity.EXTRA_ROUTE, route)
        runCatching { startActivity(intent) }
    }

    private fun currentDisplayRotation(): Int =
        getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.rotation ?: 0

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun pxToDp(px: Int): Int = (px / resources.displayMetrics.density).roundToInt()

    private fun addView(view: View, params: WindowManager.LayoutParams, onFail: () -> Unit) {
        runCatching { wm.addView(view, params) }.onFailure {
            onFail()
            container.sessionStore.setError("Floating panel gagal tampil. Pastikan Overlay Permission aktif.")
        }
    }

    private fun removeView(view: View?) {
        if (view != null) runCatching { wm.removeView(view) }
    }

    companion object {
        const val ACTION_SHOW = "com.alfread.alfvision.action.SHOW"
        const val ACTION_MINIMIZE = "com.alfread.alfvision.action.MINIMIZE"
        const val ACTION_CLOSE = "com.alfread.alfvision.action.CLOSE"
        const val ACTION_CAPTURE = "com.alfread.alfvision.action.CAPTURE"
        const val ACTION_REGION = "com.alfread.alfvision.action.REGION"
        @Volatile var instance: FloatingPanelService? = null

        fun suppressForCapture(value: Boolean) { instance?.setCaptureSuppressed(value) }
    }
}
