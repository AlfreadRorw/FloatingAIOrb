package com.alfread.alfdownloader.overlay

import android.widget.ImageView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.alfread.alfdownloader.model.Prefs
import com.alfread.alfdownloader.ui.Ink
import kotlin.math.roundToInt

private val FrameCorner = 14.dp

/**
 * Garis tepi bertema yang digambar tepat di sekeliling jendela aplikasi (TikTok dll.).
 * Jendela overlay-nya NOT_TOUCHABLE, jadi sentuhan tetap tembus ke aplikasi di bawahnya.
 */
@Composable
fun WindowBorderOverlay(prefs: Prefs) {
    val theme = resolvePanelTheme(prefs)
    val shape = RoundedCornerShape(FrameCorner)
    val t = rememberInfiniteTransition(label = "frameGlow")
    val glow = t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "frameGlowA")
    Box(
        Modifier.fillMaxSize()
            .alpha(if (prefs.frameGlow) glow.value else 1f)
            .border(2.5.dp, theme.borderBrush(), shape)
    )
}

/**
 * Bar judul bertema yang menutupi bar judul polos bawaan ROM.
 * Seret = pindahkan jendela. Jendela HANYA tertutup lewat tombol X di sini (ketuk di luar tidak menutup).
 */
@Composable
fun WindowTitleOverlay(
    prefs: Prefs,
    app: NativeApp?,
    onDrag: (dx: Int, dy: Int) -> Unit,
    onDragEnd: () -> Unit,
    onToggleLock: () -> Unit,
    onCycleSize: () -> Unit,
    onClose: () -> Unit
) {
    val theme = resolvePanelTheme(prefs)
    val shape = RoundedCornerShape(topStart = FrameCorner, topEnd = FrameCorner)
    val view = LocalView.current
    fun absolute(local: Offset): Offset {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return Offset(loc[0] + local.x, loc[1] + local.y)
    }
    val last = androidx.compose.runtime.remember { arrayOfNulls<Offset>(1) }

    Row(
        Modifier.fillMaxSize().clip(shape)
            .background(theme.bgBrush(1f))
            .border(1.5.dp, theme.borderBrush(), shape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { last[0] = absolute(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        val cur = absolute(change.position)
                        val l = last[0] ?: cur
                        onDrag((cur.x - l.x).roundToInt(), (cur.y - l.y).roundToInt())
                        last[0] = cur
                    },
                    onDragEnd = { last[0] = null; onDragEnd() },
                    onDragCancel = { last[0] = null; onDragEnd() }
                )
            }
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app?.icon != null) {
            AndroidView(
                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE } },
                update = { it.setImageDrawable(app.icon) },
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            app?.label ?: "", color = theme.text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        TitleButton(if (prefs.windowLock) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
            if (prefs.windowLock) theme.accent else theme.muted, onToggleLock)
        TitleButton(Icons.Rounded.AspectRatio, theme.muted, onCycleSize)
        TitleButton(Icons.Rounded.Close, Ink.Danger, onClose)
    }
}

@Composable
private fun TitleButton(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
}
