package com.alfread.alfvision.service

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.vision.VoiceState
import kotlin.math.roundToInt

@Composable
fun FloatingOrb(
    accent: ComposeColor,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(2.dp, accent.copy(alpha = 0.85f), CircleShape)
            .pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) { detectDragGestures { _, drag -> onDrag(drag.x, drag.y) } },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Visibility, contentDescription = "Open ALF Vision", tint = accent, modifier = Modifier.size(30.dp))
    }
}

@Composable
fun FloatingPanel(
    settings: AppSettings,
    lines: List<ChatLine>,
    input: String,
    busy: Boolean,
    error: String?,
    currentImage: PendingImage?,
    voiceState: VoiceState,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    onCapture: () -> Unit,
    onSelectRegion: () -> Unit,
    onQuickAction: (String) -> Unit,
    onCompare: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onPin: () -> Unit,
    onClear: () -> Unit,
    onVoice: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onOpenApp: () -> Unit
) {
    val accent = ComposeColor(settings.accent.argb.toULong())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(settings.floating.opacity)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(settings.floating.locked) {
                    if (!settings.floating.locked) detectDragGestures { _, drag -> onDrag(drag.x, drag.y) }
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null, tint = accent)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("ALF VISION", style = MaterialTheme.typography.labelLarge)
                Text(if (busy) "ANALYZING" else "READY", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCapture) { Icon(Icons.Default.CameraAlt, "Capture") }
            IconButton(onClick = onMaximize) { Icon(Icons.Default.OpenInFull, "Maximize") }
            IconButton(onClick = onMinimize) { Icon(Icons.Default.Minimize, "Minimize") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
        }

        if (currentImage != null) {
            Surface(tonalElevation = 2.dp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CropFree, null, tint = accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Image: ${currentImage.label}", Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = onPin) { Text("PIN") }
                }
            }
        }

        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = onSelectRegion, label = { Text("Region") }, leadingIcon = { Icon(Icons.Default.Crop, null) })
            AssistChip(onClick = onCompare, label = { Text("Compare") }, leadingIcon = { Icon(Icons.Default.Compare, null) })
            AssistChip(onClick = onClear, label = { Text("Clear") }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null) })
        }

        Row(Modifier.padding(horizontal = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Analyze", "Explain", "Read", "Translate", "Find Error", "Help Me").forEach { action ->
                FilterChip(selected = false, onClick = { onQuickAction(action) }, label = { Text(action, fontSize = 10.sp) })
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            reverseLayout = false,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines, key = { it.id }) { line ->
                MessageBubble(line, accent)
            }
            if (error != null) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(6.dp))
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
        }

        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
            TextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4,
                placeholder = { Text("Ask anything...") },
                enabled = !busy,
                leadingIcon = {
                    IconButton(onClick = onVoice) {
                        Icon(if (voiceState == VoiceState.LISTENING) Icons.Default.Stop else Icons.Default.Mic, "Voice input")
                    }
                },
                trailingIcon = {
                    IconButton(onClick = if (busy) onStop else onSend, enabled = busy || input.isNotBlank()) {
                        Icon(if (busy) Icons.Default.Stop else Icons.Default.Send, if (busy) "Stop" else "Send")
                    }
                }
            )
        }
        Box(Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, bottom = 6.dp)) {
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenApp) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(4.dp)); Text("APP") }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(26.dp)
                    .pointerInput(settings.floating.locked) {
                        if (!settings.floating.locked) detectDragGestures { change, drag -> change.consume(); onResize(drag.x, drag.y) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DragHandle, contentDescription = "Resize panel", modifier = Modifier.size(18.dp), tint = accent.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun MessageBubble(line: ChatLine, accent: ComposeColor) {
    val isUser = line.role == Role.USER
    val bg = if (isUser) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(color = bg, shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 310.dp)) {
            Text(line.content, Modifier.padding(10.dp), fontSize = 13.sp)
        }
    }
}

