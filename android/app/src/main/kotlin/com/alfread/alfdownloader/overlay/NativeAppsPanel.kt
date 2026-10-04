package com.alfread.alfdownloader.overlay

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

/**
 * Launcher panel for REAL Android apps. It never embeds a web page.
 * Tapping an item asks the service to start that package in Android freeform mode.
 */
@Composable
fun NativeAppsPanel(
    apps: List<NativeApp>,
    onLaunch: (NativeApp) -> Unit,
    onRefresh: () -> Unit,
    windowWidthDp: Int,
    windowHeightDp: Int,
    onWindowWidthChange: (Int) -> Unit,
    onWindowHeightChange: (Int) -> Unit
) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) apps.take(12)
        else apps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }.take(20)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Apps, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Aplikasi asli", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.OpenInNew, "Refresh", tint = Ink.Muted, modifier = Modifier.size(17.dp).clickable { onRefresh() })
        }

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
                AppChip(app, onClick = { onLaunch(app) })
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Ukuran jendela asli: ${windowWidthDp} × ${windowHeightDp} dp", color = Ink.Muted, fontSize = 11.sp)
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
            Text("Ketuk aplikasi untuk membuka jendela asli. Android tetap menerima swipe di luar jendela.", color = Ink.Muted, fontSize = 10.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun AppChip(app: NativeApp, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.width(66.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF17171C))
            .border(1.dp, Ink.Line, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE } },
            update = { it.setImageDrawable(app.icon) },
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(app.label, color = Ink.Text, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
