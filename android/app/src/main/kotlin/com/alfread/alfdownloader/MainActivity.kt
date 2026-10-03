package com.alfread.alfdownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfdownloader.data.HistoryStore
import com.alfread.alfdownloader.model.Job
import com.alfread.alfdownloader.model.MediaInfo
import com.alfread.alfdownloader.network.Api
import com.alfread.alfdownloader.termux.TermuxRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)
private val Surface = Color(0xFF111111)
private val Surface2 = Color(0xFF181818)
private val Gray = Color(0xFF9E9E9E)
private val Line = Color(0xFF2B2B2B)

private enum class Tab { DOWNLOAD, HISTORY, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { Api() }
    val history = remember { HistoryStore(context) }
    val termux = remember { TermuxRunner(context) }

    var tab by remember { mutableStateOf(Tab.DOWNLOAD) }
    var serverOnline by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("best") }
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var localHistory by remember { mutableStateOf(history.read()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var autoStart by remember { mutableStateOf(context.getSharedPreferences("settings", 0).getBoolean("autoStart", true)) }

    fun saveAutoStart(value: Boolean) {
        autoStart = value
        context.getSharedPreferences("settings", 0).edit().putBoolean("autoStart", value).apply()
    }

    fun refreshHealth() {
        scope.launch {
            serverOnline = runCatching { api.health().ok }.getOrDefault(false)
        }
    }

    LaunchedEffect(Unit) {
        if (autoStart && termux.isInstalled()) {
            delay(700)
            if (!runCatching { api.health().ok }.getOrDefault(false)) {
                termux.startServer()
            }
        }
        while (isActive) {
            serverOnline = runCatching { api.health().ok }.getOrDefault(false)
            if (serverOnline) {
                jobs = runCatching { api.jobs() }.getOrDefault(emptyList())
                jobs.filter { it.status == "completed" }.forEach {
                    history.add(it)
                }
                localHistory = history.read()
            }
            delay(2000)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Black,
            surface = Surface,
            primary = White,
            onPrimary = Black,
            onBackground = White,
            onSurface = White,
            outline = Line
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Black) {
            Column(Modifier.fillMaxSize()) {
                when (tab) {
                    Tab.DOWNLOAD -> DownloadScreen(
                        serverOnline = serverOnline,
                        url = url,
                        onUrl = { url = it },
                        quality = quality,
                        onQuality = { quality = it },
                        info = info,
                        jobs = jobs,
                        busy = busy,
                        onCheck = ::refreshHealth,
                        onInfo = {
                            if (url.isBlank()) message = "Masukkan URL dulu" else scope.launch {
                                busy = true
                                info = runCatching { api.info(url.trim()) }.onFailure { message = it.message ?: "Gagal membaca URL" }.getOrNull()
                                busy = false
                            }
                        },
                        onDownload = {
                            if (!serverOnline) { message = "Server Termux belum aktif" }
                            else scope.launch {
                                busy = true
                                runCatching { api.createJob(url.trim(), quality) }
                                    .onSuccess { message = "Download ditambahkan ke antrean" }
                                    .onFailure { message = it.message ?: "Gagal membuat job" }
                                busy = false
                            }
                        },
                        onCancel = { id -> scope.launch { runCatching { api.cancel(id) } } }
                    )
                    Tab.HISTORY -> HistoryScreen(localHistory, onClear = { history.clear(); localHistory = emptyList() })
                    Tab.SETTINGS -> SettingsScreen(
                        serverOnline = serverOnline,
                        termuxInstalled = termux.isInstalled(),
                        autoStart = autoStart,
                        onAutoStart = ::saveAutoStart,
                        onStartTermux = {
                            termux.startServer().onFailure { message = it.message ?: "Tidak bisa menjalankan Termux" }
                        },
                        onCheck = ::refreshHealth
                    )
                }
                Dock(tab) { tab = it }
            }
        }
    }

    if (message != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            title = { Text("ALF Downloader") },
            text = { Text(message ?: "") },
            containerColor = Surface
        )
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = Gray, fontSize = 13.sp)
    }
}

