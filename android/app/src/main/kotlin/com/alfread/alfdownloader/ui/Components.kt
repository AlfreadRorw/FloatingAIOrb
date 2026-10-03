package com.alfread.alfdownloader.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfdownloader.model.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun rememberTick(enabled: Boolean): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(enabled, haptic) {
        { if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

@Composable
fun AlfMark(size: Dp, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val r = s * 0.30f
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF2A2A31), Color(0xFF0A0A0C))),
            cornerRadius = CornerRadius(r)
        )
        drawRoundRect(color = accent.copy(alpha = 0.40f), cornerRadius = CornerRadius(r), style = Stroke(width = s * 0.03f))
        val arrow = Path().apply {
            moveTo(s * .44f, s * .20f); lineTo(s * .56f, s * .20f); lineTo(s * .56f, s * .47f)
            lineTo(s * .68f, s * .47f); lineTo(s * .50f, s * .65f); lineTo(s * .32f, s * .47f)
            lineTo(s * .44f, s * .47f); close()
        }
        val tray = Path().apply {
            moveTo(s * .28f, s * .64f); lineTo(s * .34f, s * .64f); lineTo(s * .34f, s * .72f)
            lineTo(s * .66f, s * .72f); lineTo(s * .66f, s * .64f); lineTo(s * .72f, s * .64f)
            lineTo(s * .72f, s * .80f); lineTo(s * .28f, s * .80f); close()
        }
        drawPath(arrow, accent)
        drawPath(tray, accent)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, radius: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier.clip(shape).background(Ink.Surface).border(1.dp, Ink.Line, shape),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(horizontal = 4.dp),
        color = Ink.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = LocalAccent.current, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun IconAction(icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Ink.Muted) {
    Box(
        modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val bg by animateColorAsState(if (selected) accent else Ink.Surface2, label = "chipBg")
    val fg = if (selected) Color.Black else Ink.Text
    val shape = RoundedCornerShape(50)
    Row(
        modifier.alpha(if (enabled) 1f else 0.4f).clip(shape).background(bg)
            .border(1.dp, if (selected) accent else Ink.Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AccentButton(
    text: String, icon: ImageVector, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().height(58.dp).clip(shape)
            .background(if (enabled) accent else Ink.Surface3)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (enabled) Color.Black else Ink.Muted
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = fg, strokeWidth = 2.5.dp)
        else Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GhostButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true, tint: Color = Ink.Text, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.alpha(if (enabled) 1f else 0.4f).height(48.dp).clip(shape).background(Ink.Surface2)
            .border(1.dp, Ink.Line, shape).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusChip(online: Boolean, starting: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulseA"
    )
    val dot = when { online -> Ink.Success; starting -> LocalAccent.current; else -> Ink.Danger }
    val label = when { online -> "Online"; starting -> "Menyalakan"; else -> "Offline" }
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(Ink.Surface2).border(1.dp, Ink.Line, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).alpha(if (starting) pulse else 1f).background(dot, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Ink.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (!online && !starting) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.PowerSettingsNew, null, tint = Ink.Muted, modifier = Modifier.size(14.dp))
        }
    }
}

private val thumbCache = LruCache<String, ImageBitmap>(24)

@Composable
fun RemoteThumb(url: String?, modifier: Modifier = Modifier, fallback: ImageVector = Icons.Rounded.Movie) {
    val img by produceState<ImageBitmap?>(thumbCache.get(url ?: ""), url) {
        if (url.isNullOrBlank()) { value = null; return@produceState }
        thumbCache.get(url)?.let { value = it; return@produceState }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 8000
                val bytes = c.inputStream.use { it.readBytes() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 640) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
            }.getOrNull()
        }
        if (bmp != null) thumbCache.put(url, bmp)
        value = bmp
    }
    Box(modifier.background(Ink.Surface3), contentAlignment = Alignment.Center) {
        val b = img
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(fallback, null, tint = Ink.Muted, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, tint = if (checked) accent else Ink.Muted)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = Ink.Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black, checkedTrackColor = accent, checkedBorderColor = accent,
                uncheckedThumbColor = Ink.Muted, uncheckedTrackColor = Ink.Surface3, uncheckedBorderColor = Ink.Line
            )
        )
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(Ink.Line))
}

