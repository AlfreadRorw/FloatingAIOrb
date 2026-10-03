package com.alfread.alfdownloader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val BASE_URL = "http://127.0.0.1:8080"
private const val SERVER_SCRIPT =
    "/data/data/com.termux/files/home/Github/termux-server/start.sh"
private const val SERVER_WORKDIR =
    "/data/data/com.termux/files/home/Github/termux-server"

data class VideoInfo(
    val title: String,
    val uploader: String?,
    val duration: Long?,
    val thumbnail: String?
)

data class HistoryItem(
    val id: String,
    val url: String,
    val quality: String,
    val status: String,
    val filename: String?,
    val error: String?,
    val completedAt: Long?
)

data class JobState(
    val id: String,
    val status: String,
    val progress: Float,
    val message: String,
    val filename: String?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AlfDownloaderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlfDownloaderApp()
                }
            }
        }
    }
}

@Composable
fun AlfDownloaderTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        secondary = Color(0xFFAAAAAA),
        background = Color.Black,
        onBackground = Color.White,
        surface = Color(0xFF111111),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1B1B1B),
        onSurfaceVariant = Color(0xFFBBBBBB),
        outline = Color(0xFF444444)
    )

    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}

@Composable
fun AlfDownloaderApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var serverOnline by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(emptyList<HistoryItem>()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            serverOnline = Api.health()
            if (selectedTab == 1) {
                history = Api.history()
            }
            delay(4000)
        }
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0B0B0B),
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Status") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        history = Api.history()
                    },
                    icon = { Icon(Icons.Default.History, null) },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(
                modifier = Modifier.padding(padding),
                serverOnline = serverOnline,
                onServerRefresh = {
                    serverOnline = Api.health()
                },
                onStartTermux = {
                    startTermuxServer(context)
                    Toast.makeText(
                        context,
                        "Perintah start server dikirim ke Termux",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onHistoryChanged = {
                    history = Api.history()
                }
            )

            1 -> HistoryScreen(
                modifier = Modifier.padding(padding),
                items = history,
                onRefresh = {
                    history = Api.history()
                },
                onClear = {
                    Api.clearHistory()
                    history = emptyList()
                }
            )

            else -> SettingsScreen(
                modifier = Modifier.padding(padding),
                onStartTermux = {
                    startTermuxServer(context)
                    Toast.makeText(
                        context,
                        "Perintah start server dikirim ke Termux",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier,
    serverOnline: Boolean,
    onServerRefresh: () -> Unit,
    onStartTermux: () -> Unit,
    onHistoryChanged: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var selectedQuality by remember { mutableStateOf("720p") }
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var infoError by remember { mutableStateOf("") }
    var loadingInfo by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<JobState?>(null) }
    var downloading by remember { mutableStateOf(false) }

    LaunchedEffect(job?.id) {
        val id = job?.id ?: return@LaunchedEffect
        while (downloading) {
            val latest = Api.job(id)
            if (latest != null) {
                job = latest
                if (latest.status == "completed" || latest.status == "error") {
                    downloading = false
                    onHistoryChanged()
                }
            }
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "ALF DOWNLOADER",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (serverOnline) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (serverOnline) Color.White else Color(0xFF888888)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (serverOnline) "SERVER ONLINE" else "SERVER OFFLINE",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onServerRefresh) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(5.dp))
                Text("Refresh")
            }
        }

        if (!serverOnline) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Termux server belum aktif.",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Jalankan server secara manual di Termux atau gunakan tombol di bawah."
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onStartTermux,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Terminal, null)
                        Spacer(Modifier.width(8.dp))
                        Text("START TERMUX SERVER")
                    }
                }
            }
        }

        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                info = null
                infoError = ""
                downloadError = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL") },
            leadingIcon = { Icon(Icons.Default.Link, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            placeholder = { Text("https://...") }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (url.isBlank()) {
                        infoError = "Masukkan URL terlebih dahulu."
                    } else {
                        loadingInfo = true
                        infoError = ""
                        Thread {
                            val result = Api.info(url)
                            android.os.Handler(mainLooper).post {
                                loadingInfo = false
                                if (result.first != null) {
                                    info = result.first
                                } else {
                                    infoError = result.second
                                }
                            }
                        }.start()
                    }
                },
                enabled = serverOnline && !loadingInfo,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                if (loadingInfo) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black
                    )
                } else {
                    Icon(Icons.Default.Info, null)
                }
                Spacer(Modifier.width(7.dp))
                Text("GET INFO")
            }

            OutlinedButton(
                onClick = {
                    val clip = LocalContext.current
                        .getSystemService(Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    val item = clip.primaryClip?.getItemAt(0)
                    if (item != null) {
                        url = item.text?.toString().orEmpty()
                    }
                },
                modifier = Modifier.weight(0.5f)
            ) {
                Icon(Icons.Default.ContentPaste, null)
            }
        }

        if (infoError.isNotBlank()) {
            ErrorBox(infoError)
        }

        info?.let { data ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        data.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    data.uploader?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("Uploader: $it", color = Color.LightGray)
                    }
                    data.duration?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("Duration: ${formatDuration(it)}", color = Color.LightGray)
                    }
                }
            }
        }

        Text(
            "QUALITY",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )

        val qualities = listOf("best", "1080p", "720p", "480p", "audio")

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            qualities.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    rowItems.forEach { q ->
                        val active = q == selectedQuality
                        OutlinedButton(
                            onClick = { selectedQuality = q },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (active) Color.White else Color.Transparent,
                                contentColor = if (active) Color.Black else Color.White
                            )
                        ) {
                            Text(q.uppercase())
                        }
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Button(
            onClick = {
                downloadError = ""
                Thread {
                    val result = Api.download(url, selectedQuality)
                    android.os.Handler(mainLooper).post {
                        if (result.first != null) {
                            job = result.first
                            downloading = true
                        } else {
                            downloadError = result.second
                        }
                    }
                }.start()
            },
            enabled = serverOnline && url.isNotBlank() && !downloading,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            )
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (downloading) "DOWNLOADING..." else "DOWNLOAD",
                fontWeight = FontWeight.Bold
            )
        }

        if (downloadError.isNotBlank()) {
            ErrorBox(downloadError)
        }

        job?.let { current ->
            DownloadProgressCard(current)
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "ALF Downloader bekerja melalui server lokal Termux. Pastikan kamu hanya mengunduh konten yang memang kamu berhak simpan.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun DownloadProgressCard(job: JobState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    job.status.uppercase(),
                    fontWeight = FontWeight.Bold
                )
            }

            LinearProgressIndicator(
                progress = { job.progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )

            Text("${job.progress.toInt()}%")
            Text(job.message, color = Color.LightGray)

            job.filename?.takeIf { it.isNotBlank() }?.let {
                Text("File: $it", color = Color.Gray)
            }
        }
    }
}

