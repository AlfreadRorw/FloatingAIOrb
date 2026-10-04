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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.alfread.alfdownloader.AppController
import com.alfread.alfdownloader.overlay.OverlayController

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
                RowDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Perbarui yt-dlp", Icons.Rounded.SystemUpdate, enabled = c.online) { c.updateYtdlp() }
                    Text(
                        "Jalankan ini kalau muncul \"Unsupported URL\" — biasanya berarti yt-dlp di Termux sudah usang.",
                        color = Ink.Muted, fontSize = 12.sp, lineHeight = 16.sp
                    )
                }
                RowDivider()
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Pasang server", Icons.Rounded.CloudDownload, Modifier.weight(1f), enabled = c.termux.hasPermission()) { c.installServer() }
                    GhostButton("Salin izin", Icons.Rounded.Terminal, Modifier.weight(1f)) {
                        c.termux.copyAllowExternalAndOpen(); c.toast("Tempel di Termux lalu Enter")
                    }
                }
                RowDivider()
                SwitchRow(Icons.Rounded.Shield, "Jaga server tetap hidup", "Bar mengambang memantau server & menyalakan ulang otomatis bila mati", p.keepServerAlive) { v -> c.update { it.copy(keepServerAlive = v) } }
                RowDivider()
                SwitchRow(Icons.Rounded.RestartAlt, "Jalankan setelah HP menyala", "Bar mengambang + server aktif otomatis setelah restart", p.startOnBoot) { v -> c.update { it.copy(startOnBoot = v) } }
                RowDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Lihat log server", Icons.Rounded.Article, Modifier.fillMaxWidth(), enabled = c.online) { c.loadServerLog() }
                    if (c.serverLog.isNotEmpty()) {
                        Text(
                            c.serverLog.takeLast(40).joinToString("\n"),
                            color = Ink.Muted, fontSize = 10.sp, lineHeight = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
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

        // ---------------- tema jendela mengambang
        item { SectionTitle("Tema jendela mengambang", Modifier.padding(top = 8.dp)) }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Pilih tema", color = Ink.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        com.alfread.alfdownloader.overlay.PanelThemes.forEachIndexed { i, t ->
                            val sel = p.panelTheme == i
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { c.update { it.copy(panelTheme = i) } }) {
                                Box(
                                    Modifier.size(width = 64.dp, height = 84.dp).clip(RoundedCornerShape(18.dp))
                                        .background(Ink.Surface3).background(t.bgBrush(1f))
                                        .border(if (sel) 2.5.dp else 1.dp, if (sel) t.accent else Ink.Line, RoundedCornerShape(18.dp))
                                ) {
                                    Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(width = 36.dp, height = 8.dp).clip(RoundedCornerShape(50)).background(t.surface))
                                    Box(Modifier.align(Alignment.Center).size(width = 40.dp, height = 12.dp).clip(RoundedCornerShape(50)).background(t.accent))
                                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(width = 36.dp, height = 8.dp).clip(RoundedCornerShape(50)).background(t.surface))
                                }
                                Text(t.name, color = if (sel) Ink.Text else Ink.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                    Column {
                        Text("Kepekatan panel ${(p.panelOpacity * 100).toInt()}%", color = Ink.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = p.panelOpacity, onValueChange = { v -> c.update { it.copy(panelOpacity = v) } }, valueRange = 0.4f..1f,
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Ink.Surface3)
                        )
                        Text("Kelengkungan sudut ${p.panelCornerDp} dp", color = Ink.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = p.panelCornerDp.toFloat(), onValueChange = { v -> c.update { it.copy(panelCornerDp = v.toInt()) } }, valueRange = 8f..36f,
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Ink.Surface3)
                        )
                        Text("Lebar panel ${p.panelWidthDp} dp", color = Ink.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = p.panelWidthDp.toFloat(), onValueChange = { v -> c.update { it.copy(panelWidthDp = v.toInt()) } }, valueRange = 240f..360f,
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Ink.Surface3)
                        )
                    }
                }
                RowDivider()
                SwitchRow(Icons.Rounded.Palette, "Pakai warna aksen tema", "Matikan untuk memakai warna aksen aplikasi (Pengaturan → Tampilan)", p.panelUseThemeAccent) { v -> c.update { it.copy(panelUseThemeAccent = v) } }
                RowDivider()
                SwitchRow(Icons.Rounded.BlurOn, "Blur latar belakang", "Buramkan layar di belakang panel (Android 12+)", p.panelBlur) { v -> c.update { it.copy(panelBlur = v) } }
                RowDivider()
                SwitchRow(Icons.Rounded.Bolt, "Bar berdenyut saat mengunduh", "Bar tepi layar menyala berdenyut selama ada unduhan", p.barGlow) { v -> c.update { it.copy(barGlow = v) } }
                RowDivider()
                SwitchRow(Icons.Rounded.Visibility, "Redupkan bar saat diam", "Bar jadi samar bila tidak ada unduhan supaya tidak mengganggu", p.barDimWhenIdle) { v -> c.update { it.copy(barDimWhenIdle = v) } }
            }
        }

        // ---------------- floating bubble
        item { SectionTitle("Jendela mengambang", Modifier.padding(top = 8.dp)) }
        item {
            val context = LocalContext.current
            val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (OverlayController.canDrawOverlays(context)) {
                    c.update { it.copy(floatingEnabled = true) }
                    OverlayController.start(context)
                } else {
                    c.toast("Izin \"Tampil di atas aplikasi lain\" belum diberikan")
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                SwitchRow(
                    Icons.Rounded.Bolt, "Bar mengambang",
                    "Garis tegak di tepi layar — geser naik/turun, ketuk untuk buka panel", p.floatingEnabled
                ) { v ->
                    if (v) {
                        if (OverlayController.canDrawOverlays(context)) {
                            c.update { it.copy(floatingEnabled = true) }
                            OverlayController.start(context)
                        } else {
                            overlayLauncher.launch(OverlayController.permissionIntent(context))
                        }
                    } else {
                        c.update { it.copy(floatingEnabled = false) }
                        OverlayController.stop(context)
                    }
                }
                RowDivider()
                Box(Modifier.padding(16.dp)) {
                    OptionGroup("Posisi bar", listOf("Kiri", "Kanan"), p.bubbleSide) { i -> c.update { it.copy(bubbleSide = i) } }
                }
                RowDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Panjang bar ${p.bubbleLengthDp} dp", color = Ink.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = p.bubbleLengthDp.toFloat(),
                        onValueChange = { v -> c.update { it.copy(bubbleLengthDp = v.toInt()) } },
                        valueRange = 60f..260f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Ink.Surface3)
                    )
                    Text("Ketebalan bar ${p.bubbleThicknessDp} dp", color = Ink.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = p.bubbleThicknessDp.toFloat(),
                        onValueChange = { v -> c.update { it.copy(bubbleThicknessDp = v.toInt()) } },
                        valueRange = 4f..18f,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Ink.Surface3)
                    )
                }
                RowDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Jendela aplikasi asli", color = Ink.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Tombol aplikasi di panel membuka APK aslinya sebagai Android freeform window — bukan WebView. Ukuran awal: ${p.nativeWindowWidthDp} × ${p.nativeWindowHeightDp} dp.",
                        color = Ink.Muted, fontSize = 12.sp, lineHeight = 16.sp
                    )
                }
                RowDivider()
                Text(
                    "Panel ALF dibuat NOT_FOCUSABLE + NOT_TOUCH_MODAL supaya sentuhan di luar panel tetap diteruskan ke aplikasi di belakang. Saat Shizuku aktif, aplikasi dibuka sebagai task freeform asli Android.",
                    color = Ink.Muted, fontSize = 12.sp, lineHeight = 16.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // ---------------- shizuku
        item { SectionTitle("Shizuku", Modifier.padding(top = 8.dp)) }
        item {
            val context = LocalContext.current
            val H = com.alfread.alfdownloader.shizuku.ShizukuHelper
            LaunchedEffect(Unit) { H.init() }
            val installed = H.isInstalled(context)
            val ready = H.binderAlive.value
            val shizukuGranted = H.granted.value
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Security, tint = if (shizukuGranted) Ink.Success else Ink.Muted)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                !installed -> "Shizuku tidak terpasang"
                                !ready -> "Shizuku belum dijalankan"
                                shizukuGranted -> "Terhubung & diizinkan"
                                else -> "Terdeteksi, belum diizinkan"
                            },
                            color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp
                        )
                        Text(
                            "Dipakai untuk membuka aplikasi sebagai jendela mengambang asli & membebaskan baterai. Status ini diperbarui otomatis.",
                            color = Ink.Muted, fontSize = 12.sp, lineHeight = 16.sp
                        )
                    }
                }
                RowDivider()
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Minta izin", Icons.Rounded.VerifiedUser, Modifier.weight(1f), enabled = ready && !shizukuGranted) {
                        if (H.permanentlyDenied()) {
                            c.toast("Izin pernah ditolak permanen. Buka Shizuku → Aplikasi terotorisasi → aktifkan ALF.")
                        } else {
                            H.requestPermission { granted -> c.toast(if (granted) "Shizuku diizinkan" else "Izin Shizuku ditolak") }
                        }
                    }
                    GhostButton("Buka Shizuku", Icons.Rounded.OpenInNew, Modifier.weight(1f), enabled = installed) {
                        if (!H.openShizukuApp(context)) c.toast("Tidak bisa membuka Shizuku")
                    }
                }
                RowDivider()
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Bebaskan baterai", Icons.Rounded.BatteryChargingFull, Modifier.weight(1f), enabled = shizukuGranted) {
                        val a = H.whitelistBattery(context.packageName)
                        val b = H.whitelistBattery("com.termux")
                        c.toast(if (a && b) "ALF & Termux dibebaskan dari pembatasan baterai" else "Sebagian gagal — coba cara manual juga")
                    }
                    GhostButton("Aktifkan freeform", Icons.Rounded.OpenInNew, Modifier.weight(1f), enabled = shizukuGranted) {
                        val ok = H.enableFreeformSupport()
                        c.toast(if (ok) "Freeform diaktifkan. Jika belum berubah, restart HP sekali." else "ROM menolak pengaturan freeform")
                    }
                }
                RowDivider()
                SwitchRow(
                    Icons.Rounded.AutoFixHigh, "Pakai Shizuku otomatis",
                    "Bebaskan baterai & buka aplikasi sebagai jendela mengambang lewat Shizuku", p.useShizuku
                ) { v -> c.update { it.copy(useShizuku = v) } }
                RowDivider()
                SwitchRow(
                    Icons.Rounded.UnfoldLess, "Ciutkan panel setelah buka aplikasi",
                    "Panel otomatis mengecil jadi garis supaya tidak menutupi jendela aplikasi", p.collapseOnLaunch
                ) { v -> c.update { it.copy(collapseOnLaunch = v) } }
                RowDivider()
                SwitchRow(
                    Icons.Rounded.ContentPaste, "Tempel link otomatis",
                    "Saat panel dibuka, link yang baru disalin dari TikTok dll. langsung terisi — tinggal tekan Unduh", p.autoPasteOnExpand
                ) { v -> c.update { it.copy(autoPasteOnExpand = v) } }
            }
        }

        // ---------------- diagnosa
        item { SectionTitle("Diagnosa jendela mengambang", Modifier.padding(top = 8.dp)) }
        item {
            val context = LocalContext.current
            val H = com.alfread.alfdownloader.shizuku.ShizukuHelper
            var tick by remember { mutableIntStateOf(0) }
            val flags = remember(tick) { H.freeformFlags(context) }
            val feature = remember(tick) { context.packageManager.hasSystemFeature("android.software.freeform_window_management") }
            val rows = listOf(
                "Izin \"Tampil di atas aplikasi lain\"" to OverlayController.canDrawOverlays(context),
                "Shizuku terpasang" to H.isInstalled(context),
                "Shizuku sedang berjalan" to H.binderAlive.value,
                "ALF diizinkan di Shizuku" to H.granted.value,
                "Freeform didukung sistem" to feature,
                "enable_freeform_support = 1" to flags.first,
                "force_resizable_activities = 1" to flags.second
            )
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { (label, okay) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (okay) "✓" else "✗", color = if (okay) Ink.Success else Ink.Danger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(label, color = Ink.Text, fontSize = 13.sp)
                        }
                    }
                    Text(
                        "Jika \"Freeform didukung sistem\" ✗ tetapi flag ✓: buka Opsi pengembang → aktifkan \"Enable freeform windows\" dan \"Force activities to be resizable\", lalu restart HP.",
                        color = Ink.Muted, fontSize = 11.sp, lineHeight = 15.sp
                    )
                }
                RowDivider()
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Segarkan", Icons.Rounded.Refresh, Modifier.weight(1f)) { H.refresh(); tick++ }
                    GhostButton("Opsi pengembang", Icons.Rounded.OpenInNew, Modifier.weight(1f)) {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure { c.toast("Buka Pengaturan → Tentang ponsel → ketuk Nomor versi 7×") }
                    }
                }
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
