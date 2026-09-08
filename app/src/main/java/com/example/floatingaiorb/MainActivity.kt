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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private data class ChatItem(val role: String, val text: String, val image: Boolean = false, val time: String = "")

class MainActivity : ComponentActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("orb", Context.MODE_PRIVATE) }
    private var pendingPhotoUri: Uri? = null
    private var voiceEngine: VoiceEngine? = null
    private var pendingVoiceAfterPermission = false

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START_CAPTURE
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            window.decorView.post { moveTaskToBack(true) }
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchFullCamera() else toast("Izin kamera ditolak")
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (pendingVoiceAfterPermission) {
                pendingVoiceAfterPermission = false
                ContextCompat.startForegroundService(this, Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_VOICE))
                window.decorView.post { moveTaskToBack(true) }
            } else voiceEngine?.startListening()
        } else {
            pendingVoiceAfterPermission = false
            toast("Izin mic ditolak. Voice belum bisa jalan.")
        }
    }
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingPhotoUri != null) {
            val bitmap = contentResolver.openInputStream(pendingPhotoUri!!)?.use { stream -> android.graphics.BitmapFactory.decodeStream(stream) }
            AppMemory.setCamera(bitmap)
        } else toast("Foto tidak berhasil diambil")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        voiceEngine = VoiceEngine(this, onState = { state -> AppMemory.voiceState = state }, onText = { text -> AppMemory.voiceText = text })
        setContent { OrbTheme { AppRoot() } }
        if (savedInstanceState == null && intent.getBooleanExtra("REQUEST_SCREEN", false)) {
            window.decorView.post { requestCapture() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("REQUEST_MIC", false)) {
            pendingVoiceAfterPermission = true
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                pendingVoiceAfterPermission = false
                ContextCompat.startForegroundService(this, Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_VOICE))
                window.decorView.post { moveTaskToBack(true) }
            } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (intent.getBooleanExtra("REQUEST_SCREEN", false)) {
            window.decorView.post { requestCapture() }
        }
    }


    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) startOrbOnly()
    }

    override fun onDestroy() {
        voiceEngine?.release()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun startOrbOnly() {
        runCatching { startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_SHOW_ORB)) }
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

    private fun launchFullCamera() {
        val file = File.createTempFile("orb_camera_", ".jpg", cacheDir)
        pendingPhotoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingPhotoUri?.let { takePicture.launch(it) }
    }

    private fun captureCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchFullCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    @Composable
    private fun AppRoot() {
        var showSettings by remember { mutableStateOf(false) }
        var activeTab by remember { mutableStateOf("Home") }
        val voiceState by remember { derivedStateOf { AppMemory.voiceState } }
        var apiKey by remember { mutableStateOf(prefs.getString("key", "").orEmpty()) }
        var model by remember { mutableStateOf(prefs.getString("model", AIClient.DEFAULT_MODEL).orEmpty()) }
        var endpoint by remember { mutableStateOf(prefs.getString("endpoint", AIClient.DEFAULT_ENDPOINT).orEmpty()) }
        var voicePreset by remember { mutableStateOf(VoiceEngine.Preset.valueOf(prefs.getString("voicePreset", VoiceEngine.Preset.KAWAII.name).orEmpty())) }
        var input by remember { mutableStateOf("") }
        var sending by remember { mutableStateOf(false) }
        var messages by remember {
            mutableStateOf(listOf(ChatItem("ai", "Halo! Aku Floating AI Orb. Aku bisa ngobrol, mendengar suara, melihat kamera, dan menganalisis layar kamu.", time = now())))
        }
        val cameraBitmap = AppMemory.cameraBitmap
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        LaunchedEffect(AppMemory.voiceText) {
            val voiceText = AppMemory.voiceText
            if (voiceText.isNotBlank()) {
                AppMemory.voiceText = ""
                input = voiceText
                sendMessage(voiceText, cameraBitmap, { result -> messages = messages + result }, { sending = it })
            }
        }
        LaunchedEffect(messages.size) { if (messages.isNotEmpty()) scope.launch { listState.animateScrollToItem(messages.lastIndex) } }

        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF060914), Color(0xFF0D1020), Color(0xFF080A12))))) {
            AnimatedBackground()
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Header(voiceState = voiceState)
                FeatureRow(
                    onOrb = { ensureOverlayAndShow() },
                    onScreen = { requestCapture() },
                    onCamera = { captureCamera() }
                )
                QuickActions(onSend = { q -> input = q })

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(messages) { _, item -> ChatBubble(item) }
                    if (sending) item { ThinkingBubble() }
                }

                if (cameraBitmap != null) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF151A29), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, null, tint = Color(0xFFB99AFF))
                            Spacer(Modifier.width(8.dp))
                            Text("Foto kamera siap. AI dapat melihat gambar ini saat kamu bertanya.", color = Color(0xFFD5DAE6), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { AppMemory.setCamera(null) }) { Text("Hapus") }
                        }
                    }
                }

                Composer(
                    input = input,
                    onInput = { input = it },
                    onSend = {
                        val text = input.trim()
                        if (text.isNotBlank() && !sending) {
                            input = ""
                            sendMessage(text, cameraBitmap, { messages = messages + it }, { sending = it })
                        }
                    },
                    voiceState = voiceState,
                    onMic = {
                        when (voiceState) {
                            VoiceEngine.State.LISTENING -> voiceEngine?.stopListening()
                            VoiceEngine.State.SPEAKING -> voiceEngine?.stopSpeaking()
                            else -> {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voiceEngine?.startListening()
                                else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    onSpeak = { messages.lastOrNull { it.role == "ai" }?.let { voiceEngine?.speak(it.text) } }
                )
                Spacer(Modifier.height(8.dp))
                BottomHotbar(
                    active = activeTab,
                    onHome = { activeTab = "Home"; showSettings = false },
                    onOrb = { activeTab = "Orb"; ensureOverlayAndShow() },
                    onScreen = { activeTab = "Screen"; requestCapture() },
                    onSettings = { activeTab = "Settings"; showSettings = true }
                )
            }

            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 74.dp, start = 12.dp, end = 12.dp)
            ) {
                SetupCard(
                    apiKey = apiKey,
                    model = model,
                    endpoint = endpoint,
                    voicePreset = voicePreset,
                    onKey = { apiKey = it }, onModel = { model = it }, onEndpoint = { endpoint = it },
                    onPreset = { voicePreset = it; voiceEngine?.setPreset(it) },
                    onSave = {
                        prefs.edit().putString("key", apiKey.trim()).putString("model", model.trim()).putString("endpoint", endpoint.trim()).putString("voicePreset", voicePreset.name).apply()
                        showSettings = false
                        toast("Pengaturan disimpan")
                    },
                    onClose = { showSettings = false }
                )
            }
        }
    }

    private fun sendMessage(
        text: String,
        image: Bitmap?,
        onResult: (ChatItem) -> Unit,
        onBusy: (Boolean) -> Unit
    ) {
        if (text.isBlank()) return
        val command = CommandEngine.parse(text)
        if (command.type != CommandEngine.Type.UNKNOWN) {
            val ok = if (command.type == CommandEngine.Type.TIKTOK_COMMENT && !CommandEngine.accessibilityEnabled(this)) false
            else CommandEngine.execute(this, command)
            val status = when (command.type) {
                CommandEngine.Type.OPEN_APP -> if (ok) "Membuka ${command.appName}." else "Aplikasi ${command.appName} tidak ditemukan."
                CommandEngine.Type.TIKTOK_COMMENT -> if (ok) "TikTok dibuka. AI Action Assist akan membantu membuka komentar dan mengetik teks. Sebelum komentar dikirim, kamu tetap harus menekan konfirmasi Kirim." else "Aktifkan AI Action Assist di Pengaturan Aksesibilitas terlebih dahulu."
                else -> ""
            }
            onResult(ChatItem("user", text, false, now()))
            onResult(ChatItem("ai", status, false, now()))
            voiceEngine?.speak(status)
            return
        }
        val key = prefs.getString("key", "").orEmpty()
        val model = prefs.getString("model", AIClient.DEFAULT_MODEL).orEmpty()
        val endpoint = prefs.getString("endpoint", AIClient.DEFAULT_ENDPOINT).orEmpty()
        if (key.isBlank()) { onResult(ChatItem("ai", AIClient.offlineReply(text), time = now())); return }
        onResult(ChatItem("user", text, image != null, now()))
        onBusy(true)
        executor.execute {
            val answer = runCatching { AIClient.chat(key, endpoint, model, text, image).text }.getOrElse { "${AIClient.offlineReply(text)}\n\nCatatan: koneksi AI sedang bermasalah." }
            runOnUiThread { onResult(ChatItem("ai", answer, time = now())); onBusy(false) }
        }
    }

    @Composable
    private fun AnimatedBackground() {
        val transition = rememberInfiniteTransition(label = "bg")
        val pulse by transition.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "pulse")
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.size(220.dp).align(Alignment.TopEnd).offset(x = 60.dp, y = 80.dp).alpha(pulse * 0.18f).clip(CircleShape).background(Color(0xFF8B5CF6)))
            Box(Modifier.size(170.dp).align(Alignment.BottomStart).offset(x = (-60).dp, y = 80.dp).alpha(pulse * 0.12f).clip(CircleShape).background(Color(0xFF22D3EE)))
        }
    }

    @Composable
    private fun Header(voiceState: VoiceEngine.State) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OrbLogo(Modifier.size(52.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Floating AI Orb", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                val status = when (voiceState) {
                    VoiceEngine.State.LISTENING -> "Lagi dengerin kamu"
                    VoiceEngine.State.SPEAKING -> "Lagi ngomong"
                    VoiceEngine.State.ERROR -> "Voice lagi bermasalah"
                    else -> "Siap bantu kapan aja"
                }
                Text(status, color = Color(0xFFA9B1C4), fontSize = 12.sp)
            }
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF171D2B)) {
                Text("V6", color = Color(0xFFCAB6FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }

    @Composable
    private fun OrbLogo(modifier: Modifier = Modifier) {
        val transition = rememberInfiniteTransition(label = "logo")
        val scale by transition.animateFloat(0.95f, 1.06f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "scale")
        Box(modifier.scale(scale).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFF24124C), Color(0xFF5C2AB6)))), contentAlignment = Alignment.Center) {
            Text("✦", color = Color(0xFFEDE7FF), fontSize = 29.sp)
        }
    }

    @Composable
    private fun FeatureRow(onOrb: () -> Unit, onScreen: () -> Unit, onCamera: () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeatureButton("Orb", "FLOAT", Icons.Default.GraphicEq, onOrb, Modifier.weight(1f))
            FeatureButton("Screen", "VISION", Icons.Default.Visibility, onScreen, Modifier.weight(1f))
            FeatureButton("Kamera", "VISION", Icons.Default.CameraAlt, onCamera, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("AI Action Assist • buka aplikasi & bantu isi teks") }
    }

    @Composable
    private fun FeatureButton(title: String, tag: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
        Surface(modifier = modifier.height(70.dp).clickable { onClick() }, shape = RoundedCornerShape(19.dp), color = Color(0xFF7848DD), shadowElevation = 7.dp) {
            Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                Text(tag, color = Color(0xFFDCCFFF), fontSize = 8.sp, letterSpacing = 1.sp)
            }
        }
    }

    @Composable
    private fun QuickActions(onSend: (String) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Apa yang kamu lihat?", "Buat ringkasan", "Bantu aku").forEach { q ->
                SuggestionChip(onClick = { onSend(q) }, label = { Text(q, maxLines = 1, fontSize = 11.sp) })
            }
        }
    }

    @Composable
    private fun ChatBubble(item: ChatItem) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.role == "user") Arrangement.End else Arrangement.Start) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (item.role == "user") Color(0xFF6133BE) else Color(0xFF141A29),
                border = if (item.role == "ai") androidx.compose.foundation.BorderStroke(1.dp, Color(0x332E3B58)) else null,
                modifier = Modifier.widthIn(max = 330.dp)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Text(if (item.role == "user") "Kamu" else "Orb", color = if (item.role == "user") Color(0xFFEAE0FF) else Color(0xFFBFA8FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (item.image) Text("Foto • ", color = Color(0xFFE7EAF2), fontSize = 11.sp)
                    Text(item.text, color = Color(0xFFF1F3F8), fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp))
                    if (item.time.isNotBlank()) Text(item.time, color = Color(0xFF8A95A9), fontSize = 9.sp, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
                }
            }
        }
    }

    @Composable
    private fun ThinkingBubble() {
        Row(Modifier.fillMaxWidth()) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF141A29)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Orb sedang berpikir", color = Color(0xFFADB6C8), fontSize = 12.sp)
                    Spacer(Modifier.width(7.dp))
                    Text("•••", color = Color(0xFFB68DFF), fontSize = 15.sp)
                }
            }
        }
    }

    @Composable
    private fun Composer(input: String, onInput: (String) -> Unit, onSend: () -> Unit, voiceState: VoiceEngine.State, onMic: () -> Unit, onSpeak: () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInput,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tanya AI apa saja...", color = Color(0xFF6E798E)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(19.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF9A6BFF), unfocusedBorderColor = Color(0xFF2B3244), focusedContainerColor = Color(0xFF0E1420), unfocusedContainerColor = Color(0xFF0E1420), cursorColor = Color(0xFF9A6BFF))
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SmallAction(Icons.Default.GraphicEq, if (voiceState == VoiceEngine.State.SPEAKING) "Stop" else "Baca", onSpeak)
                Spacer(Modifier.height(4.dp))
                SmallAction(if (voiceState == VoiceEngine.State.LISTENING) Icons.Default.MicOff else Icons.Default.Mic, if (voiceState == VoiceEngine.State.LISTENING) "Stop" else "Mic", onMic)
            }
            FilledIconButton(onClick = onSend, enabled = input.isNotBlank(), modifier = Modifier.size(54.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF7948DD), disabledContainerColor = Color(0xFF232838))) {
                Icon(Icons.Default.Send, "Kirim")
            }
        }
    }

    @Composable
    private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
            Surface(shape = CircleShape, color = Color(0xFF171D2A), modifier = Modifier.size(40.dp)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color(0xFFDAD0FF), modifier = Modifier.size(19.dp)) } }
            Text(label, color = Color(0xFF8893A8), fontSize = 8.sp)
        }
    }

    @Composable
    private fun BottomHotbar(active: String, onHome: () -> Unit, onOrb: () -> Unit, onScreen: () -> Unit, onSettings: () -> Unit) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF101624), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x332E3B58)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                HotbarItem("Home", "⌂", active == "Home", onHome, Modifier.weight(1f))
                HotbarItem("Orb", "✦", active == "Orb", onOrb, Modifier.weight(1f))
                HotbarItem("Screen", "◉", active == "Screen", onScreen, Modifier.weight(1f))
                HotbarItem("Setting", "⚙", active == "Settings", onSettings, Modifier.weight(1f))
            }
        }
    }

    @Composable
    private fun HotbarItem(label: String, icon: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
        Column(modifier.clickable { onClick() }.clip(RoundedCornerShape(17.dp)).background(if (selected) Color(0xFF6D42CF) else Color.Transparent).padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, color = if (selected) Color.White else Color(0xFF8993A8), fontSize = 18.sp)
            Text(label, color = if (selected) Color.White else Color(0xFF8993A8), fontSize = 9.sp, maxLines = 1)
        }
    }

    @Composable
    private fun SetupCard(apiKey: String, model: String, endpoint: String, voicePreset: VoiceEngine.Preset, onKey: (String) -> Unit, onModel: (String) -> Unit, onEndpoint: (String) -> Unit, onPreset: (VoiceEngine.Preset) -> Unit, onSave: () -> Unit, onClose: () -> Unit) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF101624), tonalElevation = 8.dp, shadowElevation = 18.dp, modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x554F3C86), RoundedCornerShape(24.dp))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SETUP & VOICE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color(0xFFC8CFDC)) }
                }
                OutlinedTextField(apiKey, onKey, Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true)
                OutlinedTextField(model, onModel, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
                OutlinedTextField(endpoint, onEndpoint, Modifier.fillMaxWidth(), label = { Text("Endpoint") }, singleLine = true)
                Text("Anime voice", color = Color(0xFFB8C1D2), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VoiceEngine.Preset.values().forEach { preset ->
                        FilterChip(selected = voicePreset == preset, onClick = { onPreset(preset) }, label = { Text(preset.label, fontSize = 10.sp) }, modifier = Modifier.weight(1f))
                    }
                }
                Text("Preset memengaruhi pitch dan kecepatan TTS Android. Karakter suara yang tersedia tetap mengikuti mesin TTS di HP.", color = Color(0xFF7D899E), fontSize = 10.sp)
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) { Text("Simpan & Terapkan") }
            }
        }
    }

    private fun now(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

object AppMemory {
    var cameraBitmap by mutableStateOf<Bitmap?>(null)
    var voiceState by mutableStateOf(VoiceEngine.State.IDLE)
    var voiceText by mutableStateOf("")
    fun setCamera(bitmap: Bitmap?) { cameraBitmap?.let { if (it !== bitmap) runCatching { it.recycle() } }; cameraBitmap = bitmap }
}

@Composable
private fun OrbTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8B5CF6), secondary = Color(0xFF22D3EE), background = Color(0xFF070A12), surface = Color(0xFF101624)), content = content)
}
