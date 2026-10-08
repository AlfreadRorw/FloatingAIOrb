package com.alfread.alfvision.ui.screens

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.data.local.RegionPresetEntity
import com.alfread.alfvision.service.ScreenCaptureService
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.components.*
import com.alfread.alfvision.ui.navigation.Dest
import com.alfread.alfvision.ui.theme.AlfCyan
import com.alfread.alfvision.ui.theme.color
import com.alfread.alfvision.ui.theme.heroBrush
import com.alfread.alfvision.vision.VoiceState
import java.text.DateFormat
import java.util.Date

// =============================================================================================
// HOME
// =============================================================================================

@Composable
fun HomeScreen(
    vm: MainViewModel,
    padding: PaddingValues,
    onStartVision: () -> Unit,
    onStopVision: () -> Unit,
    onOverlay: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val network by vm.network.collectAsState()
    val apiReady by vm.apiReady.collectAsState()
    val running by ScreenCaptureService.running.collectAsState()
    val error by vm.session.error.collectAsState()
    val accent = settings.accent.color()
    var overlayReady by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayReady = Settings.canDrawOverlays(context)
        vm.refreshApiKey()
    }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // HERO
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(heroBrush(accent)).padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Visibility, null, tint = Color.White) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("ALF VISION", color = Color.White, style = MaterialTheme.typography.headlineSmall, letterSpacing = 1.sp)
                        Text("AI screen assistant", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(if (running) Color(0xFF3DFF8F) else Color.White.copy(alpha = 0.55f)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (running) "Vision aktif - panel siap dipakai" else "Vision belum aktif",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = if (running) onStopVision else onStartVision,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF14172B))
                ) {
                    Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "STOP VISION" else "START VISION", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        error?.let { InfoBanner(it, onDismiss = { vm.session.setError(null) }) }

        SectionLabel("Status")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusTile(
                Icons.Default.VpnKey, "API KEY", if (apiReady) "Tersimpan" else "Belum diatur", apiReady,
                Modifier.weight(1f).height(124.dp)
            ) { onNavigate(Dest.Settings.route) }
            StatusTile(
                Icons.Default.Layers, "OVERLAY", if (overlayReady) "Aktif" else "Perlu izin", overlayReady,
                Modifier.weight(1f).height(124.dp)
            ) { if (!overlayReady) onOverlay() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusTile(
                Icons.Default.CameraAlt, "CAPTURE", if (running) "Berjalan" else "Berhenti", running,
                Modifier.weight(1f).height(124.dp)
            ) { if (!running) onStartVision() }
            StatusTile(
                if (network) Icons.Default.Wifi else Icons.Default.WifiOff, "NETWORK", if (network) "Online" else "Offline", network,
                Modifier.weight(1f).height(124.dp)
            )
        }

        SectionLabel("Quick actions")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Icons.AutoMirrored.Filled.Chat, "Chat", Modifier.weight(1f)) { onNavigate(Dest.Chat.route) }
            ActionTile(Icons.Default.CameraAlt, "Capture", Modifier.weight(1f)) { vm.capture() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Icons.Default.Crop, "Region", Modifier.weight(1f)) {
                if (overlayReady) vm.showRegionSelector() else onOverlay()
            }
            ActionTile(Icons.Default.AutoAwesome, "Jawab Soal", Modifier.weight(1f)) {
                vm.answerScreen()
                onNavigate(Dest.Chat.route)
            }
        }

        AlfCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.Security, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Screenshot hanya dikirim ke Groq saat kamu menekan capture atau analyze. API key disimpan terenkripsi di perangkat.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// =============================================================================================
// VISION
// =============================================================================================

private fun vmRegion(item: RegionPresetEntity) =
    Region(item.x, item.y, item.width, item.height, item.screenWidth, item.screenHeight, item.displayId, item.rotation)

