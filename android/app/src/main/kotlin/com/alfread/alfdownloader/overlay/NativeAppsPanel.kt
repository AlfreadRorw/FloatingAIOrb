package com.alfread.alfdownloader.overlay

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.alfread.alfdownloader.ui.Ink
import com.alfread.alfdownloader.ui.LocalAccent
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults

import com.alfread.alfdownloader.shizuku.ShizukuHelper

/**
 * Launcher panel for REAL Android apps. It never embeds a web page.
 * Ketuk = buka jendela asli. Tahan = sematkan/lepas sematan (favorit tampil paling depan).
 */
@Composable
fun NativeAppsPanel(
    apps: List<NativeApp>,
    pinned: Set<String>,
    onTogglePin: (NativeApp) -> Unit,
    onLaunch: (NativeApp) -> Unit,
    onRefresh: () -> Unit,
    windowWidthDp: Int,
    windowHeightDp: Int,
    anchor: Int,
    onWindowWidthChange: (Int) -> Unit,
    onWindowHeightChange: (Int) -> Unit,
    onPreset: (Int) -> Unit,
    onAnchor: (Int) -> Unit
) {
    val accent = LocalAccent.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query, pinned) {
        val q = query.trim().lowercase()
        val base = if (q.isBlank()) apps else apps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        val sorted = base.sortedByDescending { it.packageName in pinned }
        if (q.isBlank()) sorted.take(14) else sorted.take(24)
    }
    val shizukuOk = ShizukuHelper.granted.value

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Apps, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Aplikasi asli", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.OpenInNew, "Refresh", tint = Ink.Muted, modifier = Modifier.size(17.dp).clickable { onRefresh() })
        }

        // Status mode: supaya jelas kenapa aplikasi terbuka penuh / mengambang
        Text(
            if (shizukuOk) "● Mode jendela mengambang (Shizuku aktif)"
            else "● Mode biasa — Shizuku belum terhubung/diizinkan, aplikasi akan terbuka layar penuh",
            color = if (shizukuOk) Ink.Success else Ink.Danger, fontSize = 10.sp, lineHeight = 13.sp
        )

        Row(
            Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF17171C)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = Ink.Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Ink.Text, fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isBlank()) Text("Cari aplikasi…", color = Ink.Muted, fontSize = 12.sp)
                    inner()
                }
            )
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            filtered.forEach { app ->
                AppChip(app, pinned = app.packageName in pinned, onClick = { onLaunch(app) }, onLongClick = { onTogglePin(app) })
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ukuran jendela: ${windowWidthDp} × ${windowHeightDp} dp", color = Ink.Muted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallChip("Kecil", false) { onPreset(0) }
                SmallChip("Sedang", false) { onPreset(1) }
                SmallChip("Besar", false) { onPreset(2) }
                SmallChip("Tinggi", false) { onPreset(3) }
            }
            Text("Posisi jendela", color = Ink.Muted, fontSize = 10.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Tengah", "Kiri atas", "Kanan atas", "Kiri bawah", "Kanan bawah").forEachIndexed { i, label ->
                    SmallChip(label, anchor == i) { onAnchor(i) }
                }
            }
            Text("Lebar", color = Ink.Muted, fontSize = 10.sp)
            Slider(
                value = windowWidthDp.toFloat(),
                onValueChange = { onWindowWidthChange(it.toInt()) },
                valueRange = 240f..600f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Ink.Surface3)
            )
            Text("Tinggi", color = Ink.Muted, fontSize = 10.sp)
            Slider(
                value = windowHeightDp.toFloat(),
                onValueChange = { onWindowHeightChange(it.toInt()) },
                valueRange = 320f..900f,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Ink.Surface3)
            )
        }

        if (filtered.isEmpty()) {
            Text("Aplikasi tidak ditemukan", color = Ink.Muted, fontSize = 11.sp)
        } else {
            Text("Ketuk = buka jendela asli. Tahan = sematkan ★ (tampil paling depan).", color = Ink.Muted, fontSize = 10.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun SmallChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) accent else Color(0xFF1C1C21))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(label, color = if (selected) Color.Black else Ink.Text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppChip(app: NativeApp, pinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val accent = LocalAccent.current
    Column(
        Modifier.width(66.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF17171C))
            .border(1.dp, if (pinned) accent else Ink.Line, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE } },
            update = { it.setImageDrawable(app.icon) },
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text((if (pinned) "★ " else "") + app.label, color = Ink.Text, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
