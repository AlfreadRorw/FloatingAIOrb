package com.alfread.alfdownloader.overlay

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.alfread.alfdownloader.ui.Chip
import com.alfread.alfdownloader.ui.Ink
import com.alfread.alfdownloader.ui.LocalAccent
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MiniBrowserOverlay(
    shortcut: AppShortcut,
    onSwitch: (AppShortcut) -> Unit,
    onOpenNative: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onDragHeader: (Int, Int) -> Unit,
    onDragHeaderEnd: () -> Unit,
    onResize: (Int, Int) -> Unit,
    onResizeEnd: () -> Unit
) {
    val accent = LocalAccent.current
    var canGoBack by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loadedId by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(18.dp)

    Column(
        Modifier.fillMaxSize().clip(shape).background(Color(0xFF0F0F12)).border(1.dp, Ink.Line, shape)
    ) {
        Row(
            Modifier.fillMaxWidth().height(42.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, amount -> change.consume(); onDragHeader(amount.x.roundToInt(), amount.y.roundToInt()) },
                        onDragEnd = { onDragHeaderEnd() }
                    )
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.DragIndicator, null, tint = Ink.Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(shortcut.label, color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.OpenInNew, "Buka app asli", tint = accent, modifier = Modifier.size(16.dp).clickable { onOpenNative() })
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.Remove, "Kecilkan", tint = Ink.Muted, modifier = Modifier.size(16.dp).clickable { onMinimize() })
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.Close, "Tutup", tint = Ink.Danger, modifier = Modifier.size(16.dp).clickable { onClose() })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MiniAppShortcuts.forEach { s -> Chip(s.label, s.id == shortcut.id) { onSwitch(s) } }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) { canGoBack = view.canGoBack() }
                        }
                        webViewRef = this
                    }
                },
                update = { view ->
                    if (loadedId != shortcut.id) { view.loadUrl(shortcut.webUrl); loadedId = shortcut.id }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.ArrowBack, "Mundur", tint = if (canGoBack) Ink.Text else Ink.Line,
                modifier = Modifier.size(18.dp).clickable(enabled = canGoBack) { webViewRef?.goBack() }
            )
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Rounded.Refresh, "Muat ulang", tint = Ink.Text, modifier = Modifier.size(18.dp).clickable { webViewRef?.reload() })
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(22.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, amount -> change.consume(); onResize(amount.x.roundToInt(), amount.y.roundToInt()) },
                            onDragEnd = { onResizeEnd() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.OpenInFull, "Ubah ukuran", tint = Ink.Muted, modifier = Modifier.size(15.dp)) }
        }
    }
}
