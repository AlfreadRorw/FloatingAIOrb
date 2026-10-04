package com.alfread.alfdownloader.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfdownloader.looksLikeUrl
import com.alfread.alfdownloader.model.HealthResponse
import com.alfread.alfdownloader.model.Job
import com.alfread.alfdownloader.model.Prefs
import com.alfread.alfdownloader.model.isActive
import com.alfread.alfdownloader.ui.Ink
import com.alfread.alfdownloader.ui.formatClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive as coroutineIsActive
import kotlin.math.roundToInt

/** Kumpulan aksi yang dipakai panel — dikumpulkan agar signature tidak membengkak. */
class PanelActions(
    val onDownload: (url: String, quality: String) -> Unit,
    val onCancel: (String) -> Unit,
    val onOpenApp: () -> Unit,
    val onClose: () -> Unit,
    val onSwitchSide: () -> Unit,
    val onRefreshApps: () -> Unit,
    val onLaunchNative: (NativeApp) -> Unit,
    val onTogglePin: (NativeApp) -> Unit,
    val onWidth: (Int) -> Unit,
    val onHeight: (Int) -> Unit,
    val onPreset: (Int) -> Unit,
    val onAnchor: (Int) -> Unit,
    val onStartServer: () -> Unit,
    val onStopServer: () -> Unit,
    val onUpdateYtdlp: () -> Unit,
    val onClearFinished: () -> Unit,
    val onSetTheme: (Int) -> Unit,
    val onSetOpacity: (Float) -> Unit,
    val onRestartServer: () -> Unit,
    val onFixServer: () -> Unit,
    val onOpenFolder: () -> Unit,
    val onToggleLock: () -> Unit,
    val onToggleFrame: () -> Unit,
    val onCloseWindow: () -> Unit,
    val onSetFont: (Int) -> Unit,
    val onCaptionHeight: (Int) -> Unit
)

@Composable
private fun PanelIcon(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(17.dp))
    }
}

