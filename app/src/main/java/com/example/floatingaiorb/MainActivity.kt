package com.example.floatingaiorb

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

private data class ChatItem(val role: String, val text: String, val image: Boolean = false)

class MainActivity : ComponentActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private val executor = Executors.newSingleThreadExecutor()

    private val prefs by lazy { getSharedPreferences("orb", Context.MODE_PRIVATE) }
    private var cameraResult: ((Bitmap?) -> Unit)? = null

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START_CAPTURE
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        } else Toast.makeText(this, "Screen capture dibatalkan", Toast.LENGTH_SHORT).show()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> cameraResult?.invoke(bitmap); cameraResult = null }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null) else Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        setContent {
            OrbTheme {
                AppHome(
                    apiKey = prefs.getString("key", "") ?: "",
                    model = prefs.getString("model", AIClient.DEFAULT_MODEL) ?: AIClient.DEFAULT_MODEL,
                    endpoint = prefs.getString("endpoint", AIClient.DEFAULT_ENDPOINT) ?: AIClient.DEFAULT_ENDPOINT,
                    onSaveSettings = { key, model, endpoint ->
                        prefs.edit().putString("key", key).putString("model", model).putString("endpoint", endpoint).apply()
                    },
                    onOverlay = { ensureOverlayAndShow() },
                    onCapture = { requestCapture() },
                    onStopCapture = { startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP)) },
                    onCamera = { captureCameraForAI() },
                    apiCall = { key, model, endpoint, text, bitmap ->
                        executor.execute {
                            try {
                                val r = AIClient.chat(key, endpoint, model, text, bitmap)
                                runOnUiThread { AppBus.reply?.invoke(r.text) }
                            } catch (e: Exception) {
                                runOnUiThread { AppBus.reply?.invoke("Error: ${e.message ?: "permintaan gagal"}") }
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) startOrbOnly()
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }

    private fun startOrbOnly() {
        runCatching {
            startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_SHOW_ORB))
        }
    }

    private fun ensureOverlayAndShow() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else startOrbOnly()
    }

    private fun requestCapture() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun captureCameraForAI() {
        cameraResult = { bitmap ->
            if (bitmap != null) {
                AppBus.cameraBitmap?.recycle()
                AppBus.cameraBitmap = bitmap
                Toast.makeText(this, "Foto kamera siap dianalisis di chat", Toast.LENGTH_SHORT).show()
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch(null)
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }
}

private object AppBus { var reply: ((String) -> Unit)? = null; var cameraBitmap: Bitmap? = null }

@Composable
private fun OrbTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8B5CF6), secondary = Color(0xFF22D3EE), background = Color(0xFF070A12), surface = Color(0xFF101522)), content = content)
}

@Composable
private fun AppHome(
    apiKey: String,
    model: String,
    endpoint: String,
    onSaveSettings: (String, String, String) -> Unit,
    onOverlay: () -> Unit,
    onCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onCamera: () -> Unit,
    apiCall: (String, String, String, String, Bitmap?) -> Unit
) {
    var key by remember { mutableStateOf(apiKey) }
    var selectedModel by remember { mutableStateOf(model) }
    var selectedEndpoint by remember { mutableStateOf(endpoint) }
    var input by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(listOf(ChatItem("ai", "Halo. Aku Floating AI Orb. Coba chat dulu, lalu pakai kamera atau screen capture untuk memberiku mata."))) }
    var sending by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val cameraImage = AppBus.cameraBitmap

    fun send(text: String, image: Bitmap? = null) {
        if (text.isBlank() || key.isBlank()) return
        items = items + ChatItem("user", text, image != null)
        input = ""; sending = true
        AppBus.reply = { answer -> items = items + ChatItem("ai", answer); sending = false }
        apiCall(key, selectedModel, selectedEndpoint, text, image)
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0E19), Color(0xFF080B12))))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Color(0xFF17112B), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text("✦", color = Color(0xFFB99AFF), style = MaterialTheme.typography.headlineSmall) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Floating AI Orb", style = MaterialTheme.typography.titleLarge); Text("AI • Vision • Overlay", color = Color(0xFF8D98AD), style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { showSettings = !showSettings }) { Text("SETUP") }
        }
        AnimatedVisibility(showSettings) {
            Column(Modifier.padding(horizontal = 16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("Groq / OpenAI API key") }, singleLine = true)
                OutlinedTextField(selectedModel, { selectedModel = it }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
                OutlinedTextField(selectedEndpoint, { selectedEndpoint = it }, Modifier.fillMaxWidth(), label = { Text("Endpoint") }, singleLine = true)
                Button(onClick = { onSaveSettings(key, selectedModel, selectedEndpoint) }, modifier = Modifier.fillMaxWidth()) { Text("Simpan pengaturan") }
                Text("Untuk Groq, model vision yang tersedia saat ini antara lain qwen/qwen3.6-27b. Jangan taruh API key permanen di source APK.", color = Color(0xFF8893A7), style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOverlay, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Orb") }
            Button(onClick = onCapture, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Screen") }
            Button(onClick = onCamera, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Kamera") }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.role == "user") Arrangement.End else Arrangement.Start) {
                    Surface(shape = RoundedCornerShape(18.dp), color = if (item.role == "user") Color(0xFF5B35B8) else Color(0xFF141B2A), modifier = Modifier.widthIn(max = 330.dp).shadow(2.dp, RoundedCornerShape(18.dp))) {
                        Text((if (item.image) "📷 Foto • " else "") + item.text, Modifier.padding(14.dp))
                    }
                }
            }
            if (sending) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { Text("AI sedang berpikir…", color = Color(0xFF9BA6BB), modifier = Modifier.padding(8.dp)) } }
        }

        if (cameraImage != null) Text("Foto kamera tersimpan. Kirim pertanyaan untuk menganalisis gambar ini.", color = Color(0xFF9BA6BB), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Tanya AI apa saja…") }, maxLines = 4)
            Spacer(Modifier.width(8.dp))
            Button(onClick = { send(input, AppBus.cameraBitmap) }, enabled = !sending && key.isNotBlank() && input.isNotBlank(), shape = RoundedCornerShape(16.dp)) { Text("➤") }
        }
    }
}
