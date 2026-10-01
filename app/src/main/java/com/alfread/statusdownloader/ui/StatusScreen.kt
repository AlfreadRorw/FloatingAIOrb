package com.alfread.statusdownloader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alfread.statusdownloader.R
import com.alfread.statusdownloader.data.*

@Composable
fun StatusScreen(
    ui: StatusUiState,
    settings: AppSettings,
    history: List<HistoryRecord>,
    onGrantPermission: () -> Unit,
    onRefresh: () -> Unit,
    onFilter: (StatusFilter) -> Unit,
    onToggleSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onDownload: (StatusItem) -> Unit,
    onDownloadSelected: () -> Unit,
    onPreview: (StatusItem) -> Unit,
    onOpenWhatsApp: () -> Unit
) {
    val doneNames = remember(history) { history.map { it.originalName }.toSet() }
    val visible = remember(ui.items, ui.filter) {
        when (ui.filter) {
            StatusFilter.ALL -> ui.items
            StatusFilter.IMAGE -> ui.items.filter { it.kind == MediaKind.IMAGE }
            StatusFilter.VIDEO -> ui.items.filter { it.kind == MediaKind.VIDEO }
        }
    }
    val selecting = ui.selected.isNotEmpty()
    val imgCount = ui.items.count { it.kind == MediaKind.IMAGE }
    val vidCount = ui.items.count { it.kind == MediaKind.VIDEO }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Status",
            subtitle = if (!ui.hasPermission) "Izin akses diperlukan" else "${ui.items.size} status ditemukan",
            actions = {
                if (ui.hasPermission) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat ulang")
                    }
                }
            }
        )

        if (!ui.hasPermission) {
            EmptyState(
                title = "Izin akses file diperlukan",
                desc = "Aktifkan \"Akses semua file\" agar Alfread bisa membaca folder .Statuses " +
                    "milik WhatsApp dan menyimpan hasilnya ke folder Download.",
                actionLabel = "Berikan Izin",
                onAction = onGrantPermission
            )
        } else {
            if (ui.loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            PillGroup(
                options = listOf("Semua ${ui.items.size}", "Foto $imgCount", "Video $vidCount"),
                selectedIndex = ui.filter.ordinal,
                onSelect = { onFilter(StatusFilter.values()[it]) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            AnimatedVisibility(visible = selecting) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = "Batal pilih")
                    }
                    Text(
                        "${ui.selected.size} dipilih",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onSelectAll(visible.map { it.key }) }) { Text("Pilih semua") }
                    Button(onClick = onDownloadSelected) {
                        Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Unduh")
                    }
                }
            }

            if (visible.isEmpty() && !ui.loading) {
                EmptyState(
                    title = "Belum ada status",
                    desc = "Lihat dulu status teman di WhatsApp, lalu kembali ke sini. " +
                        "Daftar akan diperbarui otomatis.",
                    actionLabel = "Buka WhatsApp",
                    onAction = onOpenWhatsApp
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(settings.gridColumns),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visible, key = { it.key }) { item ->
                        StatusCard(
                            item = item,
                            selected = item.key in ui.selected,
                            selecting = selecting,
                            downloaded = item.file.name in doneNames,
                            busy = item.key in ui.busy,
                            onClick = { if (selecting) onToggleSelect(item.key) else onPreview(item) },
                            onLongClick = { onToggleSelect(item.key) },
                            onDownload = { onDownload(item) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusCard(
    item: StatusItem,
    selected: Boolean,
    selecting: Boolean,
    downloaded: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .aspectRatio(0.72f)
            .clip(shape)
            .background(scheme.surfaceVariant)
            .then(if (selected) Modifier.border(3.dp, scheme.onBackground, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = item.file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.kind == MediaKind.VIDEO) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Text(formatSize(item.size), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }

        if (selecting) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color(0x66000000))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(enabled = !busy && !downloaded, onClick = onDownload),
                contentAlignment = Alignment.Center
            ) {
                when {
                    busy -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black
                    )
                    downloaded -> Icon(Icons.Default.Check, "Sudah diunduh", tint = Color.Black, modifier = Modifier.size(18.dp))
                    else -> Icon(painterResource(R.drawable.ic_download), "Unduh", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
