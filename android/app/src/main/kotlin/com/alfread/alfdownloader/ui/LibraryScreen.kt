package com.alfread.alfdownloader.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfdownloader.AppController
import com.alfread.alfdownloader.model.Job

@Composable
fun LibraryScreen(c: AppController, bottomPad: Dp) {
    val accent = LocalAccent.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    val shown = c.library.filter { j ->
        (filter == 0 || (filter == 1 && j.status == "completed") || (filter == 2 && j.status == "error")) &&
            (query.isBlank() || (j.title ?: j.url).contains(query, ignoreCase = true))
    }.sortedByDescending { it.finishedAt ?: it.createdAt }
    val done = c.library.filter { it.status == "completed" }
    val total = done.sumOf { it.sizeBytes }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pustaka", color = Ink.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${done.size} file  •  ${formatBytes(total)}", color = Ink.Muted, fontSize = 13.sp)
                }
                IconAction(Icons.Rounded.FolderOpen, "Buka folder", { c.openFolder() }, tint = accent)
                IconAction(Icons.Rounded.ClearAll, "Hapus semua", { confirmClear = true }, tint = if (c.library.isEmpty()) Ink.Line else Ink.Muted)
            }
        }
        item {
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Cari judul…", color = Ink.Muted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Ink.Muted) },
                trailingIcon = { if (query.isNotEmpty()) IconAction(Icons.Rounded.Close, "Hapus", { query = "" }) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Ink.Surface, unfocusedContainerColor = Ink.Surface,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = accent
                )
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Semua", filter == 0) { filter = 0 }
                Chip("Selesai", filter == 1, icon = Icons.Rounded.CheckCircle) { filter = 1 }
                Chip("Gagal", filter == 2, icon = Icons.Rounded.Error) { filter = 2 }
            }
        }
        if (c.online && c.health != null) {
            item {
                Panel(Modifier.fillMaxWidth(), radius = 18.dp) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.Storage)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Ruang bebas ${formatBytes(c.health?.freeBytes ?: 0)}", color = Ink.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(c.health?.downloadDir ?: "", color = Ink.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconTile(Icons.Rounded.VideoLibrary, size = 56.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(if (c.library.isEmpty()) "Pustaka masih kosong" else "Tidak ada hasil", color = Ink.Text, fontWeight = FontWeight.SemiBold)
                    Text("Unduhan yang selesai akan muncul di sini.", color = Ink.Muted, fontSize = 12.sp)
                }
            }
        } else {
            items(shown, key = { it.id }) { job -> HistoryRow(c, job) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Ink.Surface2,
            title = { Text("Hapus riwayat?", color = Ink.Text) },
            text = { Text("Riwayat unduhan dihapus dari aplikasi. File di folder Download tidak ikut terhapus.", color = Ink.Muted) },
            confirmButton = { TextButton(onClick = { c.clearLibrary(); confirmClear = false }) { Text("Hapus", color = Ink.Danger) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal", color = Ink.Text) } }
        )
    }
}

@Composable
fun HistoryRow(c: AppController, job: Job) {
    val ok = job.status == "completed"
    Panel(Modifier.fillMaxWidth(), radius = 20.dp) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteThumb(job.thumbnail, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                    fallback = if (job.quality == "audio") Icons.Rounded.MusicNote else Icons.Rounded.Movie)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(job.title ?: job.url, color = Ink.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(
                        qualityLabel(job.quality, job.audioFormat),
                        if (job.sizeBytes > 0) formatBytes(job.sizeBytes) else null,
                        shortDate(job.finishedAt ?: job.createdAt).ifBlank { null }
                    ).joinToString("  •  ")
                    Text(meta, color = Ink.Muted, fontSize = 12.sp)
                }
                Icon(
                    if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null,
                    tint = if (ok) Ink.Success else Ink.Danger, modifier = Modifier.size(20.dp)
                )
            }
            if (!ok && !job.error.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(job.error, color = Ink.Danger, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconAction(Icons.Rounded.Refresh, "Unduh ulang", { c.redownload(job) })
                IconAction(Icons.Rounded.ContentCopy, "Salin link", { c.copy(job.url) })
                IconAction(Icons.Rounded.Share, "Bagikan", { c.share(job.url) })
                IconAction(Icons.Rounded.Delete, "Hapus", { c.removeFromLibrary(job.id) })
            }
        }
    }
}
