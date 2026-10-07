package com.alfread.alfvision.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.alfread.alfvision.R
import com.alfread.alfvision.core.*
import com.alfread.alfvision.data.network.GroqError
import com.alfread.alfvision.data.prefs.AppSettings
import com.alfread.alfvision.domain.RegionCalculator
import com.alfread.alfvision.domain.PromptBuilder
import com.alfread.alfvision.domain.AIRequestManager
import com.alfread.alfvision.core.AppContainer
import com.alfread.alfvision.ui.theme.ALFVisionTheme
import com.alfread.alfvision.util.AlfLogger
import com.alfread.alfvision.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlin.math.max

class FloatingPanelService : Service() {
    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var panelView: ComposeView? = null
    private var orbView: ComposeView? = null
    private var selectorView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelExpanded = false
    private var orbParams: WindowManager.LayoutParams? = null
    private var currentSettings by mutableStateOf(AppSettings(
        theme = ThemeMode.DARK,
        accent = AccentColor.BLUE,
        selectedModel = Constants.DEFAULT_MODEL,
        responseStyle = ResponseStyle.NORMAL,
        temperature = 0.7f,
        maxTokens = 2048,
        saveHistory = true,
        saveScreenshots = false,
        sendOnlySelectedRegion = true,
        autoAnalyze = false,
        autoAnalyzeIntervalMs = 5000,
        gamingMode = false,
        captureQuality = 85,
        panelWidth = Constants.PANEL_DEFAULT_WIDTH,
        panelHeight = Constants.PANEL_DEFAULT_HEIGHT,
        panelX = 24,
        panelY = 80,
        orbSize = Constants.ORB_DEFAULT_SIZE,
        opacity = 0.96f,
        snap = true,
        lockPosition = false,
        animation = true,
        autoHide = false,
        blur = true,
        panelStyle = PanelStyle.GLASS,
        timeoutSeconds = 45,
        retryCount = 2,
        debugMode = false,
        autoDelete = AutoDeletePeriod.NEVER,
        activeProfile = "General",
        lastSuccessfulRequest = "Never",
        lastError = "None"
    ))

