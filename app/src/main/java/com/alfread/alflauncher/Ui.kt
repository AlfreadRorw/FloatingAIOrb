package com.alfread.alflauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val label = TextStyle(color = Color.White, fontSize = 11.sp, shadow = Shadow(Color.Black.copy(.6f), Offset(0f, 1f), 4f))

@Composable
fun Glass(
    modifier: Modifier = Modifier, radius: androidx.compose.ui.unit.Dp = 28.dp,
    alpha: Float = .28f, dark: Boolean, content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    val base = if (dark) Color.Black else Color.White
    Box(
        modifier.shadow(18.dp, shape, ambientColor = Color.Black.copy(.3f), spotColor = Color.Black.copy(.35f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(base.copy(alpha = (alpha * 1.3f).coerceAtMost(.95f)), base.copy(alpha = alpha * .7f))))
            .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(.6f), Color.White.copy(.08f))), shape),
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(a: AppInfo, onClick: () -> Unit, showLabel: Boolean, menu: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) .86f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "press")
    var open by remember { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.scale(s).defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .combinedClickable(src, null, onClick = onClick, onLongClick = { open = true })
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(a.icon, a.label, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
            if (showLabel) Text(a.label, style = label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
        DropdownMenu(open, { open = false }) {
            menu.forEach { (t, f) -> DropdownMenuItem(text = { Text(t) }, onClick = { open = false; f() }) }
        }
    }
}

@Composable
fun ClockView(h24: Boolean) {
    val fmt = if (h24) "HH:mm" else "h:mm"
    val time by produceState("", h24) {
        while (true) {
            val now = System.currentTimeMillis()
            value = SimpleDateFormat(fmt, Locale.getDefault()).format(Date(now))
            delay(60_000 - now % 60_000 + 50)
        }
    }
    val date = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, style = TextStyle(color = Color.White, fontSize = 64.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light, shadow = Shadow(Color.Black.copy(.4f), Offset(0f, 2f), 8f)))
        Text(date, style = label.copy(fontSize = 16.sp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherApp(vm: LauncherVM, homeSignal: Int, requestDefault: () -> Unit) {
    val dark = when (vm.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    var drawer by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(homeSignal) { drawer = false; settings = false }
    BackHandler(drawer || settings) { if (settings) settings = false else drawer = false }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(vm.dim)).systemBarsPadding()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -120f) drawer = true }) { _, d -> total += d }
                }
        ) {
            val perPage = 20
            val pages = maxOf(1, (vm.home.size + perPage - 1) / perPage)
            val pager = rememberPagerState { pages }
            Column(Modifier.fillMaxSize()) {
                ClockView(vm.h24)
                HorizontalPager(pager, Modifier.weight(1f).padding(top = 16.dp)) { page ->
                    val items = vm.home.drop(page * perPage).take(perPage).mapNotNull { vm.byPkg(it) }
                    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        items.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                row.forEach { a ->
                                    AppIcon(a, { vm.launch(a) }, true,
                                        listOf("Remove from Home" to { vm.removeHome(a) }, "Uninstall" to { vm.uninstall(a) }),
                                        Modifier.weight(1f))
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        if (items.isEmpty()) Text("Swipe up, long-press an app, and choose Add to Home.", style = label.copy(fontSize = 14.sp), modifier = Modifier.padding(24.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(pages) { i ->
                        val w by animateDpAsState(if (pager.currentPage == i) 18.dp else 7.dp, label = "dot")
                        Box(Modifier.padding(3.dp).height(7.dp).width(w).clip(CircleShape).background(Color.White.copy(if (pager.currentPage == i) .95f else .45f)))
                    }
                }
                Glass(Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(86.dp), 32.dp, vm.opacity, dark) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        vm.dock.take(vm.dockCount).mapNotNull { vm.byPkg(it) }.forEach { a ->
                            AppIcon(a, { vm.launch(a) }, false, listOf(
                                "Move left" to { vm.moveDock(a, -1) }, "Move right" to { vm.moveDock(a, 1) },
                                "Remove from Dock" to { vm.removeDock(a) }))
                        }
                    }
                }
            }
            IconButton({ settings = true }, Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Settings, "Settings", tint = Color.White)
            }
            AnimatedVisibility(drawer, enter = slideInVertically { it / 3 } + fadeIn(), exit = slideOutVertically { it / 3 } + fadeOut()) {
                Drawer(vm, dark, { drawer = false }) { msg -> android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show() }
            }
            AnimatedVisibility(settings, enter = fadeIn() + scaleIn(initialScale = .94f), exit = fadeOut() + scaleOut(targetScale = .94f)) {
                SettingsPanel(vm, dark, requestDefault) { settings = false }
            }
        }
    }
}

@Composable
fun Drawer(vm: LauncherVM, dark: Boolean, close: () -> Unit, toast: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, vm.apps.size) { vm.apps.filter { it.label.contains(q, ignoreCase = true) } }
    Glass(Modifier.fillMaxSize(), 0.dp, .78f, dark) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search applications...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") }, shape = RoundedCornerShape(20.dp))
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(shown, key = { it.pkg }) { a ->
                    AppIcon(a, { close(); vm.launch(a) }, true, listOf(
                        "Add to Home" to { vm.addHome(a) },
                        "Add to Dock" to { if (!vm.addDock(a)) toast("Dock is full") },
                        "Uninstall" to { vm.uninstall(a) }), Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsPanel(vm: LauncherVM, dark: Boolean, requestDefault: () -> Unit, close: () -> Unit) {
    val tc = if (dark) Color.White else Color.Black
    Glass(Modifier.fillMaxSize(), 0.dp, .85f, dark) {
        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("Settings", color = tc, fontSize = 28.sp)
            Spacer(Modifier.height(16.dp))
            Button(requestDefault) { Text("Set as default launcher") }
            Spacer(Modifier.height(16.dp))
            Text("Dock icons: ${vm.dockCount}", color = tc)
            Slider(vm.dockCount.toFloat(), { vm.dockCount = it.toInt(); vm.save() }, valueRange = 4f..8f, steps = 3)
            Text("Dock opacity", color = tc)
            Slider(vm.opacity, { vm.opacity = it; vm.save() }, valueRange = 0.05f..0.8f)
            Text("Wallpaper dim", color = tc)
            Slider(vm.dim, { vm.dim = it; vm.save() }, valueRange = 0f..0.6f)
            Text("Theme", color = tc)
            Row { listOf("system", "light", "dark").forEach { t ->
                FilterChip(vm.theme == t, { vm.theme = t; vm.save() }, { Text(t) }, Modifier.padding(end = 8.dp)) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("24-hour clock", color = tc, modifier = Modifier.weight(1f))
                Switch(vm.h24, { vm.h24 = it; vm.save() })
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton({
                vm.home.clear(); vm.dock.clear(); vm.save()
            }) { Text("Reset layout") }
            Spacer(Modifier.height(8.dp))
            TextButton(close) { Text("Done") }
        }
    }
}