@Composable
fun ErrorBox(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171717)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(13.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Warning, null)
            Spacer(Modifier.width(8.dp))
            Text(message, color = Color(0xFFDDDDDD))
        }
    }
}

@Composable
fun HistoryScreen(
    modifier: Modifier,
    items: List<HistoryItem>,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "HISTORY",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(5.dp))
                Text("Refresh")
            }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = onClear) {
                Icon(Icons.Default.ClearAll, null)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Belum ada riwayat.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF111111)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                item.filename ?: "Download",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${item.quality.uppercase()} • ${item.status.uppercase()}",
                                color = Color.LightGray
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                item.url,
                                color = Color.Gray,
                                maxLines = 2
                            )
                            item.error?.let {
                                Spacer(Modifier.height(5.dp))
                                Text(it, color = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier,
    onStartTermux: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text(
            "SETTINGS",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )

        SettingCard(
            title = "Server",
            body = "http://127.0.0.1:8080"
        )

        SettingCard(
            title = "Download folder",
            body = "/storage/emulated/0/Download/ALF Downloader"
        )

        SettingCard(
            title = "Termux script",
            body = SERVER_SCRIPT
        )

        SettingCard(
            title = "Theme",
            body = "Monochrome black / white"
        )

        Button(
            onClick = onStartTermux,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            )
        ) {
            Icon(Icons.Default.Terminal, null)
            Spacer(Modifier.width(8.dp))
            Text("START TERMUX SERVER")
        }

        Text(
            "Jika tombol start tidak bekerja, jalankan server manual dari Termux dengan bash start.sh dan pastikan allow-external-apps=true.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun SettingCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(body, color = Color.LightGray)
        }
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}

