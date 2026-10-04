package com.alfread.alfdownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfdownloader.AppController
import com.alfread.alfdownloader.shizuku.ShizukuHelper

/**
 * Kartu bantuan di layar Unduh: menampilkan progres saat server dinyalakan, dan langkah perbaikan
 * satu-ketuk bila server tidak mau menyala (izin, allow-external-apps, pasang server).
 */
@Composable
fun ServerHelpCard(c: AppController) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    val installed = c.termux.isInstalled()
    val hasPerm = c.termux.hasPermission()

    // 1) Sedang menyalakan → tampilkan tahap, bukan layar kosong
    if (!c.online && c.starting) {
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(c.serverStage.ifBlank { "Menyalakan server…" }, color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent, trackColor = Ink.Surface3)
                Text("Pertama kali setelah HP restart biasanya 10–30 detik karena yt-dlp dimuat.", color = Ink.Muted, fontSize = 12.sp)
            }
        }
        return
    }

    val needHelp = !c.online && (c.serverFailed || (installed && !hasPerm) || !installed)
    if (!needHelp) return

    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Build, tint = Ink.Danger)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Server belum jalan", color = Ink.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        when {
                            !installed -> "Termux belum terpasang."
                            !hasPerm -> "ALF belum boleh menjalankan perintah di Termux."
                            else -> "Ikuti langkah di bawah (cukup sekali)."
                        }, color = Ink.Muted, fontSize = 12.sp
                    )
                }
            }
            if (installed && !hasPerm) {
                GhostButton(
                    "Beri izin lewat Shizuku", Icons.Rounded.VerifiedUser, Modifier.fillMaxWidth(),
                    enabled = ShizukuHelper.granted.value
                ) {
                    val ok = ShizukuHelper.grantTermuxRunCommand(context.packageName)
                    c.toast(if (ok) "Izin diberikan. Coba nyalakan server lagi." else "Gagal. Beri manual di Pengaturan Android → Aplikasi → ALF → Izin.")
                }
            }
            if (installed) {
                GhostButton("1. Izinkan ALF di Termux (salin & buka)", Icons.Rounded.Terminal, Modifier.fillMaxWidth()) {
                    c.termux.copyAllowExternalAndOpen()
                    c.toast("Perintah disalin. Tempel di Termux lalu tekan Enter.")
                }
                GhostButton("2. Pasang server otomatis", Icons.Rounded.CloudDownload, Modifier.fillMaxWidth(), enabled = hasPerm) { c.installServer() }
                GhostButton("3. Coba nyalakan lagi", Icons.Rounded.PowerSettingsNew, Modifier.fillMaxWidth(), enabled = hasPerm, tint = accent) { c.startServer() }
            }
        }
    }
}
