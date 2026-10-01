package com.alfread.statusdownloader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alfread.statusdownloader.R
import com.alfread.statusdownloader.data.Actions
import com.alfread.statusdownloader.data.ChatMessage
import com.alfread.statusdownloader.data.MessageParser
import com.alfread.statusdownloader.data.MsgType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private val TYPE_LABELS = listOf("Semua", "Teks", "Foto", "Video", "VN/Audio", "Dokumen")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedScreen(
    messages: List<ChatMessage>,
    notifAccess: Boolean,
    chatLogEnabled: Boolean,
    onGrantNotif: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onDelete: (ChatMessage, Boolean) -> Unit,
    onClearAll: () -> Unit,
    onSaveMedia: (ChatMessage) -> Unit
) {
    var scope by rememberSaveable { mutableStateOf(0) }      // 0 = terhapus, 1 = semua pesan
    var typeIdx by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ChatMessage?>(null) }
    val scheme = MaterialTheme.colorScheme

    DisposableEffect(Unit) { onDispose { VoicePlayer.stop() } }

    val deletedCount = remember(messages) { messages.count { it.deleted } }
    val filtered = remember(messages, scope, typeIdx, query) {
        val q = query.trim().lowercase()
        messages.asReversed().filter { m ->
            (scope == 1 || m.deleted) &&
                when (typeIdx) {
                    1 -> m.type == MsgType.TEXT
                    2 -> m.type == MsgType.IMAGE
                    3 -> m.type == MsgType.VIDEO
                    4 -> m.type == MsgType.VOICE || m.type == MsgType.AUDIO
                    5 -> m.type == MsgType.DOCUMENT
                    else -> true
                } &&
                (q.isEmpty() || m.chat.lowercase().contains(q) || m.sender.lowercase().contains(q) ||
                    m.text.lowercase().contains(q))
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Terhapus",
            subtitle = "$deletedCount pesan dihapus • ${messages.size} tercatat",
            actions = {
                if (messages.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus semua catatan")
                    }
                }
            }
        )

        when {
            !notifAccess -> AccessPrompt(onGrantNotif, onOpenAppInfo)
            !chatLogEnabled -> EmptyState(
                title = "Pencatatan chat dimatikan",
                desc = "Aktifkan \"Catat pesan masuk\" di tab Setting agar pesan yang dihapus bisa dipulihkan."
            )
            else -> {
                PillGroup(
                    options = listOf("Terhapus $deletedCount", "Semua pesan"),
                    selectedIndex = scope,
                    onSelect = { scope = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cari chat atau isi pesan") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = scheme.surfaceVariant,
                        unfocusedContainerColor = scheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(TYPE_LABELS.size) { i ->
                        FilterChip(
                            selected = typeIdx == i,
                            onClick = { typeIdx = i },
                            label = { Text(TYPE_LABELS[i]) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = scheme.onBackground,
                                selectedLabelColor = scheme.background
                            )
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    EmptyState(
                        title = if (scope == 0) "Belum ada pesan terhapus" else "Belum ada pesan tercatat",
                        desc = "Alfread hanya bisa memulihkan pesan yang masuk saat pencatatan aktif. " +
                            "Pastikan unduh otomatis media di WhatsApp menyala agar foto, video, VN, dan dokumen ikut tersimpan."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { m ->
                            MessageCard(m, onSave = { onSaveMedia(m) }, onDelete = { pendingDelete = m })
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus catatan ini?") },
            text = { Text(if (m.mediaPath != null) "File media cadangan juga bisa ikut dihapus." else "Catatan pesan akan dihapus dari Alfread.") },
            confirmButton = {
                TextButton(onClick = { onDelete(m, true); pendingDelete = null }) {
                    Text(if (m.mediaPath != null) "Hapus + file" else "Hapus")
                }
            },
            dismissButton = {
                Row {
                    if (m.mediaPath != null) {
                        TextButton(onClick = { onDelete(m, false); pendingDelete = null }) { Text("Catatan saja") }
                    }
                    TextButton(onClick = { pendingDelete = null }) { Text("Batal") }
                }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus semua catatan chat?") },
            text = { Text("Semua pesan tercatat dan media cadangan di Alfread akan dihapus. Chat asli di WhatsApp tidak terpengaruh.") },
            confirmButton = { TextButton(onClick = { onClearAll(); confirmClear = false }) { Text("Hapus semua") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun AccessPrompt(onGrantNotif: () -> Unit, onOpenAppInfo: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painterResource(R.drawable.logo_app), null,
            Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))
        )
        Spacer(Modifier.height(20.dp))
        Text("Aktifkan akses notifikasi", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Alfread menyimpan salinan pesan WhatsApp yang masuk. Saat pengirim menghapus pesan, " +
                "salinannya tetap bisa kamu baca di sini. Semua data hanya tersimpan di HP ini.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrantNotif) { Text("Aktifkan Akses Notifikasi") }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onOpenAppInfo) { Text("Tombol abu-abu? Buka Info Aplikasi") }
        Text(
            "Android 13+: di Info Aplikasi tekan ⋮ lalu pilih \"Izinkan setelan terbatas\", kemudian ulangi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MessageCard(m: ChatMessage, onSave: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val file = remember(m.mediaPath) { m.mediaPath?.let { File(it) }?.takeIf { it.exists() } }
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(m.chat, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (m.sender != m.chat) {
                    Text(m.sender, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Text(
                    fmt.format(m.time) + if (m.app == "com.whatsapp.w4b") " • WA Business" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
            if (m.deleted) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(scheme.onBackground)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("DIHAPUS", color = scheme.background, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        when {
            m.type == MsgType.IMAGE && file != null || m.type == MsgType.VIDEO && file != null -> {
                Box(
                    Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp))
                        .background(scheme.outlineVariant)
                        .clickable { Actions.openAny(ctx, file!!) }
                ) {
                    AsyncImage(
                        model = file, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                    if (m.type == MsgType.VIDEO) {
                        Box(
                            Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape)
                                .background(Color(0x99000000)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White) }
                    }
                }
            }
            (m.type == MsgType.VOICE || m.type == MsgType.AUDIO) && file != null -> {
                val playing = VoicePlayer.playing == file.absolutePath
                Row(
                    Modifier.clip(RoundedCornerShape(28.dp)).background(scheme.background).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(scheme.onBackground)
                            .clickable { VoicePlayer.toggle(file.absolutePath) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (playing) Box(Modifier.size(14.dp).background(scheme.background))
                        else Icon(Icons.Default.PlayArrow, null, tint = scheme.background)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.padding(end = 12.dp)) {
                        Text(if (m.type == MsgType.VOICE) "Pesan suara" else "Audio", style = MaterialTheme.typography.titleMedium)
                        Text(formatSize(file.length()), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
            m.type == MsgType.DOCUMENT && file != null -> {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            MessageParser.fileName(m.text).ifBlank { file.name },
                            style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Text(formatSize(file.length()), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { Actions.openAny(ctx, file) }) { Text("Buka") }
                }
            }
            m.type.isMedia -> {
                Text(m.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Media tidak sempat tersimpan (belum terunduh saat pesan masuk).",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
                )
            }
            else -> Text(m.text, style = MaterialTheme.typography.bodyLarge)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = {
                if (file != null) Actions.shareAny(ctx, file) else Actions.shareText(ctx, m.text)
            }) { Icon(Icons.Default.Share, contentDescription = "Bagikan") }
            if (file != null) {
                IconButton(onClick = onSave) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = "Simpan ke Download")
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Hapus catatan") }
        }
    }
}