/** Bar tegak di tepi layar yang diseret naik-turun; ketuk untuk membuka panel bertema. */
@Composable
fun OverlayBubble(
    sideLeft: Boolean,
    prefs: Prefs,
    pollHealth: suspend () -> HealthResponse?,
    pollJobs: suspend () -> List<Job>,
    pollLog: suspend () -> List<String>,
    onDragY: (Int) -> Unit,
    onDragX: (Int) -> Unit,
    onDragEnd: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    apps: List<NativeApp>,
    pinned: Set<String>,
    windowWidthDp: Int,
    windowHeightDp: Int,
    anchor: Int,
    actions: PanelActions
) {
    val theme = PanelThemes[prefs.panelTheme.coerceIn(0, PanelThemes.lastIndex)].let {
        if (prefs.panelUseThemeAccent) it else it.copy(accent = com.alfread.alfdownloader.ui.AccentOptions[prefs.accent.coerceIn(0, com.alfread.alfdownloader.ui.AccentOptions.lastIndex)].color)
    }
    var expanded by remember { mutableStateOf(false) }
    var health by remember { mutableStateOf<HealthResponse?>(null) }
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var pasted by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        while (coroutineIsActive) {
            health = pollHealth()
            jobs = pollJobs()
            delay(if (jobs.any { it.isActive }) 1200 else 2500)
        }
    }

    fun setExpanded(value: Boolean) {
        expanded = value
        onExpandedChange(value)
        if (!value) pasted = ""
    }

    LaunchedEffect(expanded) {
        if (expanded && prefs.autoPasteOnExpand && pasted.isBlank()) {
            delay(300)
            val t = runCatching { clipboard.getText()?.text.orEmpty().trim() }.getOrDefault("")
            if (looksLikeUrl(t)) { pasted = t; tab = 0 }
        }
    }

    val active = jobs.filter { it.isActive }
    val avgProgress = if (active.isEmpty()) 0f else (active.map { it.progress }.average() / 100.0).toFloat()
    val online = health?.ok == true
    val ready = health?.ready != false

    CompositionLocalProviderPanel(theme) {
        if (!expanded) {
            CollapsedBar(theme, prefs, online, active.size, avgProgress,
                onDragX = onDragX, onDragY = onDragY, onDragEnd = onDragEnd, onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); setExpanded(true)
                })
            return@CompositionLocalProviderPanel
        }

        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val shape = RoundedCornerShape(prefs.panelCornerDp.dp)
        AnimatedVisibility(shown, enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.92f)) {
            Column(
                Modifier.width(prefs.panelWidthDp.dp).clip(shape)
                    .background(theme.bgBrush(prefs.panelOpacity))
                    .border(1.2.dp, theme.borderBrush(), shape)
            ) {
                // ------- header
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(online, ready, theme)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Alfread Tools", color = theme.text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text(
                            when {
                                !online -> "Server offline"
                                !ready -> "Memuat yt-dlp…"
                                else -> "yt-dlp ${health?.ytdlp ?: ""}".trim()
                            }, color = theme.muted, fontSize = 10.sp, maxLines = 1
                        )
                    }
                    PanelIcon(if (sideLeft) Icons.Rounded.ArrowForward else Icons.Rounded.ArrowBack, "Pindah sisi", theme.muted) { actions.onSwitchSide() }
                    PanelIcon(Icons.Rounded.OpenInNew, "Buka ALF", theme.muted) { actions.onOpenApp() }
                    PanelIcon(Icons.Rounded.UnfoldLess, "Kecilkan", theme.muted) { setExpanded(false) }
                    PanelIcon(Icons.Rounded.Close, "Tutup", Ink.Danger) { actions.onClose() }
                }

                // ------- tab
                Row(
                    Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(theme.surface).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    listOf(Icons.Rounded.Download to "Unduh", Icons.Rounded.Apps to "Aplikasi", Icons.Rounded.Tune to "Alat").forEachIndexed { i, (ic, label) ->
                        val sel = tab == i
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(50))
                                .background(if (sel) theme.accent else Color.Transparent)
                                .clickable { tab = i }.padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(ic, null, tint = if (sel) (if (theme.light) Color.White else Color.Black) else theme.muted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(label, color = if (sel) (if (theme.light) Color.White else Color.Black) else theme.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (i == 0 && active.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Text("${active.size}", color = if (sel) (if (theme.light) Color.White else Color.Black) else theme.accent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                val maxContentH = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
                Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).heightIn(max = maxContentH).verticalScroll(rememberScrollState())) {
                    when (tab) {
                        0 -> DownloadTab(theme, jobs, pasted, { pasted = it }, online, actions) { text -> pasted = text }
                        1 -> NativeAppsPanel(
                            apps = apps, pinned = pinned,
                            windowWidthDp = windowWidthDp, windowHeightDp = windowHeightDp, anchor = anchor,
                            onLaunch = { app ->
                                actions.onLaunchNative(app)
                                if (prefs.collapseOnLaunch) setExpanded(false)
                            },
                            actions = actions
                        )
                        else -> ToolsTab(theme, prefs, health, online, active.isNotEmpty(), pollLog, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompositionLocalProviderPanel(theme: PanelTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPanel provides theme, content = content)
}

@Composable
private fun StatusDot(online: Boolean, ready: Boolean, theme: PanelTheme) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "dotA")
    val loading = online && !ready
    Box(
        Modifier.size(10.dp).alpha(if (loading) a else 1f)
            .background(if (!online) Ink.Danger else if (loading) theme.accent else Ink.Success, CircleShape)
    )
}

@Composable
private fun CollapsedBar(
    theme: PanelTheme, prefs: Prefs, online: Boolean, activeCount: Int, progress: Float,
    onDragX: (Int) -> Unit, onDragY: (Int) -> Unit, onDragEnd: () -> Unit, onTap: () -> Unit
) {
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseA")
    val idleDim = prefs.barDimWhenIdle && activeCount == 0
    val alpha by animateFloatAsState(
        when { !online -> 0.4f; idleDim -> 0.5f; activeCount > 0 && prefs.barGlow -> pulse; else -> 1f }, label = "barAlpha"
    )
    Box(
        Modifier.fillMaxHeight().widthIn(min = 4.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, amount ->
                        change.consume()
                        val dx = amount.x.roundToInt(); val dy = amount.y.roundToInt()
                        if (kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.15f) onDragX(dx) else onDragY(dy)
                    },
                    onDragEnd = { onDragEnd() }
                )
            }
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        val barShape = RoundedCornerShape(50)
        Box(Modifier.fillMaxSize().alpha(alpha).clip(barShape).background(theme.barBrush())) {
            if (activeCount > 0) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(progress.coerceIn(0.06f, 1f)).align(Alignment.BottomCenter)
                        .clip(barShape).background(Color.Black.copy(alpha = 0.35f))
                )
            }
        }
        if (activeCount > 1) {
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (-6).dp).size(16.dp).background(theme.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("$activeCount", color = if (theme.light) Color.White else Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ============================================================ tab: unduh

@Composable
private fun DownloadTab(
    theme: PanelTheme, jobs: List<Job>, pasted: String, setPasted: (String) -> Unit,
    online: Boolean, actions: PanelActions, onPasteText: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var quality by remember { mutableStateOf("best") }
    val active = jobs.filter { it.isActive }
    val finished = jobs.filter { it.status == "completed" }.takeLast(3).reversed()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(14.dp)).background(theme.surface).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    pasted.ifBlank { "Tempel link di sini…" },
                    color = if (pasted.isBlank()) theme.muted else theme.text,
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            MiniButton(Icons.Rounded.ContentPaste, theme) { onPasteText(clipboard.getText()?.text.orEmpty().trim()) }
            Spacer(Modifier.width(6.dp))
            MiniButton(Icons.Rounded.Download, theme, enabled = looksLikeUrl(pasted), filled = true) {
                actions.onDownload(pasted.trim(), quality); setPasted("")
            }
        }

        // Pilih kualitas cepat
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("best" to "Terbaik", "1080" to "1080p", "720" to "720p", "audio" to "MP3").forEach { (v, label) ->
                val sel = quality == v
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (sel) theme.accent else theme.surface)
                        .clickable { quality = v }.padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Text(label, color = if (sel) (if (theme.light) Color.White else Color.Black) else theme.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!online) {
            Text("Server offline. Buka tab Alat → Nyalakan server.", color = theme.muted, fontSize = 11.sp)
        } else if (active.isEmpty()) {
            Text("Tidak ada unduhan berjalan", color = theme.muted, fontSize = 11.sp)
        } else {
            active.take(3).forEach { job ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(theme.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(job.title ?: job.url, color = theme.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.Close, "Batal", tint = Ink.Danger, modifier = Modifier.size(16.dp).clickable { actions.onCancel(job.id) })
                    }
                    LinearProgressIndicator(
                        progress = { (job.progress / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                        color = theme.accent, trackColor = theme.muted.copy(alpha = 0.25f)
                    )
                    Text(
                        if (job.status == "downloading") "${job.progress.toInt()}%  •  ${speedText(job.speedBps)}  •  ${job.etaSeconds?.let { formatClock(it) } ?: ""}"
                        else job.status, color = theme.muted, fontSize = 10.sp
                    )
                }
            }
            if (active.size > 3) Text("+${active.size - 3} lainnya", color = theme.muted, fontSize = 11.sp)
        }

        if (finished.isNotEmpty()) {
            Text("Baru selesai", color = theme.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            finished.forEach { job ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Ink.Success, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(job.title ?: job.url, color = theme.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun speedText(bps: Double): String = when {
    bps >= 1_048_576 -> String.format("%.1f MB/s", bps / 1_048_576)
    bps >= 1024 -> String.format("%.0f KB/s", bps / 1024)
    else -> ""
}

// ============================================================ tab: alat

@Composable
private fun SectionLabel(text: String, theme: PanelTheme) {
    Text(text.uppercase(), color = theme.muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
}

@Composable
private fun ToggleRow(label: String, desc: String, checked: Boolean, theme: PanelTheme, onChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(theme.surface)
            .clickable(onClick = onChange).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = theme.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = theme.muted, fontSize = 9.sp, lineHeight = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Switch(
            checked = checked, onCheckedChange = { onChange() },
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = if (theme.light) Color.White else Color.Black,
                checkedTrackColor = theme.accent,
                uncheckedThumbColor = theme.muted,
                uncheckedTrackColor = theme.muted.copy(alpha = 0.2f),
                uncheckedBorderColor = theme.muted.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun ToolsTab(
    theme: PanelTheme, prefs: Prefs, health: HealthResponse?, online: Boolean, hasActive: Boolean,
    pollLog: suspend () -> List<String>, actions: PanelActions
) {
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var logLoading by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---------------- server
        SectionLabel("Server", theme)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(theme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (online) "Server berjalan" else "Server mati", color = if (online) Ink.Success else Ink.Danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            if (online && health != null) {
                Text("Versi ${health.version ?: "-"} • yt-dlp ${health.ytdlp ?: "memuat…"}", color = theme.muted, fontSize = 10.sp)
                Text("Ruang kosong ${freeText(health.freeBytes)} • aktif ${formatUptime(health.uptime)}", color = theme.muted, fontSize = 10.sp)
            } else Text("Ketuk Nyalakan untuk menjalankan lewat Termux.", color = theme.muted, fontSize = 10.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton(Icons.Rounded.PlayArrow, "Nyalakan", theme, Modifier.weight(1f), enabled = !online) { actions.onStartServer() }
            ToolButton(Icons.Rounded.PowerSettingsNew, "Matikan", theme, Modifier.weight(1f), enabled = online && !hasActive) { actions.onStopServer() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton(Icons.Rounded.RestartAlt, "Mulai ulang", theme, Modifier.weight(1f), enabled = online && !hasActive) { actions.onRestartServer() }
            ToolButton(Icons.Rounded.Shield, "Anti-mati", theme, Modifier.weight(1f)) { actions.onFixServer() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton(Icons.Rounded.Refresh, "Update yt-dlp", theme, Modifier.weight(1f), enabled = online) { actions.onUpdateYtdlp() }
            ToolButton(Icons.Rounded.DeleteSweep, "Bersihkan", theme, Modifier.weight(1f), enabled = online) { actions.onClearFinished() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton(Icons.Rounded.Article, if (logLoading) "Memuat…" else "Log server", theme, Modifier.weight(1f)) {
                scope.launch { logLoading = true; log = pollLog(); logLoading = false }
            }
            ToolButton(Icons.Rounded.FolderOpen, "Folder unduhan", theme, Modifier.weight(1f)) { actions.onOpenFolder() }
        }
        if (log.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(theme.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    log.takeLast(14).joinToString("\n"), color = theme.muted, fontSize = 9.sp, lineHeight = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolButton(Icons.Rounded.ContentPaste, "Salin log", theme, Modifier.weight(1f)) {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(log.joinToString("\n")))
                    }
                    ToolButton(Icons.Rounded.Close, "Tutup log", theme, Modifier.weight(1f)) { log = emptyList() }
                }
            }
        }

        // ---------------- jendela aplikasi
        SectionLabel("Jendela aplikasi", theme)
        ToggleRow("Kunci di atas", "Jendela tidak hilang saat mengetuk di luar — tutup lewat X", prefs.windowLock, theme) { actions.onToggleLock() }
        ToggleRow("Bingkai & bar judul tema", "Ganti bar judul putih polos dengan tema panel", prefs.windowFrame, theme) { actions.onToggleFrame() }
        Text("Tinggi bar judul ${prefs.captionHeightDp} dp (sesuaikan bila bar putih masih terlihat)", color = theme.muted, fontSize = 10.sp)
        androidx.compose.material3.Slider(
            value = prefs.captionHeightDp.toFloat(), onValueChange = { actions.onCaptionHeight(it.toInt()) }, valueRange = 28f..64f,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent, inactiveTrackColor = theme.muted.copy(alpha = 0.25f))
        )
        ToolButton(Icons.Rounded.Close, "Tutup jendela aplikasi", theme, Modifier.fillMaxWidth()) { actions.onCloseWindow() }

        // ---------------- tampilan
        SectionLabel("Tampilan", theme)
        Text("Tema", color = theme.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PanelThemes.size) { i ->
                val t = PanelThemes[i]
                val sel = prefs.panelTheme == i
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { actions.onSetTheme(i) }) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(t.bgBrush(1f))
                            .border(if (sel) 2.5.dp else 1.dp, if (sel) theme.accent else t.border.first(), CircleShape)
                    ) { Box(Modifier.align(Alignment.Center).size(10.dp).background(t.accent, CircleShape)) }
                    Text(t.name, color = if (sel) theme.text else theme.muted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        Text("Kepekatan ${(prefs.panelOpacity * 100).toInt()}%", color = theme.muted, fontSize = 10.sp)
        androidx.compose.material3.Slider(
            value = prefs.panelOpacity, onValueChange = { actions.onSetOpacity(it) }, valueRange = 0.4f..1f,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent, inactiveTrackColor = theme.muted.copy(alpha = 0.25f))
        )
        Text("Font", color = theme.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(com.alfread.alfdownloader.ui.AlfFonts.size) { i ->
                val f = com.alfread.alfdownloader.ui.AlfFonts[i]
                val sel = prefs.fontIndex == i
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (sel) theme.accent else theme.surface)
                        .clickable { actions.onSetFont(i) }.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(f.name, color = if (sel) (if (theme.light) Color.White else Color.Black) else theme.text, fontSize = 11.sp, fontFamily = f.family)
                }
            }
        }
    }
}

private fun freeText(b: Long): String = when {
    b >= 1L shl 30 -> String.format("%.1f GB", b / (1024.0 * 1024 * 1024))
    b >= 1L shl 20 -> String.format("%.0f MB", b / (1024.0 * 1024))
    else -> "-"
}

private fun formatUptime(sec: Int): String = when {
    sec >= 3600 -> "${sec / 3600}j ${(sec % 3600) / 60}m"
    sec >= 60 -> "${sec / 60}m"
    else -> "${sec}d"
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, theme: PanelTheme, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.alpha(if (enabled) 1f else 0.4f).height(38.dp).clip(RoundedCornerShape(12.dp)).background(theme.surface)
            .border(1.dp, theme.muted.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = theme.accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = theme.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun MiniButton(icon: ImageVector, theme: PanelTheme, enabled: Boolean = true, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
            .background(if (filled) (if (enabled) theme.accent else theme.surface) else theme.surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (filled) (if (enabled) (if (theme.light) Color.White else Color.Black) else theme.muted) else theme.text, modifier = Modifier.size(17.dp))
    }
}
