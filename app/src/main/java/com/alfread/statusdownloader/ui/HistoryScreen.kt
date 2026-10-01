package com.alfread.statusdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alfread.statusdownloader.data.HistoryRecord
import com.alfread.statusdownloader.data.MediaKind
import java.io.File

@Composable
fun HistoryScreen(
    history: List<HistoryRecord>,
    onOpen: (HistoryRecord) -> Unit,
    onShare: (HistoryRecord) -> Unit,
    onDelete: (HistoryRecord, Boolean) -> Unit,
    onClear: (Boolean) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<HistoryRecord?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Riwayat",
            subtitle = "${history.size} file diunduh",
            actions = {
                if (history.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text("Hapus semua") }
                }
            }
        )

        if (history.isEmpty()) {
            EmptyState(
                title = "Riwayat masih kosong",
                desc = "Status yang kamu unduh akan muncul di sini, lengkap dengan ukuran dan waktunya."
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Foto", history.count { it.kind == MediaKind.IMAGE }.toString(), Modifier.weight(1f))
                StatCard("Video", history.count { it.kind == MediaKind.VIDEO }.toString(), Modifier.weight(1f))
                StatCard("Total ukuran", formatSize(history.sumOf { it.size }), Modifier.weight(1.4f))
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { rec ->
                    HistoryRow(
                        rec = rec,
                        onOpen = { onOpen(rec) },
                        onShare = { onShare(rec) },
                        onDelete = { pendingDelete = rec }
                    )
                }
            }
        }
    }

    pendingDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus item ini?") },
            text = { Text("Pilih apakah file di folder Download ikut dihapus.") },
            confirmButton = {
                TextButton(onClick = { onDelete(rec, true); pendingDelete = null }) { Text("Hapus file") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onDelete(rec, false); pendingDelete = null }) { Text("Riwayat saja") }
                    TextButton(onClick = { pendingDelete = null }) { Text("Batal") }
                }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus semua riwayat?") },
            text = { Text("Pilih apakah semua file hasil unduhan ikut dihapus dari penyimpanan.") },
            confirmButton = {
                TextButton(onClick = { onClear(true); confirmClear = false }) { Text("Hapus + file") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onClear(false); confirmClear = false }) { Text("Riwayat saja") }
                    TextButton(onClick = { confirmClear = false }) { Text("Batal") }
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    rec: HistoryRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val file = remember(rec.savedPath) { File(rec.savedPath) }
    val exists = remember(rec.savedPath) { file.exists() }
    val scheme = MaterialTheme.colorScheme

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(scheme.outlineVariant),
            contentAlignment = Alignment.Center
        ) {
            if (exists) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (rec.kind == MediaKind.VIDEO) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${if (rec.kind == MediaKind.VIDEO) "Video" else "Foto"} • ${formatSize(rec.size)} • ${relativeTime(rec.time)}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (exists) rec.origin else "File tidak ditemukan",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Bagikan") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Hapus") }
    }
}