    private var autoAnalyzeJob: Job? = null
    private lateinit var aiRequestManager: AIRequestManager

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        aiRequestManager = AIRequestManager(AppContainer.groq, serviceScope)
        serviceScope.launch {
            AppContainer.preferences.settings.collectLatest { settings ->
                currentSettings = settings
                AlfLogger.debugEnabled = settings.debugMode
                configureAutoAnalyze(settings)
                panelParams?.let { params ->
                    params.alpha = settings.opacity
                    if (panelView != null) runCatching { windowManager.updateViewLayout(panelView, params) }
                }
            }
        }
        serviceScope.launch {
            VisionEventBus.frames.collectLatest { frame ->
                latestFrame = frame.bitmap
                latestRegion = frame.region
                latestSizeText = "${frame.bitmap.width} × ${frame.bitmap.height}"
            }
        }
        showPanel()
    }

    private var latestFrame: android.graphics.Bitmap? by mutableStateOf(null)
    private var lastResponse: String by mutableStateOf("")
    private var lastError: String by mutableStateOf("")
    private var latestRegion: RegionRect? by mutableStateOf(null)
    private var latestSizeText: String by mutableStateOf("No capture")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!PermissionUtils.overlayGranted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            "show" -> showPanel()
            "show_region_selector" -> showSelector()
            "minimize" -> showOrb()
            "maximize" -> showPanel()
        }
        return START_NOT_STICKY
    }

    private fun configureAutoAnalyze(settings: AppSettings) {
        autoAnalyzeJob?.cancel()
        if (!settings.autoAnalyze) return
        autoAnalyzeJob = serviceScope.launch {
            while (true) {
                delay(settings.autoAnalyzeIntervalMs.toLong())
                if (RegionState.current != null) {
                    CaptureEventBus.request(RegionState.current)
                    delay(450L)
                    latestFrame?.let { analyze("Analyze the current selected region and point out anything important.") }
                }
            }
        }
    }

    fun showPanel() {
        if (!PermissionUtils.overlayGranted(this)) return
        removeSelector()
        removeOrb()
        if (panelView != null) return
        val p = WindowManager.LayoutParams(
            currentSettings.panelWidth,
            currentSettings.panelHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentSettings.panelX
            y = currentSettings.panelY
            alpha = currentSettings.opacity
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        panelParams = p
        panelView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(SimpleLifecycleOwner())
            setContent {
                ALFVisionTheme(settings = currentSettings) {
                    FloatingPanelContent(
                        settings = currentSettings,
                        frame = latestFrame,
                        sizeText = latestSizeText,
                        response = lastResponse,
                        error = lastError,
                        voiceText = lastVoiceText.value,
                        onMinimize = { showOrb() },
                        onToggleMaximize = { toggleMaximize() },
                        onClose = { stopSelf() },
                        onMove = { dx, dy -> if (!currentSettings.lockPosition) movePanel(dx, dy) },
                        onResize = { dw, dh -> resizePanel(dw, dh) },
                        onSelectRegion = { showSelector() },
                        onCapture = { CaptureEventBus.request(RegionState.current) },
                        onStopCapture = {
                            startService(Intent(this@FloatingPanelService, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
                        },
                        onAsk = { prompt, action -> analyze(prompt, action) },
                        onCopy = { copyToClipboard(it) },
                        onVoice = { startVoice { text -> serviceScope.launch { lastVoiceText.value = text } } },
                        onQuickAction = { action -> analyze(PromptBuilder.quickAction(action), action) }
                    )
                }
            }
        }
        windowManager.addView(panelView, p)
        serviceScope.launch { AppContainer.preferences.setPanelPosition(p.x, p.y) }
        if (currentSettings.autoHide) scheduleAutoHide()
    }

    private val lastVoiceText = mutableStateOf("")

    private fun analyze(prompt: String, action: String? = null) {
        val image = latestFrame ?: return
        lastError = ""
        lastResponse = "Requesting…"
        serviceScope.launch {
            val imagePath = if (currentSettings.saveScreenshots) {
                runCatching {
                    val prepared = AppContainer.imageProcessor.prepare(image, currentSettings.captureQuality)
                    val path = AppContainer.history.saveScreenshot(this@FloatingPanelService, prepared.bytes, prompt)
                    prepared.bitmap.recycle()
                    path
                }.getOrNull()
            } else null
            val profile = AppContainer.profiles.get(currentSettings.activeProfile)
            aiRequestManager.start(
                prompt = prompt.ifBlank { "Analyze this screen area." },
                systemPrompt = profile?.systemPrompt,
                images = listOf(image),
                model = profile?.preferredModel ?: currentSettings.selectedModel,
                style = currentSettings.responseStyle
            ) { response ->
                response.onSuccess { output ->
                    lastError = ""
                    lastResponse = output.result.text
                    if (currentSettings.saveHistory) {
                        serviceScope.launch {
                            val conversationId = AppContainer.history.ensureConversation(
                                "Vision ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                                currentSettings.activeProfile
                            )
                            AppContainer.history.addMessage(conversationId, "user", prompt, output.result.model, imagePath = imagePath, regionJson = latestRegion?.toString())
                            AppContainer.history.addMessage(
                                conversationId, "assistant", output.result.text, output.result.model,
                                inputTokens = output.result.inputTokens, outputTokens = output.result.outputTokens
                            )
                        }
                    }
                }.onFailure { error ->
                    lastError = error.message ?: "Request failed."
                    lastResponse = ""
                    AlfLogger.e("AI request failed", error)
                }
                refreshPanel()
            }
        }
    }

    private fun refreshPanel() {
        panelView?.post { panelView?.invalidate() }
    }

    private fun copyToClipboard(text: String) {
        val manager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        manager.setPrimaryClip(android.content.ClipData.newPlainText("ALF AI response", text))
    }

    private fun startVoice(onResult: (String) -> Unit) {
        if (!PermissionUtils.microphoneGranted(this)) return
        val recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { recognizer.destroy() }
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onResult(text)
                recognizer.destroy()
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)
    }

    private fun toggleMaximize() {
        val p = panelParams ?: return
        val metrics = resources.displayMetrics
        if (!panelExpanded) {
            p.x = 12
            p.y = 36
            p.width = (metrics.widthPixels - 24).coerceAtLeast(Constants.PANEL_MIN_WIDTH)
            p.height = (metrics.heightPixels - 72).coerceAtLeast(Constants.PANEL_MIN_HEIGHT)
            panelExpanded = true
        } else {
            p.width = currentSettings.panelWidth
            p.height = currentSettings.panelHeight
            p.x = currentSettings.panelX
            p.y = currentSettings.panelY
            panelExpanded = false
        }
        windowManager.updateViewLayout(panelView, p)
    }

    private fun movePanel(dx: Float, dy: Float) {
        val p = panelParams ?: return
        p.x += dx.toInt()
        p.y += dy.toInt()
        if (currentSettings.snap) {
            val sw = resources.displayMetrics.widthPixels
            if (p.x < 28) p.x = 0
            if (p.x > sw - p.width - 28) p.x = (sw - p.width).coerceAtLeast(0)
            if (p.y < 28) p.y = 0
        }
        windowManager.updateViewLayout(panelView, p)
        panelView?.postDelayed({ serviceScope.launch { AppContainer.preferences.setPanelPosition(p.x, p.y) } }, 180)
    }

    private fun resizePanel(dw: Float, dh: Float) {
        val p = panelParams ?: return
        val metrics = resources.displayMetrics
        p.width = (p.width + dw.toInt()).coerceIn(Constants.PANEL_MIN_WIDTH, metrics.widthPixels)
        p.height = (p.height + dh.toInt()).coerceIn(Constants.PANEL_MIN_HEIGHT, metrics.heightPixels)
        windowManager.updateViewLayout(panelView, p)
        panelView?.postDelayed({ serviceScope.launch { AppContainer.preferences.setPanelSize(p.width, p.height) } }, 180)
    }

    private fun showOrb() {
        removePanel()
        removeSelector()
        if (orbView != null) return
        val p = WindowManager.LayoutParams(
            currentSettings.orbSize,
            currentSettings.orbSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentSettings.panelX.coerceAtLeast(0)
            y = currentSettings.panelY.coerceAtLeast(0)
            alpha = 1f
        }
        orbParams = p
        orbView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(SimpleLifecycleOwner())
            setContent {
                ALFVisionTheme(settings = currentSettings) {
                    FloatingOrbContent(
                        onTap = { showPanel() },
                        onDoubleTap = { CaptureEventBus.request(RegionState.current) },
                        onLongPress = { showPanel() },
                        onMove = { dx, dy ->
                            p.x += dx.toInt(); p.y += dy.toInt()
                            val sw = resources.displayMetrics.widthPixels
                            if (p.x < -p.width / 2 || p.x > sw - p.width / 2) {
                                removeOrb()
                            } else {
                                windowManager.updateViewLayout(orbView, p)
                                serviceScope.launch { AppContainer.preferences.setPanelPosition(p.x, p.y) }
                            }
                        }
                    )
                }
            }
        }
        windowManager.addView(orbView, p)
    }

    private fun showSelector() {
        removeSelector()
        removePanel()
        removeOrb()
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            dimAmount = 0f
        }
        selectorView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(SimpleLifecycleOwner())
            setContent {
                ALFVisionTheme(settings = currentSettings) {
                    RegionSelectorContent(
                        initial = RegionState.current,
                        onCancel = { showPanel() },
                        onDone = { region -> RegionState.current = region; showPanel() },
                        onSave = { name, region ->
                            serviceScope.launch { AppContainer.regions.save(name, region) }
                        },
                        onFullscreen = { sourceW, sourceH ->
                            RegionState.current = RegionRect(0, 0, sourceW, sourceH, sourceW, sourceH)
                        },
                        onReset = { sourceW, sourceH ->
                            RegionState.current = RegionRect(
                                sourceW / 10,
                                sourceH / 8,
                                (sourceW * 0.8f).toInt(),
                                (sourceH * 0.65f).toInt(),
                                sourceW,
                                sourceH
                            )
                        }
                    )
                }
            }
        }
        windowManager.addView(selectorView, p)
    }

    private fun scheduleAutoHide() {
        panelView?.postDelayed({
            panelParams?.let {
                it.alpha = 0.35f
                panelView?.let { view -> windowManager.updateViewLayout(view, it) }
            }
        }, 30_000L)
    }

    private fun removePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        panelParams = null
    }

    private fun removeOrb() {
        orbView?.let { runCatching { windowManager.removeView(it) } }
        orbView = null
        orbParams = null
    }

    private fun removeSelector() {
        selectorView?.let { runCatching { windowManager.removeView(it) } }
        selectorView = null
    }

    override fun onDestroy() {
        autoAnalyzeJob?.cancel()
        removePanel(); removeOrb(); removeSelector()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
private fun FloatingPanelContent(
    settings: AppSettings,
    frame: android.graphics.Bitmap?,
    sizeText: String,
    response: String,
    error: String,
    voiceText: String,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onSelectRegion: () -> Unit,
    onCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onAsk: (String, String?) -> Unit,
    onCopy: (String) -> Unit,
    onVoice: () -> Unit,
    onQuickAction: (String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    val image = frame
    LaunchedEffect(voiceText) { if (voiceText.isNotBlank()) prompt = voiceText }
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = settings.opacity))
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectDragGestures { _, drag -> onMove(drag.x, drag.y) } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("ALF VISION", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("AI Screen Assistant", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onMinimize) { Icon(Icons.Default.Remove, "Minimize") }
                IconButton(onClick = onToggleMaximize) { Icon(Icons.Default.Fullscreen, "Maximize") }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                SmallAction("REGION", Icons.Default.Crop, onSelectRegion)
                SmallAction("CAPTURE", Icons.Default.CameraAlt, onCapture)
                SmallAction("STOP", Icons.Default.Stop, onStopCapture)
            }
            if (settings.gamingMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    FilledTonalButton(onClick = { onQuickAction("Analyze") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("AI", fontSize = 11.sp) }
                    FilledTonalButton(onClick = onCapture, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("CAPTURE", fontSize = 11.sp) }
                    FilledTonalButton(onClick = { onQuickAction("Help Me") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("ASK", fontSize = 11.sp) }
                }
            }

            if (image != null) {
                Box(
                    Modifier.fillMaxWidth().height(155.dp).padding(top = 8.dp)
                        .clip(RoundedCornerShape(14.dp)).background(ComposeColor.Black)
                ) {
                    androidx.compose.foundation.Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Text(sizeText, Modifier.align(Alignment.BottomEnd).padding(5.dp), color = ComposeColor.White, fontSize = 10.sp)
                }
            } else {
                Box(Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CenterFocusStrong, null, modifier = Modifier.size(28.dp))
                        Text("Capture a selected region", fontSize = 12.sp)
                    }
                }
            }

            Text("Quick AI", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(listOf("Analyze", "Explain", "Read", "Translate", "Summarize", "Find Error", "Extract Text", "Describe", "Help Me")) { action ->
                    SuggestionChip(onClick = { onQuickAction(action) }, label = { Text(action, fontSize = 11.sp) })
                }
            }

            if (response.isNotBlank() || error.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("ALF AI", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        if (error.isNotBlank()) Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        if (response.isNotBlank()) {
                            Text(response, fontSize = 12.sp)
                            Row { TextButton(onClick = { onCopy(response) }) { Text("Copy") } }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Ask anything", fontSize = 12.sp) },
                    maxLines = 4,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
                IconButton(onClick = onVoice) { Icon(Icons.Default.Mic, "Voice input") }
                FilledIconButton(
                    onClick = { onAsk(prompt, null) },
                    enabled = prompt.isNotBlank() && image != null
                ) { Icon(Icons.Default.Send, "Send") }
            }

            Box(Modifier.fillMaxWidth().height(12.dp).pointerInput(Unit) { detectDragGestures { _, drag -> onResize(drag.x, drag.y) } }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(34.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        Icon(icon, null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp)
    }
}

@Composable
private fun FloatingOrbContent(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onMove: (Float, Float) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
            .pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onMove(drag.x, drag.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Visibility, "Open ALF Vision Panel", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun RegionSelectorContent(
    initial: RegionRect?,
    onCancel: () -> Unit,
    onDone: (RegionRect) -> Unit,
    onSave: (String, RegionRect) -> Unit,
    onFullscreen: (Int, Int) -> Unit,
    onReset: (Int, Int) -> Unit
) {
    var sourceW by remember { mutableIntStateOf(0) }
    var sourceH by remember { mutableIntStateOf(0) }
    var rect by remember { mutableStateOf(initial) }
    var name by remember { mutableStateOf("") }
    var locked by remember { mutableStateOf(false) }
    var hideBorder by remember { mutableStateOf(false) }
    var opacity by remember { mutableFloatStateOf(1f) }

    BoxWithConstraints(Modifier.fillMaxSize().background(ComposeColor.Black.copy(alpha = 0.14f))) {
        sourceW = constraints.maxWidth
        sourceH = constraints.maxHeight
        if (rect == null && sourceW > 0 && sourceH > 0) {
            rect = RegionRect(sourceW / 10, sourceH / 8, sourceW * 4 / 5, sourceH * 3 / 5, sourceW, sourceH)
        } else if (rect != null && sourceW > 0 && sourceH > 0 && (rect!!.sourceWidth != sourceW || rect!!.sourceHeight != sourceH)) {
            rect = RegionCalculator.constrain(rect!!.x, rect!!.y, rect!!.width, rect!!.height, sourceW, sourceH)
        }
        val current = rect ?: return@BoxWithConstraints

        val regionColor = MaterialTheme.colorScheme.primary
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(sourceW, sourceH, current, locked) {
                    var activeHandle: RegionHandle? = null
                    detectDragGestures(
                        onDragStart = { position ->
                            if (!locked) {
                                val threshold = 42f
                                val handles = listOf(
                                RegionHandle.TOP_LEFT to androidx.compose.ui.geometry.Offset(current.x.toFloat(), current.y.toFloat()),
                                RegionHandle.TOP_RIGHT to androidx.compose.ui.geometry.Offset((current.x + current.width).toFloat(), current.y.toFloat()),
                                RegionHandle.BOTTOM_LEFT to androidx.compose.ui.geometry.Offset(current.x.toFloat(), (current.y + current.height).toFloat()),
                                RegionHandle.BOTTOM_RIGHT to androidx.compose.ui.geometry.Offset((current.x + current.width).toFloat(), (current.y + current.height).toFloat())
                            )
                                activeHandle = handles.minByOrNull { (it.second - position).getDistance() }
                                    ?.takeIf { (it.second - position).getDistance() <= threshold }
                                    ?.first
                            }
                        },
                        onDrag = { change, drag ->
                            if (!locked) {
                                change.consume()
                                val h = activeHandle
                                val updated = when (h) {
                                RegionHandle.TOP_LEFT -> RegionCalculator.constrain(current.x + drag.x.toInt(), current.y + drag.y.toInt(), current.width - drag.x.toInt(), current.height - drag.y.toInt(), sourceW, sourceH)
                                RegionHandle.TOP_RIGHT -> RegionCalculator.constrain(current.x, current.y + drag.y.toInt(), current.width + drag.x.toInt(), current.height - drag.y.toInt(), sourceW, sourceH)
                                RegionHandle.BOTTOM_LEFT -> RegionCalculator.constrain(current.x + drag.x.toInt(), current.y, current.width - drag.x.toInt(), current.height + drag.y.toInt(), sourceW, sourceH)
                                RegionHandle.BOTTOM_RIGHT -> RegionCalculator.constrain(current.x, current.y, current.width + drag.x.toInt(), current.height + drag.y.toInt(), sourceW, sourceH)
                                null -> RegionCalculator.constrain(current.x + drag.x.toInt(), current.y + drag.y.toInt(), current.width, current.height, sourceW, sourceH)
                            }
                                rect = updated
                            }
                        },
                        onDragEnd = { activeHandle = null }
                    )
                }
        ) {
            if (!hideBorder) {
                drawRoundRect(
                    color = regionColor.copy(alpha = opacity.coerceIn(0.15f, 1f)),
                    topLeft = androidx.compose.ui.geometry.Offset(current.x.toFloat(), current.y.toFloat()),
                    size = androidx.compose.ui.geometry.Size(current.width.toFloat(), current.height.toFloat()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )
            }
            val handle = 22f
            listOf(
                androidx.compose.ui.geometry.Offset(current.x.toFloat(), current.y.toFloat()),
                androidx.compose.ui.geometry.Offset((current.x + current.width).toFloat(), current.y.toFloat()),
                androidx.compose.ui.geometry.Offset(current.x.toFloat(), (current.y + current.height).toFloat()),
                androidx.compose.ui.geometry.Offset((current.x + current.width).toFloat(), (current.y + current.height).toFloat())
            ).forEach { center ->
                drawCircle(regionColor.copy(alpha = opacity.coerceIn(0.2f, 1f)), handle / 2, center)
                drawCircle(ComposeColor.White.copy(alpha = opacity.coerceIn(0.2f, 1f)), handle / 2, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ) {
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SCREEN VISION REGION", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${current.width} × ${current.height}  •  X ${current.x}  Y ${current.y}", fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = {
                        onReset(sourceW, sourceH)
                        rect = RegionRect(sourceW / 10, sourceH / 8, (sourceW * 0.8f).toInt(), (sourceH * 0.65f).toInt(), sourceW, sourceH)
                    }) { Text("Reset", fontSize = 10.sp) }
                    OutlinedButton(onClick = {
                        onFullscreen(sourceW, sourceH)
                        rect = RegionRect(0, 0, sourceW, sourceH, sourceW, sourceH)
                    }) { Text("Fullscreen", fontSize = 10.sp) }
                    OutlinedButton(onClick = {
                        rect = RegionRect(
                            ((sourceW - current.width) / 2).coerceAtLeast(0),
                            ((sourceH - current.height) / 2).coerceAtLeast(0),
                            current.width,
                            current.height,
                            sourceW,
                            sourceH
                        )
                    }) { Text("Center", fontSize = 10.sp) }
                    Button(onClick = { onDone(current) }) { Text("Done", fontSize = 10.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = locked,
                        onClick = { locked = !locked },
                        label = { Text(if (locked) "Locked" else "Lock") },
                        leadingIcon = { Icon(if (locked) Icons.Default.Lock else Icons.Default.LockOpen, null, modifier = Modifier.size(15.dp)) }
                    )
                    FilterChip(
                        selected = hideBorder,
                        onClick = { hideBorder = !hideBorder },
                        label = { Text(if (hideBorder) "Border Off" else "Border On") },
                        leadingIcon = { Icon(Icons.Default.BorderStyle, null, modifier = Modifier.size(15.dp)) }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Opacity", fontSize = 10.sp)
                    Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.2f..1f, modifier = Modifier.weight(1f))
                    Text("${(opacity * 100).toInt()}%", fontSize = 10.sp)
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.width(150.dp), singleLine = true, label = { Text("Preset") })
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), current) }) {
                    Icon(Icons.Default.BookmarkAdd, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp)); Text("Save")
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

private class SimpleLifecycleOwner : androidx.lifecycle.LifecycleOwner {
    private val registry = androidx.lifecycle.LifecycleRegistry(this)
    init { registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
}