@Composable
fun RegionSelectorOverlay(
    region: Region,
    onRegionChange: (Region) -> Unit,
    onReset: () -> Unit,
    onCenter: () -> Unit,
    onFullscreen: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val handle = 24.dp
    val density = LocalDensity.current
    val screenW = region.screenWidth.coerceAtLeast(1)
    val screenH = region.screenHeight.coerceAtLeast(1)
    val x = region.x.toFloat()
    val y = region.y.toFloat()
    val w = region.width.toFloat()
    val h = region.height.toFloat()
    var dragMode by remember { mutableIntStateOf(-1) }

    Box(
        Modifier.fillMaxSize().background(ComposeColor.Black.copy(alpha = 0.14f)).pointerInput(region) {
            detectDragGestures(
                onDragStart = { offset ->
                    val handlePx = with(density) { handle.toPx() }
                    val right = x + w
                    val bottom = y + h
                    val mode = when {
                        offset.x in (right - handlePx)..(right + handlePx) && offset.y in (bottom - handlePx)..(bottom + handlePx) -> 1
                        offset.x in (x - handlePx)..(x + handlePx) && offset.y in (y - handlePx)..(y + handlePx) -> 2
                        offset.x in (right - handlePx)..(right + handlePx) && offset.y in (y - handlePx)..(y + handlePx) -> 3
                        offset.x in (x - handlePx)..(x + handlePx) && offset.y in (bottom - handlePx)..(bottom + handlePx) -> 4
                        offset.x in x..right && offset.y in y..bottom -> 0
                        else -> -1
                    }
                    dragMode = mode
                },
                onDrag = { change, drag ->
                    change.consume()
                    val mode = dragMode
                    when (mode) {
                        0 -> onRegionChange(region.copy(x = (region.x + drag.x).roundToInt().coerceIn(0, screenW - region.width), y = (region.y + drag.y).roundToInt().coerceIn(0, screenH - region.height)))
                        1 -> onRegionChange(region.copy(width = (region.width + drag.x).roundToInt().coerceIn(80, screenW - region.x), height = (region.height + drag.y).roundToInt().coerceIn(80, screenH - region.y)))
                        2 -> {
                            val nx = (region.x + drag.x).roundToInt().coerceIn(0, region.x + region.width - 80)
                            val ny = (region.y + drag.y).roundToInt().coerceIn(0, region.y + region.height - 80)
                            onRegionChange(region.copy(x = nx, y = ny, width = region.width + region.x - nx, height = region.height + region.y - ny))
                        }
                        3 -> {
                            val nx = (region.x).coerceIn(0, screenW - 80)
                            val ny = (region.y + drag.y).roundToInt().coerceIn(0, region.y + region.height - 80)
                            onRegionChange(region.copy(y = ny, width = (region.width + drag.x).roundToInt().coerceAtLeast(80), height = region.height + region.y - ny))
                        }
                        4 -> {
                            val nx = (region.x + drag.x).roundToInt().coerceIn(0, region.x + region.width - 80)
                            val ny = region.y
                            onRegionChange(region.copy(x = nx, y = ny, width = region.width + region.x - nx, height = (region.height + drag.y).roundToInt().coerceAtLeast(80)))
                        }
                    }
                }
            )
        }
    ) {
        Box(
            Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .border(3.dp, ComposeColor.White, RoundedCornerShape(6.dp))
                .background(ComposeColor.Black.copy(alpha = 0.08f))
        ) {
            Text("AI REGION  ${region.width} × ${region.height}", color = ComposeColor.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
        }
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 24.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onReset) { Icon(Icons.Default.Refresh, "Reset") }
            IconButton(onClick = onCenter) { Icon(Icons.Default.CenterFocusStrong, "Center") }
            IconButton(onClick = onFullscreen) { Icon(Icons.Default.Fullscreen, "Fullscreen") }
            IconButton(onClick = onSave) { Icon(Icons.Default.Save, "Save region") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close region selector") }
        }
    }
}

