package com.alfread.alfdownloader.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfdownloader.looksLikeUrl
import com.alfread.alfdownloader.model.HealthResponse
import com.alfread.alfdownloader.model.Job
import com.alfread.alfdownloader.model.isActive
import com.alfread.alfdownloader.ui.Chip
import com.alfread.alfdownloader.ui.Ink
import com.alfread.alfdownloader.ui.LocalAccent
import com.alfread.alfdownloader.ui.formatClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive as coroutineIsActive
import kotlin.math.roundToInt

/** Bar tegak di tepi layar (kiri/kanan) yang bisa diseret naik-turun, ketuk untuk membuka panel. */
@Composable
fun OverlayBubble(
    sideLeft: Boolean,
    pollHealth: suspend () -> HealthResponse?,
    pollJobs: suspend () -> List<Job>,
    onDownload: (String) -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
    onDragY: (Int) -> Unit,
    onDragX: (Int) -> Unit,
    onDragEnd: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onSwitchSide: () -> Unit,
    onRefreshApps: () -> Unit,
    onLaunchNative: (NativeApp) -> Unit,
    apps: List<NativeApp>,
    windowWidthDp: Int,
    windowHeightDp: Int,
    onWindowWidthChange: (Int) -> Unit,
    onWindowHeightChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var health by remember { mutableStateOf<HealthResponse?>(null) }
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var pasted by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val accent = LocalAccent.current

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

    val active = jobs.filter { it.isActive }
    val avgProgress = if (active.isEmpty()) 0f else (active.map { it.progress }.average() / 100.0).toFloat()
    val online = health?.ok == true

    if (!expanded) {
        Box(
            Modifier
                .fillMaxHeight().widthIn(min = 4.dp).height(112.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            change.consume()
                            val dx = amount.x.roundToInt()
                            val dy = amount.y.roundToInt()
                            if (kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.15f) onDragX(dx) else onDragY(dy)
                        },
                        onDragEnd = { onDragEnd() }
                    )
                }
                .clickable { setExpanded(true) },
            contentAlignment = Alignment.Center
        ) {
            val barShape = RoundedCornerShape(50)
            Box(
                Modifier.fillMaxSize().clip(barShape)
                    .background(Color.White.copy(alpha = if (online) 0.92f else 0.35f))
            ) {
                if (active.isNotEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(avgProgress.coerceIn(0.05f, 1f))
                            .align(Alignment.BottomCenter).clip(barShape).background(accent)
                    )
                }
            }
            if (active.size > 1) {
                Box(
                    Modifier.align(Alignment.TopCenter).offset(y = (-6).dp).size(16.dp)
                        .background(accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("${active.size}", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
        }
        return
    }

    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.width(264.dp).clip(shape).background(Color(0xFF0F0F12)).border(1.dp, Ink.Line, shape)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(if (online) Ink.Success else Ink.Danger, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                if (online) "ALF online" else "Server offline", color = Ink.Text,
                fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f)
            )
            Icon(
                if (sideLeft) Icons.Rounded.ArrowForward else Icons.Rounded.ArrowBack, "Pindah sisi", tint = Ink.Muted,
                modifier = Modifier.size(17.dp).clickable { onSwitchSide() }
            )
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.OpenInNew, "Buka aplikasi", tint = Ink.Muted,
                modifier = Modifier.size(17.dp).clickable { onOpenApp() })
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.UnfoldLess, "Kecilkan", tint = Ink.Muted,
                modifier = Modifier.size(17.dp).clickable { setExpanded(false) })
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.Close, "Tutup", tint = Ink.Danger,
                modifier = Modifier.size(17.dp).clickable { onClose() })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))

        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF16161A)).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        pasted.ifBlank { "Tempel link di sini…" },
                        color = if (pasted.isBlank()) Ink.Muted else Ink.Text,
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                MiniButton(Icons.Rounded.ContentPaste) { pasted = clipboard.getText()?.text.orEmpty() }
                Spacer(Modifier.width(6.dp))
                MiniButton(Icons.Rounded.Download, enabled = looksLikeUrl(pasted), filled = true) {
                    onDownload(pasted.trim()); pasted = ""
                }
            }

            if (active.isEmpty()) {
                Text("Tidak ada unduhan berjalan", color = Ink.Muted, fontSize = 12.sp)
            } else {
                active.take(2).forEach { job ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            job.title ?: job.url, color = Ink.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            progress = { (job.progress / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                            color = accent, trackColor = Color(0xFF26262C)
                        )
                        Text(
                            if (job.status == "downloading") "${job.progress.toInt()}%  •  ${job.etaSeconds?.let { formatClock(it) } ?: ""}"
                            else job.status,
                            color = Ink.Muted, fontSize = 11.sp
                        )
                    }
                }
                if (active.size > 2) Text("+${active.size - 2} lainnya", color = Ink.Muted, fontSize = 11.sp)
            }

            NativeAppsPanel(
                apps = apps,
                onLaunch = onLaunchNative,
                onRefresh = onRefreshApps,
                windowWidthDp = windowWidthDp,
                windowHeightDp = windowHeightDp,
                onWindowWidthChange = onWindowWidthChange,
                onWindowHeightChange = onWindowHeightChange
            )
        }
    }
}

@Composable
private fun MiniButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, filled: Boolean = false, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
            .background(if (filled) (if (enabled) accent else Color(0xFF26262C)) else Color(0xFF1C1C21))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (filled) (if (enabled) Color.Black else Ink.Muted) else Ink.Text, modifier = Modifier.size(16.dp))
    }
}
