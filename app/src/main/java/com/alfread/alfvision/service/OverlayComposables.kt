package com.alfread.alfvision.service

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.ui.components.DockBar
import com.alfread.alfvision.ui.components.DockItem
import com.alfread.alfvision.ui.components.SliderRow
import com.alfread.alfvision.ui.components.SwitchRow
import com.alfread.alfvision.ui.theme.AlfCyan
import com.alfread.alfvision.ui.theme.brandBrush
import com.alfread.alfvision.ui.theme.color
import com.alfread.alfvision.vision.VoiceState
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PanelTab { CHAT, TOOLS, SETUP }

const val ROUTE_APP = "app"

/** Semua callback panel dikelompokkan di sini supaya signature FloatingPanel tetap rapi. */
class PanelActions(
    val onInput: (String) -> Unit,
    val onSend: () -> Unit,
    val onMinimize: () -> Unit,
    val onMaximize: () -> Unit,
    val onClose: () -> Unit,
    val onCapture: () -> Unit,
    val onSelectRegion: () -> Unit,
    val onClearRegion: () -> Unit,
    val onQuickAction: (String) -> Unit,
    val onCompare: () -> Unit,
    val onRetry: () -> Unit,
    val onStop: () -> Unit,
    val onPin: () -> Unit,
    val onClear: () -> Unit,
    val onVoice: () -> Unit,
    val onDrag: (Float, Float) -> Unit,
    val onDragEnd: () -> Unit,
    val onResize: (Float, Float) -> Unit,
    val onResizeEnd: () -> Unit,
    val onOpenApp: (String?) -> Unit,
    val onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    val onInputTouch: () -> Unit,
    val onInputFocus: (Boolean) -> Unit,
    val onInteract: () -> Unit
)

// ---------------------------------------------------------------------------------------------
// GESTURE HELPERS
// Drag jendela overlay memakai koordinat RAW layar. Memakai detectDragGestures (koordinat lokal)
// membuat panel bergetar karena view-nya ikut bergerak saat dipindah.
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.windowDrag(enabled: Boolean, onDrag: (Float, Float) -> Unit, onEnd: () -> Unit): Modifier = composed {
    val last = remember { floatArrayOf(0f, 0f) }
    if (!enabled) {
        Modifier
    } else {
        Modifier.pointerInteropFilter { event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    last[0] = event.rawX
                    last[1] = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    onDrag(event.rawX - last[0], event.rawY - last[1])
                    last[0] = event.rawX
                    last[1] = event.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onEnd()
                    true
                }
                else -> false
            }
        }
    }
}

private class OrbGestureState {
    var downX = 0f
    var downY = 0f
    var lastX = 0f
    var lastY = 0f
    var moved = false
    var longFired = false
    var lastTapTime = 0L
    var longRunnable: Runnable? = null
    var tapRunnable: Runnable? = null
}