@Composable
private fun StatusPill(online: Boolean) {
    Surface(color = if (online) White else Surface2, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (online) Black else Gray, RoundedCornerShape(50)))
            Spacer(Modifier.width(8.dp))
            Text(if (online) "SERVER ONLINE" else "SERVER OFFLINE", color = if (online) Black else White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DownloadScreen(
    serverOnline: Boolean, url: String, onUrl: (String) -> Unit, quality: String, onQuality: (String) -> Unit,
    info: MediaInfo?, jobs: List<Job>, busy: Boolean, onCheck: () -> Unit, onInfo: () -> Unit, onDownload: () -> Unit, onCancel: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("ALF Downloader", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Monochrome media hub", color = Gray, fontSize = 13.sp) }
                StatusPill(serverOnline)
            }
        }
        item {
            Card(Modifier.padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("VIDEO URL", color = Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url, onValueChange = onUrl, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Paste YouTube / TikTok URL", color = Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = White, unfocusedBorderColor = Line, cursorColor = White,
                            focusedTextColor = White, unfocusedTextColor = White
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onCheck, modifier = Modifier.weight(1f)) { Text("CHECK SERVER") }
                        OutlinedButton(onClick = onInfo, enabled = url.isNotBlank() && !busy, modifier = Modifier.weight(1f)) { Text("GET INFO") }
                    }
                }
            }
        }
        if (info != null) {
            item {
                Card(Modifier.padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = Surface2), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("VIDEO INFO", color = Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(info.title.ifBlank { "Untitled" }, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Text(listOfNotNull(info.uploader.takeIf { it.isNotBlank() }, info.duration?.let { formatDuration(it) }).joinToString("  ·  "), color = Gray, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("QUALITY", color = Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                QualitySelector(quality, onQuality)
            }
        }
        item {
            Button(
                onClick = onDownload, enabled = serverOnline && url.isNotBlank() && !busy, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black)
            ) { Text(if (busy) "WORKING..." else "DOWNLOAD", fontWeight = FontWeight.Black) }
        }
        if (jobs.isNotEmpty()) {
            item { Text("QUEUE", Modifier.padding(horizontal = 20.dp, vertical = 2.dp), color = Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            items(jobs.filter { it.status !in setOf("completed", "error", "cancelled") }, key = { it.id }) { job ->
                JobCard(job, onCancel)
            }
            }
        }
    }
}

@Composable
private fun QualitySelector(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf("best" to "Best", "1080" to "1080p", "720" to "720p", "480" to "480p", "360" to "360p", "audio" to "Audio")
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(labels[value] ?: "Best") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { onChange(key); expanded = false }) }
        }
    }
}

@Composable
private fun JobCard(job: Job, onCancel: (String) -> Unit) {
    Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(job.title ?: job.url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { (job.progress / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = White, trackColor = Line)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${job.progress.toInt()}%", color = Gray, fontSize = 12.sp)
                Text(job.status.uppercase(), color = Gray, fontSize = 12.sp)
                if (job.status in setOf("queued", "downloading")) TextButton(onClick = { onCancel(job.id) }) { Text("CANCEL") }
            }
        }
    }
}

@Composable
private fun HistoryScreen(items: List<Job>, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("History", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Completed downloads", color = Gray, fontSize = 13.sp) }
            TextButton(onClick = onClear, enabled = items.isNotEmpty()) { Text("CLEAR") }
        }
        if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Belum ada riwayat", color = Gray) }
        else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { job ->
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Text(job.title ?: "Untitled", fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Text(job.filename ?: "Downloaded", color = Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(serverOnline: Boolean, termuxInstalled: Boolean, autoStart: Boolean, onAutoStart: (Boolean) -> Unit, onStartTermux: () -> Unit, onCheck: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Settings", "ALF Downloader") }
        item { SettingCard("Termux", if (termuxInstalled) "Installed" else "Not installed") }
        item { SettingCard("Server", if (serverOnline) "127.0.0.1:8080 · Online" else "127.0.0.1:8080 · Offline") }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onStartTermux, enabled = termuxInstalled, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("START TERMUX SERVER", fontWeight = FontWeight.Black)
                }
                OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("CHECK SERVER") }
            }
        }
        item {
            SettingCard("Download folder", "/storage/emulated/0/Download/ALF Downloader")
        }
        item {
            SettingCard("Integration", "Termux RUN_COMMAND")
        }
        item {
            Card(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(15.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-start server", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Coba menyalakan server Termux saat aplikasi dibuka", color = Gray, fontSize = 12.sp)
                    }
                    Switch(checked = autoStart, onCheckedChange = onAutoStart)
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Text("FIRST-TIME SETUP", color = Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("""1. Install Termux.
2. Run termux-setup-storage.
3. Install the server files from the bundled termux-server folder.
4. Set allow-external-apps=true in ~/.termux/termux.properties.
5. Grant ALF Downloader the Run commands in Termux permission.""", color = White, lineHeight = 20.sp, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, value: String) {
    Card(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(15.dp)) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(value, color = Gray, fontSize = 12.sp) }
    }
}

@Composable
private fun Dock(selected: Tab, onSelected: (Tab) -> Unit) {
    NavigationBar(containerColor = Black, contentColor = White) {
        NavigationBarItem(selected = selected == Tab.DOWNLOAD, onClick = { onSelected(Tab.DOWNLOAD) }, icon = { Icon(Icons.Default.Download, null) }, label = { Text("Download") })
        NavigationBarItem(selected = selected == Tab.HISTORY, onClick = { onSelected(Tab.HISTORY) }, icon = { Icon(Icons.Default.History, null) }, label = { Text("History") })
        NavigationBarItem(selected = selected == Tab.SETTINGS, onClick = { onSelected(Tab.SETTINGS) }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
    }
}

private fun formatDuration(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
