package com.alfread.alfdownloader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfdownloader.AppController
import com.alfread.alfdownloader.looksLikeUrl
import com.alfread.alfdownloader.model.Job
import com.alfread.alfdownloader.model.isActive

@Composable
fun DownloadScreen(c: AppController, bottomPad: androidx.compose.ui.unit.Dp) {
    val active = c.jobs.filter { it.isActive }
    val recent = c.jobs.filter { it.status == "completed" }.take(3)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { TopBar(c) }
        item { LinkCard(c) }
        item {
            AnimatedVisibility(c.infoLoading || c.info != null || c.infoError != null) { InfoCard(c) }
        }
        item { OptionsCard(c) }
        item {
            val label = when {
                c.online -> "Unduh sekarang"
                c.starting -> "Menunggu server…"
                else -> "Nyalakan server & unduh"
            }
            AccentButton(label, Icons.Rounded.Download, enabled = looksLikeUrl(c.url), loading = c.busy) { c.download() }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Antrean aktif", Modifier.weight(1f))
                if (active.isNotEmpty()) Text("${active.size} berjalan", color = Ink.Muted, fontSize = 12.sp)
            }
        }
        if (active.isEmpty()) {
            item { EmptyQueue() }
        } else {
            items(active, key = { it.id }) { job -> JobCard(job, onCancel = { c.cancel(job.id) }) }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("Baru selesai", Modifier.padding(top = 6.dp)) }
            items(recent, key = { "r" + it.id }) { job -> HistoryRow(c, job) }
        }
    }
}

@Composable
private fun TopBar(c: AppController) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AlfMark(46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("ALF Downloader", color = Ink.Text, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (c.online) "yt-dlp ${c.health?.ytdlp ?: ""}".trim() else "Ketuk status untuk menyalakan server",
                color = Ink.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        StatusChip(c.online, c.starting) { if (!c.online && !c.starting) c.startServer() else c.tab = Tab.SETTINGS }
    }
}

@Composable
private fun LinkCard(c: AppController) {
    val accent = LocalAccent.current
    val focus = LocalFocusManager.current
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = c.url, onValueChange = { c.url = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Tempel link video…", color = Ink.Muted) },
                leadingIcon = { Icon(Icons.Rounded.Link, null, tint = accent) },
                trailingIcon = {
                    if (c.url.isNotBlank()) IconAction(Icons.Rounded.Close, "Hapus", { c.url = "" })
                    else IconAction(Icons.Rounded.ContentPaste, "Tempel", { c.pasteNow() }, tint = accent)
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); c.download() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Ink.Surface2, unfocusedContainerColor = Ink.Surface2,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = accent
                )
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Auto-tempel", c.prefs.autoPaste, icon = Icons.Rounded.ContentPaste) {
                    c.update { it.copy(autoPaste = !it.autoPaste) }
                }
                Chip("Auto-info", c.prefs.autoInfo, icon = Icons.Rounded.AutoAwesome) {
                    c.update { it.copy(autoInfo = !it.autoInfo) }
                }
                Chip("Auto-unduh", c.prefs.autoDownloadClipboard, icon = Icons.Rounded.Bolt) {
                    c.update { it.copy(autoDownloadClipboard = !it.autoDownloadClipboard) }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(c: AppController) {
    Panel(Modifier.fillMaxWidth()) {
        val info = c.info
        when {
            c.infoLoading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Membaca tautan…", color = Ink.Text, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)), color = LocalAccent.current, trackColor = Ink.Surface3)
            }
            info != null -> Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteThumb(info.thumbnail, Modifier.size(width = 108.dp, height = 76.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(info.title.ifBlank { "Tanpa judul" }, color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(
                        info.uploader.takeIf { it.isNotBlank() },
                        info.duration?.let { formatDuration(it) },
                        info.viewCount?.let { formatCount(it) + " tayangan" },
                        if (info.isPlaylist) "${info.entryCount} video" else null
                    ).joinToString("  •  ")
                    Text(meta, color = Ink.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            else -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = Ink.Danger, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(c.infoError ?: "", color = Ink.Danger, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val qualities = listOf("best", "2160", "1080", "720", "480", "360", "audio")

@Composable
private fun OptionsCard(c: AppController) {
    val maxHeight = c.info?.heights?.maxOrNull()
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Kualitas", color = Ink.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                qualities.forEach { q ->
                    val icon = when (q) { "best" -> Icons.Rounded.AutoAwesome; "audio" -> Icons.Rounded.MusicNote; else -> null }
                    Chip(if (q == "audio") "Audio" else qualityLabel(q), c.quality == q, icon = icon) { c.quality = q }
                }
            }
            val q = c.quality.toIntOrNull()
            if (q != null && maxHeight != null && q > maxHeight) {
                Text("Sumber hanya tersedia sampai ${maxHeight}p — akan dipakai yang tertinggi.", color = Ink.Muted, fontSize = 12.sp)
            }
            AnimatedVisibility(c.quality == "audio") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Format audio", color = Ink.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("mp3", "m4a", "opus").forEach { f ->
                            Chip(f.uppercase(), c.audioFormat == f, icon = Icons.Rounded.Headphones) { c.audioFormat = f }
                        }
                    }
                }
            }
            Text("Tambahan", color = Ink.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Sampul", c.embedThumb, icon = Icons.Rounded.Image) { c.embedThumb = !c.embedThumb }
                Chip("Subtitle", c.subtitles, icon = Icons.Rounded.Subtitles, enabled = c.quality != "audio") { c.subtitles = !c.subtitles }
                Chip("Playlist", c.playlist, icon = Icons.Rounded.PlaylistPlay) { c.playlist = !c.playlist }
            }
        }
    }
}

@Composable
private fun EmptyQueue() {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(Icons.Rounded.CloudDownload, size = 52.dp)
            Spacer(Modifier.height(14.dp))
            Text("Belum ada unduhan berjalan", color = Ink.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Tempel link, atau bagikan dari TikTok / YouTube langsung ke ALF.", color = Ink.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun JobCard(job: Job, onCancel: () -> Unit) {
    val accent = LocalAccent.current
    Panel(Modifier.fillMaxWidth(), radius = 20.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteThumb(job.thumbnail, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(job.title ?: job.url, color = Ink.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val bar = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
                if (job.status == "downloading") {
                    LinearProgressIndicator(progress = { (job.progress / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = bar, color = accent, trackColor = Ink.Surface3)
                } else {
                    LinearProgressIndicator(modifier = bar, color = accent, trackColor = Ink.Surface3)
                }
                val stats = when (job.status) {
                    "downloading" -> listOfNotNull(
                        "${job.progress.toInt()}%",
                        formatSpeed(job.speedBps).ifBlank { null },
                        job.etaSeconds?.let { formatClock(it) },
                        if (job.totalBytes > 0) formatBytes(job.totalBytes) else null,
                        if (job.playlistCount > 0) "${job.playlistIndex}/${job.playlistCount}" else null
                    ).joinToString("  •  ")
                    "queued" -> "Menunggu giliran"
                    "starting" -> "Menyiapkan…"
                    "processing" -> "Memproses (ffmpeg)…"
                    "cancelling" -> "Membatalkan…"
                    else -> job.status
                }
                Text(stats, color = Ink.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconAction(Icons.Rounded.Close, "Batal", onCancel)
        }
    }
}