fun startTermuxServer(context: Context) {
    try {
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setPackage("com.termux")
            putExtra("com.termux.RUN_COMMAND_PATH", SERVER_SCRIPT)
            putExtra("com.termux.RUN_COMMAND_WORKDIR", SERVER_WORKDIR)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        context.startService(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Termux tidak dapat dijalankan: ${e.message}",
            Toast.LENGTH_LONG
        ).show()
    }
}

object Api {

    fun health(): Boolean {
        return try {
            val response = request("GET", "/api/health", null)
            response.code == 200
        } catch (_: Exception) {
            false
        }
    }

    fun info(url: String): Pair<VideoInfo?, String> {
        return try {
            val body = JSONObject().put("url", url).toString()
            val response = request("POST", "/api/info", body)
            if (response.code != 200) {
                return null to parseError(response.body)
            }

            val json = JSONObject(response.body)
            val duration = if (json.isNull("duration")) {
                null
            } else {
                json.optLong("duration")
            }

            VideoInfo(
                title = json.optString("title", "Untitled"),
                uploader = json.optString("uploader", null),
                duration = duration,
                thumbnail = json.optString("thumbnail", null)
            ) to ""
        } catch (e: Exception) {
            null to (e.message ?: "Connection error")
        }
    }

    fun download(url: String, quality: String): Pair<JobState?, String> {
        return try {
            val body = JSONObject()
                .put("url", url)
                .put("quality", quality)
                .toString()

            val response = request("POST", "/api/download", body)

            if (response.code !in 200..299) {
                return null to parseError(response.body)
            }

            val json = JSONObject(response.body)
            val id = json.optString("job_id")

            JobState(
                id = id,
                status = "queued",
                progress = 0f,
                message = "Queued",
                filename = null
            ) to ""
        } catch (e: Exception) {
            null to (e.message ?: "Connection error")
        }
    }

    fun job(id: String): JobState? {
        return try {
            val response = request("GET", "/api/jobs/$id", null)
            if (response.code != 200) return null

            val json = JSONObject(response.body)
            val job = json.getJSONObject("job")

            JobState(
                id = job.optString("id"),
                status = job.optString("status"),
                progress = job.optDouble("progress", 0.0).toFloat(),
                message = job.optString("message", ""),
                filename = job.optString("filename", null)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun history(): List<HistoryItem> {
        return try {
            val response = request("GET", "/api/history", null)
            if (response.code != 200) return emptyList()

            val array = JSONObject(response.body).optJSONArray("items")
                ?: JSONArray()

            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        HistoryItem(
                            id = item.optString("id", "$i"),
                            url = item.optString("url", ""),
                            quality = item.optString("quality", "best"),
                            status = item.optString("status", ""),
                            filename = item.optString("filename", null),
                            error = item.optString("error", null),
                            completedAt = if (item.has("completed_at")) {
                                item.optLong("completed_at")
                            } else {
                                null
                            }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearHistory() {
        try {
            request("POST", "/api/history/clear", "{}")
        } catch (_: Exception) {
        }
    }

    private data class Response(
        val code: Int,
        val body: String
    )

    private fun request(
        method: String,
        path: String,
        body: String?
    ): Response {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 7000
            readTimeout = 120000
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "application/json")

            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val code = connection.responseCode
            val stream = if (code >= 400) {
                connection.errorStream
            } else {
                connection.inputStream
            }

            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            return Response(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseError(body: String): String {
        return try {
            JSONObject(body).optString("error", "Server error")
        } catch (_: Exception) {
            if (body.isBlank()) "Server error" else body.take(2000)
        }
    }
}
