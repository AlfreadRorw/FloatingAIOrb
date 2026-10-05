package com.alfread.alflauncher.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.alfread.alflauncher.apps.AppInfo
import com.alfread.alflauncher.settings.SettingsActivity
import com.alfread.alflauncher.ui.AlfTheme
import com.alfread.alflauncher.ui.GlassSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LauncherActivity : ComponentActivity() {
    private val vm by viewModels<LauncherViewModel>()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            vm.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerPackageReceiver()
        setContent {
            val settings by vm.settings.collectAsState()
            val dark = when (settings.darkMode) {
                0 -> false
                1 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            AlfTheme(dark) {
                LauncherScreen(vm, dark)
            }
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        super.onDestroy()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherScreen(vm: LauncherViewModel, dark: Boolean) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsState()
    val settings by vm.settings.collectAsState()
    val dockPackages by vm.dock.collectAsState()
    var drawer by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf<AppInfo?>(null) }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, true) }
    }

    val time = remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            time.value = Date()
            kotlinx.coroutines.delay(1000)
        }
    }
    val dateText = remember(time.value, settings.clock24h) {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(time.value)
    }
    val clockPattern = if (settings.clock24h) "HH:mm" else "h:mm"
    val clockText = remember(time.value, settings.clock24h) {
        SimpleDateFormat(clockPattern, Locale.getDefault()).format(time.value)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF080808) else Color(0xFFECECEC))
            .systemBarsPadding()
            .pointerInput(drawer) {
                detectDragGestures(
                    onDragEnd = {},
                    onDragCancel = {},
                    onDrag = { change, amount ->
                        change.consume()
                        if (!drawer && amount.y < -18f) drawer = true
                        if (drawer && amount.y > 18f) drawer = false
                    }
                )
            }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(22.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(clockText, fontSize = 46.sp, color = if (dark) Color.White else Color.Black)
                Text(dateText, fontSize = 14.sp, color = if (dark) Color.White.copy(.72f) else Color.Black.copy(.65f))
            }
            Spacer(Modifier.height(32.dp))

            val dockApps = dockPackages.mapNotNull { p -> apps.find { it.packageName == p } }
            val homeApps = apps.filterNot { a -> dockApps.any { it.packageName == a.packageName } }
            val pageSize = settings.columns * settings.rows
            val pages = maxOf(1, (homeApps.size + pageSize - 1) / pageSize)
            page = page.coerceIn(0, pages - 1)
            val current = homeApps.drop(page * pageSize).take(pageSize)

            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.columns),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(current, key = { it.packageName }) { app ->
                    AppTile(
                        app = app,
                        settings = settings,
                        onClick = { vm.launch(app) },
                        onLongPress = { dragging = app; editMode = true }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages) { i ->
                    Box(
                        Modifier.padding(3.dp).size(if (i == page) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (i == page) Color.White else Color.White.copy(.35f))
                            .pointerInput(Unit) { detectTapGestures { page = i } }
                    )
                }
            }

            GlassSurface(
                Modifier.fillMaxWidth().height(76.dp),
                dark = dark,
                alpha = settings.dockOpacity / 100f,
                radius = 28
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dockApps.take(settings.dockSlots).forEach { app ->
                        AppIcon(
                            app = app,
                            size = settings.iconSize.dp,
                            onClick = { vm.launch(app) },
                            onLongPress = { dragging = app; editMode = true }
                        )
                    }
                    if (dockApps.isEmpty()) {
                        Icon(Icons.Default.Apps, "App Drawer", tint = Color.White.copy(.9f), modifier = Modifier.size(30.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (editMode) {
            GlassSurface(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                dark = dark, alpha = .86f, radius = 22
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                    IconButton(onClick = { context.startActivity(Intent(context, com.alfread.alflauncher.widgets.WidgetPickerActivity::class.java)) }) {
                        Icon(Icons.Default.Widgets, "Widgets", tint = Color.White)
                    }
                    IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                    }
                    IconButton(onClick = { editMode = false; dragging = null }) {
                        Icon(Icons.Default.Add, "Done", tint = Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = drawer,
            enter = fadeIn(spring()),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            GlassSurface(
                Modifier.fillMaxSize().padding(10.dp),
                dark = dark, alpha = .92f, radius = 32
            ) {
                Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = Color.White.copy(.75f))
                        androidx.compose.material3.OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search applications...", color = Color.White.copy(.55f)) },
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppTile(app, settings, onClick = { vm.launch(app) }, onLongPress = { dragging = app; editMode = true })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    app: AppInfo,
    settings: com.alfread.alflauncher.data.LauncherSettings,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        AppIcon(app, settings.iconSize.dp, onClick, onLongPress)
        if (settings.labels) {
            Text(
                app.label,
                color = Color.White.copy(.9f),
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun AppIcon(app: AppInfo, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, onLongPress: () -> Unit) {
    val icon = remember(app.packageName) { app.icon }
    Box(
        Modifier.size(size)
            .pointerInput(app.packageName) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.rememberDrawablePainter(icon),
            contentDescription = app.label,
            modifier = Modifier.fillMaxSize()
        )
    }
}
