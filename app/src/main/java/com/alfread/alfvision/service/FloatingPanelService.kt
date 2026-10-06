package com.alfread.alfvision.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.alfread.alfvision.AlfVisionApplication
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.ui.theme.ALFVisionTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

class FloatingPanelService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private lateinit var container: com.alfread.alfvision.AppContainer
    private var panelView: ComposeView? = null
    private var orbView: ComposeView? = null
    private var regionView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var orbParams: WindowManager.LayoutParams? = null
    private var regionParams: WindowManager.LayoutParams? = null
    private var selectorRegion: Region? = null
    private var minimized = false
    private var expanded = false
    private var savedPanelSize: Pair<Int, Int>? = null
    private var captureSuppressed = false
    private var autoHideJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = (application as AlfVisionApplication).container
        wm = getSystemService(WindowManager::class.java)
        startPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> if (minimized) restorePanel()
            ACTION_MINIMIZE -> minimize()
            ACTION_CLOSE -> stopSelf()
            ACTION_CAPTURE -> container.controller.capture(selectorRegion)
            ACTION_REGION -> showRegionSelector()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        autoHideJob?.cancel()
        removeView(panelView)
        removeView(orbView)
        removeView(regionView)
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPanel() {
        if (!Settings.canDrawOverlays(this)) return
        val current = runBlockingOrDefault()
        val width = dp(current.floating.panelWidthDp)
        val height = dp(current.floating.panelHeightDp)
        panelParams = WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            x = current.floating.x
            y = current.floating.y
        }
        panelView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val settings by container.settingsRepository.flow.collectAsState(initial = current)
                val lines by container.sessionStore.lines.collectAsState()
                val input by container.sessionStore.input.collectAsState()
                val busy by container.sessionStore.busy.collectAsState()
                val error by container.sessionStore.error.collectAsState()
                val image by container.sessionStore.currentImage.collectAsState()
                val voice by container.sessionStore.voiceState.collectAsState()
                ALFVisionTheme(settings) {
                    FloatingPanel(
                        settings = settings,
                        lines = lines,
                        input = input,
                        busy = busy,
                        error = error,
                        currentImage = image,
                        voiceState = voice,
                        onInput = container.sessionStore::setInput,
                        onSend = { container.controller.ask(container.sessionStore.input.value) },
                        onMinimize = { minimize() },
                        onMaximize = { toggleMaximize() },
                        onClose = { stopSelf() },
                        onCapture = { container.controller.capture(selectorRegion) },
                        onSelectRegion = { showRegionSelector() },
                        onQuickAction = container.controller::quickAction,
                        onCompare = container.controller::compare,
                        onRetry = container.controller::retryLast,
                        onStop = container.controller::cancelRequest,
                        onPin = container.sessionStore::pinCurrent,
                        onClear = container.sessionStore::clearChat,
                        onVoice = {
                            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) container.voiceInputManager.start()
                            else container.sessionStore.setError("Microphone permission belum diberikan. Buka Permission Center dari aplikasi.")
                        },
                        onDrag = { dx, dy -> movePanel(dx, dy) },
                        onResize = { dx, dy -> resizePanel(dx, dy) },
                        onOpenApp = {
                            startActivity(Intent(this@FloatingPanelService, com.alfread.alfvision.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    )
                }
            }
        }
        addView(panelView, panelParams!!)
        scheduleAutoHide(current)
    }

    private fun showOrb() {
        if (!Settings.canDrawOverlays(this)) return
        removeView(orbView)
        val settings = runBlockingOrDefault()
        val size = dp(settings.floating.orbSizeDp)
        orbParams = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = panelParams?.x ?: settings.floating.x
            y = panelParams?.y ?: settings.floating.y
        }
        orbView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ALFVisionTheme(settings) {
                    FloatingOrb(
                        accent = androidx.compose.ui.graphics.Color(settings.accent.argb.toULong()),
                        onTap = { restorePanel() },
                        onDoubleTap = { container.controller.capture(selectorRegion) },
                        onLongPress = { showRegionSelector() },
                        onDrag = { dx, dy -> moveOrb(dx, dy) }
                    )
                }
            }
        }
        addView(orbView, orbParams!!)
    }

    private fun toggleMaximize() {
        val params = panelParams ?: return
        val metrics = resources.displayMetrics
        if (!expanded) {
            savedPanelSize = params.width to params.height
            params.width = (metrics.widthPixels * 0.92f).roundToInt()
            params.height = (metrics.heightPixels * 0.84f).roundToInt()
            params.x = ((metrics.widthPixels - params.width) / 2).coerceAtLeast(0)
            params.y = ((metrics.heightPixels - params.height) / 2).coerceAtLeast(0)
        } else {
            savedPanelSize?.let { (w, h) -> params.width = w; params.height = h }
        }
        expanded = !expanded
        runCatching { wm.updateViewLayout(panelView, params) }
    }

    private fun minimize() {
        if (minimized) return
        minimized = true
        removeView(panelView)
        panelView = null
        showOrb()
    }

    private fun restorePanel() {
        if (!minimized) return
        minimized = false
        removeView(orbView)
        orbView = null
        startPanel()
    }

    private fun movePanel(dx: Float, dy: Float) {
        if (captureSuppressed || panelParams == null) return
        val params = panelParams!!
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        clampPanel(params)
        snapPanel(params)
        runCatching { wm.updateViewLayout(panelView, params) }
        persistPosition(params.x, params.y)
    }

    private fun resizePanel(dx: Float, dy: Float) {
        if (panelParams == null) return
        val params = panelParams!!
        params.width = (params.width + dx.roundToInt()).coerceIn(dp(280), dp(520))
        params.height = (params.height + dy.roundToInt()).coerceIn(dp(300), dp(820))
        runCatching { wm.updateViewLayout(panelView, params) }
        persistSize(params.width, params.height)
    }

    private fun moveOrb(dx: Float, dy: Float) {
        if (orbParams == null) return
        val params = orbParams!!
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        clampOrb(params)
        snapOrb(params)
        runCatching { wm.updateViewLayout(orbView, params) }
        persistPosition(params.x, params.y)
    }

    private fun clampPanel(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        params.x = params.x.coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (metrics.heightPixels - params.height).coerceAtLeast(0))
    }

    private fun clampOrb(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        params.x = params.x.coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (metrics.heightPixels - params.height).coerceAtLeast(0))
    }

    private fun snapPanel(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val threshold = (metrics.widthPixels * 0.12f).roundToInt()
        val current = runBlockingOrDefault()
        if (!current.floating.snapToEdge) return
        if (params.x <= threshold) params.x = 0
        else if (params.x + params.width >= metrics.widthPixels - threshold) params.x = (metrics.widthPixels - params.width).coerceAtLeast(0)
    }

    private fun snapOrb(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val threshold = (metrics.widthPixels * 0.14f).roundToInt()
        val current = runBlockingOrDefault()
        if (!current.floating.snapToEdge) return
        if (params.x <= threshold) params.x = 0
        else if (params.x + params.width >= metrics.widthPixels - threshold) params.x = (metrics.widthPixels - params.width).coerceAtLeast(0)
    }


    private fun scheduleAutoHide(settings: AppSettings) {
        autoHideJob?.cancel()
        if (!settings.floating.autoHide) return
        autoHideJob = scope.launch {
            delay(settings.floating.autoHideMillis)
            if (!minimized) minimize()
        }
    }

    fun showRegionSelector() {
        if (regionView != null || !Settings.canDrawOverlays(this)) return
        removeView(panelView)
        val metrics = resources.displayMetrics
        selectorRegion = selectorRegion ?: Region(
            x = metrics.widthPixels / 5,
            y = metrics.heightPixels / 5,
            width = metrics.widthPixels * 3 / 5,
            height = metrics.heightPixels * 3 / 5,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            rotation = display?.rotation ?: 0
        )
        regionParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        regionView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ALFVisionTheme(runBlockingOrDefault()) {
                    selectorRegion?.let { currentRegion ->
                        RegionSelectorOverlay(
                            region = currentRegion,
                            onRegionChange = { selectorRegion = it.copy(screenWidth = metrics.widthPixels, screenHeight = metrics.heightPixels) },
                            onReset = {
                                selectorRegion = Region(metrics.widthPixels / 5, metrics.heightPixels / 5, metrics.widthPixels * 3 / 5, metrics.heightPixels * 3 / 5, metrics.widthPixels, metrics.heightPixels, rotation = display?.rotation ?: 0)
                            },
                            onCenter = {
                                selectorRegion = selectorRegion?.let { r -> r.copy(x = (metrics.widthPixels - r.width) / 2, y = (metrics.heightPixels - r.height) / 2) }
                            },
                            onFullscreen = { selectorRegion = Region(0, 0, metrics.widthPixels, metrics.heightPixels, metrics.widthPixels, metrics.heightPixels, rotation = display?.rotation ?: 0) },
                            onSave = { saveRegionPreset() },
                            onClose = { closeRegionSelector() }
                        )
                    }
                }
            }
        }
        addView(regionView, regionParams!!)
    }

    private fun saveRegionPreset() {
        val region = selectorRegion ?: return
        scope.launch {
            val name = "Region ${System.currentTimeMillis().toString().takeLast(4)}"
            container.regionRepository.save(name, region)
            container.sessionStore.setRegion(region)
            closeRegionSelector()
        }
    }

    private fun closeRegionSelector() {
        removeView(regionView)
        regionView = null
        if (!minimized && panelView == null) startPanel()
    }

    fun setCaptureSuppressed(value: Boolean) {
        captureSuppressed = value
        if (value) {
            removeView(regionView)
            regionView = null
            removeView(panelView)
            removeView(orbView)
        } else if (minimized) {
            if (orbView == null) showOrb()
        } else if (panelView == null) {
            startPanel()
        }
    }

    private fun persistPosition(x: Int, y: Int) { scope.launch { container.settingsRepository.savePanelPosition(x, y) } }
    private fun persistSize(w: Int, h: Int) { scope.launch { container.settingsRepository.savePanelSize(dpToValue(w), dpToValue(h)) } }

    private fun runBlockingOrDefault(): AppSettings = runCatching { kotlinx.coroutines.runBlocking { container.settingsRepository.flow.first() } }.getOrDefault(AppSettings())
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun dpToValue(px: Int): Int = (px / resources.displayMetrics.density).roundToInt()

    private fun addView(view: ComposeView?, params: WindowManager.LayoutParams) {
        if (view == null) return
        runCatching { wm.addView(view, params) }.onFailure { sessionError(it) }
    }

    private fun removeView(view: ComposeView?) { if (view != null) runCatching { wm.removeView(view) } }

    private fun sessionError(t: Throwable) { container.sessionStore.setError("Floating panel gagal tampil. Pastikan Overlay Permission aktif.") }

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
