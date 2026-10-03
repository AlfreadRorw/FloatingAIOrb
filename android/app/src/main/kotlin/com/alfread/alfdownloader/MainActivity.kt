package com.alfread.alfdownloader

import android.Manifest
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alfread.alfdownloader.model.isActive
import com.alfread.alfdownloader.notify.Notifier
import com.alfread.alfdownloader.ui.*

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)
    private var shared by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        Notifier.init(this)
        takeShared(intent)
        setContent {
            App(resumeTick = resumeTick, shared = shared, onSharedConsumed = { shared = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeShared(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private fun takeShared(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        }
    }
}

@Composable
private fun App(resumeTick: Int, shared: String?, onSharedConsumed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = remember { AppController(context, scope) }
    val accent = AccentOptions[c.prefs.accent.coerceIn(0, AccentOptions.lastIndex)].color

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    LaunchedEffect(Unit) {
        val missing = buildList {
            if (c.termux.isInstalled() && !c.termux.hasPermission()) add("com.termux.permission.RUN_COMMAND")
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }

    LaunchedEffect(Unit) { c.boot() }

    LaunchedEffect(resumeTick) {
        kotlinx.coroutines.delay(350)
        c.onResume()
    }

    LaunchedEffect(shared) {
        if (shared != null) {
            c.handleShared(shared)
            onSharedConsumed()
        }
    }

    // info otomatis saat link berubah
    LaunchedEffect(c.url, c.online, c.prefs.autoInfo, c.playlist) {
        c.info = null
        c.infoError = null
        if (c.prefs.autoInfo && c.online && looksLikeUrl(c.url)) {
            kotlinx.coroutines.delay(600)
            c.fetchInfo()
        }
    }

    // unduh otomatis (share / clipboard / menunggu server)
    LaunchedEffect(c.pendingAuto, c.online) {
        if (c.pendingAuto && c.online) {
            c.pendingAuto = false
            c.download()
        }
    }

    val floating = c.prefs.dockStyle == 0
    val bottomPad = if (floating) 118.dp else 16.dp

    AlfTheme(accent) {
        Surface(Modifier.fillMaxSize(), color = Ink.Bg) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0C0C0F), Ink.Bg)))) {
                Box(
                    Modifier.align(Alignment.TopCenter).offset(y = (-170).dp).size(380.dp)
                        .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent)), CircleShape)
                )
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).imePadding()) {
                        Crossfade(c.tab, label = "tab") { tab ->
                            when (tab) {
                                Tab.DOWNLOAD -> DownloadScreen(c, bottomPad)
                                Tab.LIBRARY -> LibraryScreen(c, bottomPad)
                                Tab.SETTINGS -> SettingsScreen(c, bottomPad)
                            }
                        }
                    }
                    if (!floating) {
                        Dock(c.tab, { c.tab = it }, c.prefs, c.jobs.count { it.isActive })
                    }
                }
                if (floating) {
                    Dock(
                        c.tab, { c.tab = it }, c.prefs, c.jobs.count { it.isActive },
                        Modifier.align(Alignment.BottomCenter)
                    )
                }
                ToastHost(
                    c.message, { c.message = null },
                    Modifier.align(Alignment.BottomCenter).padding(bottom = if (floating) 104.dp else 80.dp)
                )
            }
        }
    }
}