// ---------------------------------------------------------------- dock

private data class DockItemSpec(val tab: Tab, val label: String, val icon: ImageVector)

private val dockItems = listOf(
    DockItemSpec(Tab.DOWNLOAD, "Unduh", Icons.Rounded.Download),
    DockItemSpec(Tab.LIBRARY, "Pustaka", Icons.Rounded.VideoLibrary),
    DockItemSpec(Tab.SETTINGS, "Atur", Icons.Rounded.Tune)
)

@Composable
fun Dock(
    selected: Tab, onSelect: (Tab) -> Unit, prefs: Prefs, badge: Int,
    modifier: Modifier = Modifier, preview: Boolean = false
) {
    val tick = rememberTick(prefs.haptics)
    val iconSize = when (prefs.dockSize) { 0 -> 20.dp; 2 -> 28.dp; else -> 24.dp }
    val container = Ink.Surface2.copy(alpha = prefs.dockOpacity)
    val select: (Tab) -> Unit = { tick(); onSelect(it) }
    val badgeCount = if (prefs.dockBadge) badge else 0

    if (prefs.dockStyle == 0) {
        val shape = RoundedCornerShape(32.dp)
        val outer = if (preview) modifier.fillMaxWidth() else modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)
        Box(outer, contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(shape).background(container).border(1.dp, Ink.Line, shape).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                dockItems.forEach { item ->
                    FloatingDockItem(item, selected == item.tab, prefs.dockLabels, iconSize, if (item.tab == Tab.DOWNLOAD) badgeCount else 0) { select(item.tab) }
                }
            }
        }
    } else {
        val accent = LocalAccent.current
        val inset = if (preview) Modifier else Modifier.navigationBarsPadding()
        Column(modifier.fillMaxWidth().background(container).then(inset)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
            Row(Modifier.fillMaxWidth().height(if (prefs.dockSize == 2) 72.dp else 62.dp), verticalAlignment = Alignment.CenterVertically) {
                dockItems.forEach { item ->
                    val sel = selected == item.tab
                    val tint by animateColorAsState(if (sel) accent else Ink.Muted, label = "barTint")
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clickable { select(item.tab) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        BadgeIcon(item.icon, tint, iconSize, if (item.tab == Tab.DOWNLOAD) badgeCount else 0)
                        if (prefs.dockLabels != 2) {
                            Spacer(Modifier.height(3.dp))
                            Text(item.label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeIcon(icon: ImageVector, tint: Color, size: Dp, badge: Int) {
    if (badge > 0) {
        BadgedBox(badge = { Badge(containerColor = LocalAccent.current, contentColor = Color.Black) { Text("$badge") } }) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(size))
        }
    } else {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun FloatingDockItem(item: DockItemSpec, selected: Boolean, labelMode: Int, iconSize: Dp, badge: Int, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent, label = "dockBg")
    val tint by animateColorAsState(if (selected) accent else Ink.Muted, label = "dockTint")
    val showLabel = labelMode == 1 || (labelMode == 0 && selected)
    Row(
        Modifier.clip(RoundedCornerShape(26.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeIcon(item.icon, tint, iconSize, badge)
        AnimatedVisibility(showLabel, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
            Text(item.label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

// ---------------------------------------------------------------- toast

@Composable
fun ToastHost(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        if (message != null) {
            shown = message
            delay(3200)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null, modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        val shape = RoundedCornerShape(18.dp)
        Row(
            Modifier.padding(horizontal = 24.dp).clip(shape).background(Ink.Surface3).border(1.dp, Ink.Line, shape)
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Info, null, tint = LocalAccent.current, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(shown, color = Ink.Text, fontSize = 14.sp, maxLines = 3)
        }
    }
}