@Composable
fun VisionScreen(vm: MainViewModel, padding: PaddingValues, onOverlay: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val regions by vm.regions.collectAsState()
    val image by vm.session.currentImage.collectAsState()
    val previous by vm.session.previousImage.collectAsState()
    val activeRegion by vm.session.region.collectAsState()
    val running by ScreenCaptureService.running.collectAsState()
    var annotating by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RegionPresetEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    val currentImage = image
    if (annotating && currentImage != null) {
        AnnotationEditor(
            image = currentImage,
            onSave = { vm.session.setImage(it); annotating = false },
            onClose = { annotating = false }
        )
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ScreenHeader("Vision", if (running) "Capture aktif" else "Jalankan START VISION di Home dulu") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(Icons.Default.Crop, "Select Region", Modifier.weight(1f)) {
                        if (Settings.canDrawOverlays(context)) vm.showRegionSelector() else onOverlay()
                    }
                    ActionTile(Icons.Default.CameraAlt, "Capture", Modifier.weight(1f)) { vm.capture() }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(Icons.Default.Compare, "Compare", Modifier.weight(1f), enabled = image != null && previous != null) { vm.compare() }
                    ActionTile(Icons.Default.Draw, "Annotate", Modifier.weight(1f), enabled = image != null) { annotating = true }
                }
            }
            item {
                AlfCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwitchRow(
                            "Kirim hanya region terpilih",
                            settings.vision.sendOnlyRegion,
                            supporting = activeRegion?.let { "Aktif: ${it.width} × ${it.height} di ${it.x}, ${it.y}" } ?: "Belum ada region - memakai layar penuh"
                        ) { v -> vm.updateSettings { it.copy(vision = it.vision.copy(sendOnlyRegion = v)) } }
                        if (activeRegion != null) {
                            OutlinedButton(onClick = { vm.clearRegion() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.CropFree, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Hapus region aktif")
                            }
                        }
                    }
                }
            }
            currentImage?.let { current ->
                item {
                    AlfCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Gambar saat ini - ${current.label} - ${current.bytes.size / 1024} KB", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            val preview = remember(current.bytes) {
                                BitmapFactory.decodeByteArray(current.bytes, 0, current.bytes.size)?.asImageBitmap()
                            }
                            preview?.let {
                                androidx.compose.foundation.Image(
                                    it, contentDescription = "Screenshot terakhir",
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Region presets") }
            if (regions.isEmpty()) {
                item { EmptyState(Icons.Default.CropFree, "Belum ada preset", "Pilih region lalu tekan ikon simpan di toolbar region.") }
            }
            items(regions, key = { it.id }) { item ->
                AlfCard(onClick = { vm.session.setRegion(vmRegion(item)) }) {
                    Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.CropFree, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${item.width} × ${item.height} di ${item.x}, ${item.y}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { renameTarget = item; renameText = item.name }) { Icon(Icons.Default.Edit, "Rename") }
                        IconButton(onClick = { vm.duplicateRegion(item) }) { Icon(Icons.Default.ContentCopy, "Duplicate") }
                        IconButton(onClick = { vm.deleteRegion(item) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        }
    }
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename region") },
            text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateRegion(target.copy(name = renameText.trim().ifBlank { target.name }))
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
}

// =============================================================================================
// CHAT
// =============================================================================================

@Composable
fun ChatScreen(vm: MainViewModel, padding: PaddingValues, onRequestMicrophone: () -> Unit) {
    val lines by vm.session.lines.collectAsState()
    val input by vm.session.input.collectAsState()
    val busy by vm.session.busy.collectAsState()
    val error by vm.session.error.collectAsState()
    val voice by vm.session.voiceState.collectAsState()
    val image by vm.session.currentImage.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp)) {
        ScreenHeader("Chat", if (image != null) "Gambar siap dianalisis" else "Belum ada gambar") {
            IconButton(onClick = { vm.newChat() }) { Icon(Icons.Default.DeleteSweep, "Chat baru") }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { vm.answerScreen() },
                label = { Text("Jawab Soal", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary),
                border = null
            )
            listOf("Analyze", "Explain", "Read", "Translate", "Summarize", "Find Error", "Extract Text", "Describe", "Help Me").forEach { action ->
                AssistChip(onClick = { vm.quickAction(action) }, label = { Text(action) })
            }
            AssistChip(onClick = { vm.compare() }, label = { Text("Compare") })
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (lines.isEmpty()) {
                item { EmptyState(Icons.Default.AutoAwesome, "Mulai percakapan", "Capture layar lalu pilih aksi cepat, atau ketik pertanyaanmu.") }
            }
            items(lines, key = { it.id }) { line -> MessageBubble(line) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        image?.let { attached ->
            val thumb = remember(attached.bytes) {
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeByteArray(attached.bytes, 0, attached.bytes.size, options)?.asImageBitmap()
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                thumb?.let {
                    androidx.compose.foundation.Image(
                        it, "Gambar terlampir",
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("Gambar layar terlampir (${attached.label})", Modifier.weight(1f), fontSize = 13.sp)
                IconButton(onClick = { vm.clearImage() }) { Icon(Icons.Default.Close, "Lepas gambar") }
            }
        }
        error?.let {
            InfoBanner(it, Modifier.padding(vertical = 4.dp), actionLabel = "Retry", onAction = { vm.retryLast() }, onDismiss = { vm.session.setError(null) })
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRequestMicrophone) {
                    Icon(if (voice == VoiceState.LISTENING) Icons.Default.Stop else Icons.Default.Mic, "Voice input")
                }
                TextField(
                    value = input,
                    onValueChange = vm.session::setInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask anything...") },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                IconButton(
                    onClick = { if (busy) vm.stopRequest() else vm.ask(input) },
                    enabled = busy || input.isNotBlank()
                ) {
                    Icon(if (busy) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send, if (busy) "Stop" else "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(line: ChatLine) {
    val isUser = line.role == Role.USER
    val clipboard = LocalClipboardManager.current
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = bubbleColor,
            contentColor = textColor,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (isUser) 20.dp else 6.dp, bottomEnd = if (isUser) 6.dp else 20.dp),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                SelectionContainer { MessageContent(line, textColor, MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), textSize = 15) }
                if (!isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val meta = listOfNotNull(line.model, line.tokenUsage?.takeIf { it > 0 }?.let { "$it tok" }).joinToString(" - ")
                        if (meta.isNotEmpty()) Text(meta, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        else Spacer(Modifier.weight(1f))
                        IconButton(onClick = { clipboard.setText(AnnotatedString(line.content)) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================================
// HISTORY
// =============================================================================================

@Composable
fun HistoryScreen(vm: MainViewModel, padding: PaddingValues, onOpened: () -> Unit) {
    val conversations by vm.conversations.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader("History", "${conversations.size} percakapan") {
                if (conversations.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteSweep, "Hapus semua") }
            }
        }
        if (conversations.isEmpty()) {
            item { EmptyState(Icons.Default.History, "Belum ada riwayat", "Percakapan akan tersimpan di sini jika Save Conversations aktif.") }
        }
        items(conversations, key = { it.id }) { item ->
            AlfCard(onClick = { vm.openConversation(item.id); onOpened() }) {
                Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.Forum, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(format.format(Date(item.updatedAt)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.deleteConversation(item.id) }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus semua riwayat?") },
            text = { Text("Semua percakapan tersimpan akan dihapus permanen.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } }
        )
    }
}