/** Tap, double tap, long press dan drag untuk orb dalam satu handler. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.orbGestures(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
): Modifier = composed {
    val state = remember { OrbGestureState() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val slop = with(LocalDensity.current) { 8.dp.toPx() }
    Modifier.pointerInteropFilter { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                state.downX = event.rawX
                state.downY = event.rawY
                state.lastX = event.rawX
                state.lastY = event.rawY
                state.moved = false
                state.longFired = false
                val runnable = Runnable {
                    if (!state.moved) {
                        state.longFired = true
                        onLongPress()
                    }
                }
                state.longRunnable = runnable
                handler.postDelayed(runnable, 500L)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!state.moved && (abs(event.rawX - state.downX) > slop || abs(event.rawY - state.downY) > slop)) {
                    state.moved = true
                    state.longRunnable?.let { handler.removeCallbacks(it) }
                }
                if (state.moved) {
                    onDrag(event.rawX - state.lastX, event.rawY - state.lastY)
                }
                state.lastX = event.rawX
                state.lastY = event.rawY
                true
            }
            MotionEvent.ACTION_UP -> {
                state.longRunnable?.let { handler.removeCallbacks(it) }
                if (state.moved) {
                    onDragEnd()
                } else if (!state.longFired) {
                    val now = System.currentTimeMillis()
                    val pending = state.tapRunnable
                    if (pending != null && now - state.lastTapTime < 300L) {
                        handler.removeCallbacks(pending)
                        state.tapRunnable = null
                        onDoubleTap()
                    } else {
                        state.lastTapTime = now
                        val runnable = Runnable {
                            state.tapRunnable = null
                            onTap()
                        }
                        state.tapRunnable = runnable
                        handler.postDelayed(runnable, 300L)
                    }
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                state.longRunnable?.let { handler.removeCallbacks(it) }
                if (state.moved) onDragEnd()
                true
            }
            else -> false
        }
    }
}

// ---------------------------------------------------------------------------------------------
// ORB
// ---------------------------------------------------------------------------------------------

@Composable
fun FloatingOrb(
    accent: Color,
    busy: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "orbPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "orbPulseValue"
    )
    val ringAlpha = if (busy) pulse else 0.9f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.95f), AlfCyan.copy(alpha = 0.85f))))
            .border(2.dp, Color.White.copy(alpha = ringAlpha), CircleShape)
            .orbGestures(onTap, onDoubleTap, onLongPress, onDrag, onDragEnd),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Visibility, contentDescription = "Open ALF Vision", tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// PANEL
// ---------------------------------------------------------------------------------------------

private val panelDock = listOf(
    DockItem(PanelTab.CHAT.name, "Chat", Icons.Default.Chat),
    DockItem(PanelTab.TOOLS.name, "Tools", Icons.Default.Dashboard),
    DockItem(PanelTab.SETUP.name, "Setup", Icons.Default.Tune),
    DockItem(ROUTE_APP, "App", Icons.Default.Home)
)

@Composable
fun FloatingPanel(
    settings: AppSettings,
    tab: PanelTab,
    onTab: (PanelTab) -> Unit,
    lines: List<ChatLine>,
    input: String,
    busy: Boolean,
    error: String?,
    currentImage: PendingImage?,
    voiceState: VoiceState,
    actions: PanelActions
) {
    val scheme = MaterialTheme.colorScheme
    val accent = settings.accent.color()
    val shape = RoundedCornerShape(26.dp)
    val opacity = settings.floating.opacity
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(scheme.surface.copy(alpha = opacity), scheme.background.copy(alpha = opacity))))
            .border(1.dp, accent.copy(alpha = 0.4f), shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) actions.onInteract()
                    }
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            PanelHeader(accent, busy, settings.floating.locked, actions)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    PanelTab.CHAT -> ChatPane(settings, lines, input, busy, error, currentImage, voiceState, actions)
                    PanelTab.TOOLS -> ToolsPane(accent, currentImage != null, actions) { onTab(PanelTab.CHAT) }
                    PanelTab.SETUP -> SetupPane(settings, actions)
                }
            }
            Box(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                DockBar(
                    items = panelDock,
                    selectedRoute = tab.name,
                    compact = true,
                    onSelect = { item ->
                        if (item.route == ROUTE_APP) actions.onOpenApp(null)
                        else onTab(PanelTab.valueOf(item.route))
                    }
                )
            }
        }
        // Handle resize di pojok kanan bawah (sebelumnya di kiri bawah sehingga arah drag terbalik).
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(34.dp)
                .windowDrag(!settings.floating.locked, actions.onResize, actions.onResizeEnd),
            contentAlignment = Alignment.BottomEnd
        ) {
            val handleColor = accent.copy(alpha = 0.7f)
            Canvas(Modifier.padding(8.dp).size(14.dp)) {
                drawLine(handleColor, Offset(size.width, 0f), Offset(0f, size.height), 3f)
                drawLine(handleColor, Offset(size.width, size.height * 0.5f), Offset(size.width * 0.5f, size.height), 3f)
            }
        }
    }
}

@Composable
private fun PanelHeader(accent: Color, busy: Boolean, locked: Boolean, actions: PanelActions) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().windowDrag(!locked, actions.onDrag, actions.onDragEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(brandBrush(accent)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("ALF VISION", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (busy) Color(0xFFFFB300) else Color(0xFF2ECC71)))
                    Spacer(Modifier.width(5.dp))
                    Text(if (busy) "ANALYZING" else "READY", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PanelIconButton(Icons.Default.CameraAlt, "Capture", onClick = actions.onCapture)
        PanelIconButton(Icons.Default.OpenInFull, "Maximize", onClick = actions.onMaximize)
        PanelIconButton(Icons.Default.Minimize, "Minimize", onClick = actions.onMinimize)
        PanelIconButton(Icons.Default.Close, "Close", onClick = actions.onClose)
    }
}

@Composable
private fun PanelIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, description, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun ChatPane(
    settings: AppSettings,
    lines: List<ChatLine>,
    input: String,
    busy: Boolean,
    error: String?,
    currentImage: PendingImage?,
    voiceState: VoiceState,
    actions: PanelActions
) {
    val accent = settings.accent.color()
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        if (currentImage != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CropFree, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Image: ${currentImage.label}", Modifier.weight(1f), fontSize = 12.sp)
                TextButton(onClick = actions.onPin) { Text("PIN", fontSize = 12.sp) }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (lines.isEmpty() && error == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("Capture layar lalu tanya apa saja.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(lines, key = { it.id }) { line -> MessageBubble(line, accent) }
            if (error != null) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(8.dp))
                        TextButton(onClick = actions.onRetry) { Text("Retry") }
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp))
        if (!settings.floating.compactMode) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Analyze", "Explain", "Read", "Translate", "Find Error", "Help Me").forEach { action ->
                    AssistChip(onClick = { actions.onQuickAction(action) }, label = { Text(action, fontSize = 11.sp) })
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) actions.onInputTouch()
                    }
                }
            }
        ) {
            TextField(
                value = input,
                onValueChange = actions.onInput,
                modifier = Modifier.fillMaxWidth().onFocusChanged { actions.onInputFocus(it.isFocused) },
                shape = RoundedCornerShape(22.dp),
                minLines = 1,
                maxLines = 4,
                placeholder = { Text("Ask anything...") },
                enabled = !busy,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                leadingIcon = {
                    IconButton(onClick = actions.onVoice) {
                        Icon(if (voiceState == VoiceState.LISTENING) Icons.Default.Stop else Icons.Default.Mic, "Voice input")
                    }
                },
                trailingIcon = {
                    IconButton(onClick = if (busy) actions.onStop else actions.onSend, enabled = busy || input.isNotBlank()) {
                        Icon(if (busy) Icons.Default.Stop else Icons.Default.Send, if (busy) "Stop" else "Send", tint = accent)
                    }
                }
            )
        }
    }
}

@Composable
private fun MessageBubble(line: ChatLine, accent: Color) {
    val isUser = line.role == Role.USER
    val bg = if (isUser) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(color = bg, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 320.dp)) {
            Text(line.content, Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 13.sp)
        }
    }
}

@Composable
private fun ToolsPane(accent: Color, hasImage: Boolean, actions: PanelActions, goChat: () -> Unit) {
    val tools = listOf(
        Triple(Icons.Default.CameraAlt, "Capture", actions.onCapture),
        Triple(Icons.Default.Crop, "Region", actions.onSelectRegion),
        Triple(Icons.Default.CropFree, "Full screen", actions.onClearRegion),
        Triple(Icons.Default.Compare, "Compare", { actions.onCompare(); goChat() }),
        Triple(Icons.Default.Bookmark, "Pin image", actions.onPin),
        Triple(Icons.Default.DeleteSweep, "Clear chat", actions.onClear)
    )
    val quick = listOf(
        Triple(Icons.Default.AutoAwesome, "Analyze", { actions.onQuickAction("Analyze"); goChat() }),
        Triple(Icons.Default.Forum, "Explain", { actions.onQuickAction("Explain"); goChat() }),
        Triple(Icons.Default.Search, "Read", { actions.onQuickAction("Read"); goChat() }),
        Triple(Icons.Default.Translate, "Translate", { actions.onQuickAction("Translate"); goChat() }),
        Triple(Icons.Default.BugReport, "Find Error", { actions.onQuickAction("Find Error"); goChat() }),
        Triple(Icons.Default.FlashOn, "Help Me", { actions.onQuickAction("Help Me"); goChat() })
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("TOOLS", style = MaterialTheme.typography.labelMedium, color = accent, letterSpacing = 1.2.sp)
        tools.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, label, onClick) -> ToolTile(icon, label, accent, Modifier.weight(1f), onClick) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(if (hasImage) "ASK ABOUT IMAGE" else "ASK (tanpa gambar)", style = MaterialTheme.typography.labelMedium, color = accent, letterSpacing = 1.2.sp)
        quick.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, label, onClick) -> ToolTile(icon, label, accent, Modifier.weight(1f), onClick) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ToolTile(icon: ImageVector, label: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SetupPane(settings: AppSettings, actions: PanelActions) {
    val accent = settings.accent.color()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("PANEL", style = MaterialTheme.typography.labelMedium, color = accent, letterSpacing = 1.2.sp)
        SliderRow("Opacity", settings.floating.opacity, 0.55f..1f, format = { "${(it * 100).roundToInt()}%" }) { v ->
            actions.onUpdateSettings { it.copy(floating = it.floating.copy(opacity = v)) }
        }
        SwitchRow("Snap ke tepi", settings.floating.snapToEdge) { c -> actions.onUpdateSettings { it.copy(floating = it.floating.copy(snapToEdge = c)) } }
        SwitchRow("Kunci posisi", settings.floating.locked) { c -> actions.onUpdateSettings { it.copy(floating = it.floating.copy(locked = c)) } }
        SwitchRow("Compact", settings.floating.compactMode) { c -> actions.onUpdateSettings { it.copy(floating = it.floating.copy(compactMode = c)) } }
        SwitchRow("Auto hide", settings.floating.autoHide) { c -> actions.onUpdateSettings { it.copy(floating = it.floating.copy(autoHide = c)) } }
        Text("VISION", style = MaterialTheme.typography.labelMedium, color = accent, letterSpacing = 1.2.sp)
        SwitchRow("Kirim hanya region", settings.vision.sendOnlyRegion) { c -> actions.onUpdateSettings { it.copy(vision = it.vision.copy(sendOnlyRegion = c)) } }
        SwitchRow("Auto analyze", settings.vision.autoAnalyze) { c -> actions.onUpdateSettings { it.copy(vision = it.vision.copy(autoAnalyze = c)) } }
        OutlinedButton(onClick = { actions.onOpenApp("settings") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Buka Settings lengkap")
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// REGION SELECTOR
// ---------------------------------------------------------------------------------------------

internal const val REGION_NONE = 0
internal const val REGION_MOVE = 1
internal const val REGION_TL = 2
internal const val REGION_TR = 3
internal const val REGION_BL = 4
internal const val REGION_BR = 5

private fun clampInt(value: Int, low: Int, high: Int): Int = if (high < low) low else value.coerceIn(low, high)

internal fun hitTestRegion(region: Region, x: Float, y: Float, slop: Float): Int {
    val left = region.x.toFloat()
    val top = region.y.toFloat()
    val right = left + region.width
    val bottom = top + region.height
    fun near(px: Float, py: Float) = abs(x - px) <= slop && abs(y - py) <= slop
    return when {
        near(left, top) -> REGION_TL
        near(right, top) -> REGION_TR
        near(left, bottom) -> REGION_BL
        near(right, bottom) -> REGION_BR
        x in left..right && y in top..bottom -> REGION_MOVE
        else -> REGION_NONE
    }
}

internal fun resizeRegion(region: Region, mode: Int, dx: Float, dy: Float, screenW: Int, screenH: Int, minSize: Int = 80): Region {
    var left = region.x
    var top = region.y
    var right = region.x + region.width
    var bottom = region.y + region.height
    val ix = dx.roundToInt()
    val iy = dy.roundToInt()
    when (mode) {
        REGION_MOVE -> {
            val nx = clampInt(left + ix, 0, screenW - region.width)
            val ny = clampInt(top + iy, 0, screenH - region.height)
            left = nx
            top = ny
            right = nx + region.width
            bottom = ny + region.height
        }
        REGION_TL -> {
            left = clampInt(left + ix, 0, right - minSize)
            top = clampInt(top + iy, 0, bottom - minSize)
        }
        REGION_TR -> {
            right = clampInt(right + ix, left + minSize, screenW)
            top = clampInt(top + iy, 0, bottom - minSize)
        }
        REGION_BL -> {
            left = clampInt(left + ix, 0, right - minSize)
            bottom = clampInt(bottom + iy, top + minSize, screenH)
        }
        REGION_BR -> {
            right = clampInt(right + ix, left + minSize, screenW)
            bottom = clampInt(bottom + iy, top + minSize, screenH)
        }
        else -> return region
    }
    return region.copy(x = left, y = top, width = right - left, height = bottom - top)
}

@Composable
fun RegionSelectorOverlay(
    region: Region,
    accent: Color,
    onRegionChange: (Region) -> Unit,
    onReset: () -> Unit,
    onCenter: () -> Unit,
    onFullscreen: () -> Unit,
    onApply: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    val handleSlop = with(density) { 34.dp.toPx() }
    // FIX: pointerInput sebelumnya di-key ke `region`, sehingga gesture dibatalkan setiap kali region
    // berubah (drag hanya bergerak sedikit). Sekarang key Unit + rememberUpdatedState.
    val currentRegion by rememberUpdatedState(region)
    val currentOnChange by rememberUpdatedState(onRegionChange)
    var mode by remember { mutableIntStateOf(REGION_NONE) }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset -> mode = hitTestRegion(currentRegion, offset.x, offset.y, handleSlop) },
                onDragEnd = { mode = REGION_NONE },
                onDragCancel = { mode = REGION_NONE },
                onDrag = { change, drag ->
                    change.consume()
                    if (mode != REGION_NONE) {
                        val r = currentRegion
                        currentOnChange(resizeRegion(r, mode, drag.x, drag.y, r.screenWidth.coerceAtLeast(1), r.screenHeight.coerceAtLeast(1)))
                    }
                }
            )
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dim = Color.Black.copy(alpha = 0.55f)
            val l = region.x.toFloat()
            val t = region.y.toFloat()
            val r = l + region.width
            val b = t + region.height
            drawRect(dim, Offset(0f, 0f), Size(size.width, t.coerceAtLeast(0f)))
            drawRect(dim, Offset(0f, b), Size(size.width, (size.height - b).coerceAtLeast(0f)))
            drawRect(dim, Offset(0f, t), Size(l.coerceAtLeast(0f), (b - t).coerceAtLeast(0f)))
            drawRect(dim, Offset(r, t), Size((size.width - r).coerceAtLeast(0f), (b - t).coerceAtLeast(0f)))
            drawRect(accent, Offset(l, t), Size(r - l, b - t), style = Stroke(width = 3.dp.toPx()))
            listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { corner ->
                drawCircle(Color.White, radius = 11.dp.toPx(), center = corner)
                drawCircle(accent, radius = 7.dp.toPx(), center = corner)
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.7f)) {
                Text(
                    "AI REGION  ${region.width} × ${region.height}",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Row(
                Modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onReset) { Icon(Icons.Default.Refresh, "Reset") }
                IconButton(onClick = onCenter) { Icon(Icons.Default.CenterFocusStrong, "Center") }
                IconButton(onClick = onFullscreen) { Icon(Icons.Default.Fullscreen, "Fullscreen") }
                IconButton(onClick = onSave) { Icon(Icons.Default.Save, "Save region preset") }
                IconButton(onClick = onApply) { Icon(Icons.Default.Check, "Use region", tint = accent) }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close region selector") }
            }
        }
    }
}
