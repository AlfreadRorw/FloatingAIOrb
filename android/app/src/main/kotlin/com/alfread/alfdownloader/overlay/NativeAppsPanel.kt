package com.alfread.alfdownloader.overlay

import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.alfread.alfdownloader.shizuku.ShizukuHelper
import com.alfread.alfdownloader.ui.Ink

/**
 * Peluncur aplikasi ASLI Android (bukan WebView).
 * Ketuk = buka jendela asli. Tahan = sematkan ★ (favorit tampil paling depan).
 */
@Composable
fun NativeAppsPanel(
    apps: List<NativeApp>,
    pinned: Set<String>,
    windowWidthDp: Int,
    windowHeightDp: Int,
    anchor: Int,
    onLaunch: (NativeApp) -> Unit,
    actions: PanelActions
) {
    val theme = LocalPanel.current
    val accent = theme.accent
    var query by remember { mutableStateOf("") }
    var showWindow by remember { mutableStateOf(false) }
    val filtered = remember(apps, query, pinned) {
        val q = query.trim().lowercase()
        val base = if (q.isBlank()) apps else apps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        val sorted = base.sortedByDescending { it.packageName in pinned }
        if (q.isBlank()) sorted.take(16) else sorted.take(24)
    }
    val shizukuOk = ShizukuHelper.granted.value

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Apps, null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (shizukuOk) "Jendela mengambang aktif" else "Mode biasa (Shizuku belum aktif)",
                color = if (shizukuOk) Ink.Success else Ink.Danger, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.OpenInNew, "Muat ulang", tint = theme.muted, modifier = Modifier.size(16.dp).clickable { actions.onRefreshApps() })
        }

        Row(
            Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(12.dp)).background(theme.surface).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = theme.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = theme.text, fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isBlank()) Text("Cari aplikasi…", color = theme.muted, fontSize = 12.sp)
                    inner()
                }
            )
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            filtered.forEach { app ->
                AppChip(app, pinned = app.packageName in pinned, onClick = { onLaunch(app) }, onLongClick = { actions.onTogglePin(app) })
            }
        }
        if (filtered.isEmpty()) Text("Aplikasi tidak ditemukan", color = theme.muted, fontSize = 11.sp)
        else Text("Ketuk = buka jendela. Tahan = sematkan ★", color = theme.muted, fontSize = 10.sp)

        // Pengaturan jendela (bisa dilipat supaya panel tetap ringkas)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showWindow = !showWindow }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Atur jendela • ${windowWidthDp}×${windowHeightDp} dp", color = theme.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (showWindow) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = theme.muted, modifier = Modifier.size(18.dp))
        }
        if (showWindow) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallChip("Kecil", false) { actions.onPreset(0) }
                    SmallChip("Sedang", false) { actions.onPreset(1) }
                    SmallChip("Besar", false) { actions.onPreset(2) }
                    SmallChip("Tinggi", false) { actions.onPreset(3) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Tengah", "Kiri atas", "Kanan atas", "Kiri bawah", "Kanan bawah").forEachIndexed { i, label ->
                        SmallChip(label, anchor == i) { actions.onAnchor(i) }
                    }
                }
                Text("Lebar", color = theme.muted, fontSize = 10.sp)
                Slider(
                    value = windowWidthDp.toFloat(), onValueChange = { actions.onWidth(it.toInt()) }, valueRange = 240f..600f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = theme.muted.copy(alpha = 0.25f))
                )
                Text("Tinggi", color = theme.muted, fontSize = 10.sp)
                Slider(
                    value = windowHeightDp.toFloat(), onValueChange = { actions.onHeight(it.toInt()) }, valueRange = 320f..900f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = theme.muted.copy(alpha = 0.25f))
                )
            }
        }
    }
}

@Composable
private fun SmallChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalPanel.current
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) theme.accent else theme.surface)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, color = if (selected) (if (theme.light) Color.White else Color.Black) else theme.text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppChip(app: NativeApp, pinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val theme = LocalPanel.current
    Column(
        Modifier.width(66.dp).clip(RoundedCornerShape(14.dp)).background(theme.surface)
            .border(1.dp, if (pinned) theme.accent else theme.muted.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE } },
            update = { it.setImageDrawable(app.icon) },
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text((if (pinned) "★ " else "") + app.label, color = theme.text, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
