package com.alfread.statusdownloader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.alfread.statusdownloader.R
import com.alfread.statusdownloader.data.AppSettings
import com.alfread.statusdownloader.data.ThemeMode

@Composable
fun SettingsScreen(
    settings: AppSettings,
    hasPermission: Boolean,
    targetPath: String,
    historyCount: Int,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onManagePermission: () -> Unit,
    onClearHistory: () -> Unit
) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0.0"
    }
    var prefix by remember { mutableStateOf(settings.prefix) }
    var confirmClear by remember { mutableStateOf(false) }
    val gridOptions = listOf(2, 3, 4)
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        AppHeader(title = "Setting", subtitle = "Atur Alfread sesuai kebutuhan")

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionLabel("Tampilan")
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Tema", style = MaterialTheme.typography.titleMedium)
                    Text("Hitam putih, ikuti sistem atau pilih sendiri", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    PillGroup(
                        options = listOf("Sistem", "Terang", "Gelap"),
                        selectedIndex = settings.themeMode.ordinal,
                        onSelect = { i -> onChange { it.copy(themeMode = ThemeMode.values()[i]) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HorizontalDivider(color = scheme.outlineVariant)
                Column(Modifier.padding(16.dp)) {
                    Text("Kolom grid status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    PillGroup(
                        options = gridOptions.map { "$it kolom" },
                        selectedIndex = gridOptions.indexOf(settings.gridColumns).coerceAtLeast(0),
                        onSelect = { i -> onChange { it.copy(gridColumns = gridOptions[i]) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HorizontalDivider(color = scheme.outlineVariant)
                SwitchRow("Terbaru di atas", "Urutkan status dari yang paling baru", settings.newestFirst) { v ->
                    onChange { it.copy(newestFirst = v) }
                }
            }

            SectionLabel("Sumber")
            SettingsCard {
                SwitchRow("Sertakan WhatsApp Business", "Baca juga folder .Statuses milik WA Business", settings.includeBusiness) { v ->
                    onChange { it.copy(includeBusiness = v) }
                }
            }

            SectionLabel("Unduhan")
            SettingsCard {
                SwitchRow(
                    "Mode pindahkan",
                    "Hapus file asli dari folder WhatsApp setelah diunduh. Status akan hilang dari WhatsApp.",
                    settings.moveMode
                ) { v -> onChange { it.copy(moveMode = v) } }
                HorizontalDivider(color = scheme.outlineVariant)
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { v ->
                            val clean = v.take(24)
                            prefix = clean
                            onChange { it.copy(prefix = clean) }
                        },
                        label = { Text("Awalan nama file") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HorizontalDivider(color = scheme.outlineVariant)
                Column(Modifier.padding(16.dp)) {
                    Text("Folder tujuan", style = MaterialTheme.typography.titleMedium)
                    Text(targetPath, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }

            SectionLabel("Izin & penyimpanan")
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Akses semua file", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (hasPermission) "Aktif" else "Belum aktif",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onManagePermission) { Text("Kelola") }
                }
                HorizontalDivider(color = scheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Riwayat unduhan", style = MaterialTheme.typography.titleMedium)
                        Text("$historyCount item", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { confirmClear = true }, enabled = historyCount > 0) { Text("Hapus") }
                }
            }

            SectionLabel("Tentang")
            SettingsCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painterResource(R.drawable.logo_app), null,
                        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Alfread Status Downloader", style = MaterialTheme.typography.titleMedium)
                        Text("Versi $version", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Aplikasi ini tidak berafiliasi dengan WhatsApp. Gunakan dengan bijak: " +
                        "hormati privasi dan hak cipta pemilik status.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus riwayat?") },
            text = { Text("Hanya catatan riwayat yang dihapus. File di folder Download tetap aman.") },
            confirmButton = { TextButton(onClick = { onClearHistory(); confirmClear = false }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } }
        )
    }
}
