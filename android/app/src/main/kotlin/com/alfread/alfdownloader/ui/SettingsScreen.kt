package com.alfread.alfdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

@Composable
fun SettingsScreen(c: AppController, bottomPad: Dp) {
    val accent = LocalAccent.current
    val p = c.prefs

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Pengaturan", color = Ink.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("Atur server, dock, dan otomatisasi", color = Ink.Muted, fontSize = 13.sp)
            }
        }

        // ---------------- server
        item { SectionTitle("Server", Modifier.padding(top = 8.dp)) }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Dns, tint = if (c.online) Ink.Success else Ink.Danger)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (c.online) "Server berjalan" else if (c.starting) "Sedang menyalakan…" else "Server mati",
                            color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                        Text(
                            if (c.online) "v${c.health?.version ?: "?"}  •  yt-dlp ${c.health?.ytdlp ?: "?"}" else p.serverUrl,
                            color = Ink.Muted, fontSize = 12.sp
                        )
                    }
                }
                if (!c.online && c.lastError != null) {
                    Text(
                        c.lastError ?: "", color = Ink.Danger, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Nyalakan", Icons.Rounded.PowerSettingsNew, Modifier.weight(1f), enabled = !c.online, tint = accent) { c.startServer() }
                    GhostButton("Cek", Icons.Rounded.Sync, Modifier.weight(1f)) { c.scopeCheck() }
                    GhostButton("Matikan", Icons.Rounded.Close, Modifier.weight(1f), enabled = c.online, tint = Ink.Danger) { c.stopServer() }
                }
                RowDivider()
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Buka Termux", Icons.Rounded.Terminal, Modifier.weight(1f)) {
                        if (!c.termux.openTermux()) c.toast("Termux belum terpasang")
                    }
                    GhostButton("Folder unduhan", Icons.Rounded.FolderOpen, Modifier.weight(1f)) { c.openFolder() }
                }
            }
        }

        // ---------------- automation
        item { SectionTitle("Otomatisasi", Modifier.padding(top = 8.dp)) }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SwitchRow(Icons.Rounded.PowerSettingsNew, "Nyalakan server otomatis", "Jalankan server Termux saat aplikasi dibuka", p.autoStart) { v -> c.update { it.copy(autoStart = v) } }
                RowDivider()
                SwitchRow(Icons.Rounded.ContentPaste, "Tempel link otomatis", "Isi kolom link dari clipboard saat aplikasi dibuka", p.autoPaste) { v -> c.update { it.copy(autoPaste = v) } }
                RowDivider()
                SwitchRow(Icons.Rounded.AutoAwesome, "Ambil info otomatis", "Tampilkan judul & sampul begitu link masuk", p.autoInfo) { v -> c.update { it.copy(autoInfo = v) } }
                RowDivider()
                SwitchRow(Icons.Rounded.Share, "Unduh otomatis dari Bagikan", "Link yang dibagikan ke ALF langsung diunduh", p.autoDownloadShared) { v -> c.update { it.copy(autoDownloadShared = v) } }
                RowDivider()
                SwitchRow(Icons.Rounded.Bolt, "Unduh otomatis dari clipboard", "Link yang disalin langsung masuk antrean", p.autoDownloadClipboard) { v -> c.update { it.copy(autoDownloadClipboard = v) } }
                RowDivider()
                SwitchRow(Icons.Rounded.ClearAll, "Kosongkan kolom setelah masuk antrean", "Siap untuk link berikutnya", p.clearAfterAdd) { v -> c.update { it.copy(clearAfterAdd = v) } }
                RowDivider()
                SwitchRow(Icons.Rounded.Notifications, "Notifikasi selesai", "Beri tahu saat unduhan selesai atau gagal", p.notifications) { v -> c.update { it.copy(notifications = v) } }
            }
        }

        // ---------------- dock
        item { SectionTitle("Dock bar", Modifier.padding(top = 8.dp)) }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().background(Ink.Bg).padding(vertical = 18.dp)) {
                    Dock(Tab.DOWNLOAD, { c.tab = it }, p, 2, preview = true)
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OptionGroup("Gaya", listOf("Melayang", "Menempel"), p.dockStyle) { v -> c.update { it.copy(dockStyle = v) } }
                    OptionGroup("Label", listOf("Terpilih", "Selalu", "Sembunyi"), p.dockLabels) { v -> c.update { it.copy(dockLabels = v) } }
                    OptionGroup("Ukuran ikon", listOf("Kecil", "Sedang", "Besar"), p.dockSize) { v -> c.update { it.copy(dockSize = v) } }
                    Column {
                        Text("Kepekatan ${(p.dockOpacity * 100).toInt()}%", color = Ink.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = p.dockOpacity, onValueChange = { v -> c.update { it.copy(dockOpacity = v) } },
                            valueRange = 0.5f..1f,
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Ink.Surface3)
                        )
                    }
                }
                RowDivider()
                SwitchRow(Icons.Rounded.Download, "Lencana unduhan aktif", "Tampilkan jumlah unduhan berjalan di ikon Unduh", p.dockBadge) { v -> c.update { it.copy(dockBadge = v) } }
                RowDivider()
                SwitchRow(Icons.Rounded.Vibration, "Getar halus", "Umpan balik haptik saat berpindah tab", p.haptics) { v -> c.update { it.copy(haptics = v) } }
            }
        }

        // ---------------- appearance
        item { SectionTitle("Tampilan", Modifier.padding(top = 8.dp)) }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Palette)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Warna aksen", color = Ink.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(AccentOptions[p.accent.coerceIn(0, AccentOptions.lastIndex)].name, color = Ink.Muted, fontSize = 12.sp)
                    }
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccentOptions.forEachIndexed { i, opt ->
                        val sel = i == p.accent
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(opt.color)
                                .border(if (sel) 3.dp else 1.dp, if (sel) Ink.Text else Ink.Line, CircleShape)
                                .clickable { c.update { it.copy(accent = i) } },
                            contentAlignment = Alignment.Center
                        ) { if (sel) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }

        // ---------------- defaults
        item { SectionTitle("Bawaan unduhan", Modifier.padding(top = 8.dp)) }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val qs = listOf("best", "2160", "1080", "720", "480", "360", "audio")
                    OptionGroup("Kualitas awal", qs.map { qualityLabel(it) }, qs.indexOf(p.defaultQuality).coerceAtLeast(0)) { i ->
                        c.update { it.copy(defaultQuality = qs[i]) }
                        c.quality = qs[i]
                    }
                    val fs = listOf("mp3", "m4a", "opus")
                    OptionGroup("Format audio", fs.map { it.uppercase() }, fs.indexOf(p.audioFormat).coerceAtLeast(0)) { i ->
                        c.update { it.copy(audioFormat = fs[i]) }
                        c.audioFormat = fs[i]
                    }
                    OptionGroup("Unduhan bersamaan", listOf("1", "2", "3", "4"), (p.maxConcurrent - 1).coerceIn(0, 3)) { i -> c.update { it.copy(maxConcurrent = i + 1) } }
                    val limits = listOf(0, 512, 1024, 2048, 5120)
                    OptionGroup("Batas kecepatan", listOf("Bebas", "0,5 MB/s", "1 MB/s", "2 MB/s", "5 MB/s"), limits.indexOf(p.speedLimitKb).coerceAtLeast(0)) { i ->
                        c.update { it.copy(speedLimitKb = limits[i]) }
                    }
                }
                RowDivider()
                SwitchRow(Icons.Rounded.Image, "Sematkan sampul", "Pasang thumbnail ke dalam file", p.embedThumbnail) { v -> c.update { it.copy(embedThumbnail = v) }; c.embedThumb = v }
                RowDivider()
                SwitchRow(Icons.Rounded.Info, "Sematkan metadata", "Judul, artis, dan tanggal ditulis ke file", p.embedMetadata) { v -> c.update { it.copy(embedMetadata = v) } }
                RowDivider()
                SwitchRow(Icons.Rounded.Subtitles, "Unduh subtitle", "Simpan subtitle (id/en) bila tersedia", p.subtitles) { v -> c.update { it.copy(subtitles = v) }; c.subtitles = v }
                RowDivider()
                SwitchRow(Icons.Rounded.PlaylistPlay, "Unduh seluruh playlist", "Jika link berisi playlist, ambil semuanya", p.playlist) { v -> c.update { it.copy(playlist = v) }; c.playlist = v }
            }
        }

        // ---------------- advanced
        item { SectionTitle("Lanjutan", Modifier.padding(top = 8.dp)) }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextRow("Alamat server", p.serverUrl) { v -> c.update { it.copy(serverUrl = v.trim()) } }
                    TextRow("Folder server di Termux", p.serverDir) { v -> c.update { it.copy(serverDir = v.trim()) } }
                    Text(
                        "Aplikasi juga otomatis mencari ~/Github/termux-server dan ~/termux-server. Log server: ~/.alf-server.log",
                        color = Ink.Muted, fontSize = 12.sp, lineHeight = 16.sp
                    )
                }
            }
        }

        item { SectionTitle("Panduan awal", Modifier.padding(top = 8.dp)) }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Pasang Termux, lalu jalankan termux-setup-storage.",
                        "Di folder termux-server jalankan: bash install.sh",
                        "Izinkan \"Run commands in Termux\" untuk ALF (muncul saat pertama dibuka).",
                        "Buka ALF — server menyala otomatis. Bagikan link dari aplikasi lain untuk unduh instan."
                    ).forEachIndexed { i, t ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${i + 1}", color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(22.dp))
                            Text(t, color = Ink.Text, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                AlfMark(22.dp)
                Spacer(Modifier.width(8.dp))
                Text("ALF Downloader 1.1.0", color = Ink.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun OptionGroup(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Ink.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, label -> Chip(label, i == selected) { onSelect(i) } }
        }
    }
}

@Composable
private fun TextRow(label: String, value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val accent = LocalAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Ink.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        TextField(
            value = text, onValueChange = { text = it; onCommit(it) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Ink.Surface2, unfocusedContainerColor = Ink.Surface2,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = accent
            )
        )
    }
}
