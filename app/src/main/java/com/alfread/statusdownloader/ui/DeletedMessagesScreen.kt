package com.alfread.statusdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import com.alfread.statusdownloader.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.alfread.statusdownloader.data.DeletedMessage
import com.alfread.statusdownloader.data.DeletedMessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeletedMessagesScreen(
    messages: List<DeletedMessage>,
    notificationAccess: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onDelete: (DeletedMessage) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title = "Pesan Dihapus",
            subtitle = "${messages.size} notifikasi tersimpan",
            actions = {
                if (messages.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Bersihkan") }
                }
            }
        )

        if (!notificationAccess) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Akses notifikasi belum aktif", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Aktifkan akses notifikasi agar Alfread bisa mengarsipkan pesan WhatsApp sebelum pesan tersebut dihapus.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onOpenNotificationSettings) {
                        Text("Aktifkan akses")
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            EmptyState(
                title = "Belum ada arsip",
                desc = "Pesan yang masuk lewat notifikasi akan muncul di sini."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { item ->
                    MessageRow(item, onDelete = { onDelete(item) })
                }
            }
        }
    }
}

private fun typeIcon(type: DeletedMessageType): Int = when (type) {
    DeletedMessageType.CHAT -> R.drawable.ic_msg_chat
    DeletedMessageType.VOICE -> R.drawable.ic_msg_voice
    DeletedMessageType.PHOTO -> R.drawable.ic_msg_photo
    DeletedMessageType.VIDEO -> R.drawable.ic_msg_video
    DeletedMessageType.DOCUMENT -> R.drawable.ic_msg_document
    DeletedMessageType.UNKNOWN -> R.drawable.ic_msg_chat
}

@Composable
private fun MessageRow(item: DeletedMessage, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val type = when (item.type) {
        DeletedMessageType.CHAT -> "Chat"
        DeletedMessageType.VOICE -> "VN / Audio"
        DeletedMessageType.PHOTO -> "Foto"
        DeletedMessageType.VIDEO -> "Video"
        DeletedMessageType.DOCUMENT -> "Dokumen"
        DeletedMessageType.UNKNOWN -> "Pesan"
    }

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(typeIcon(item.type)),
            contentDescription = type,
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.sender, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(type, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (item.deletedMarker && item.text.isBlank()) "Pesan dihapus oleh WhatsApp"
                else item.text.ifBlank { "Notifikasi tanpa teks" },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${if (item.deletedMarker) "Ditandai dihapus • " else ""}${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.time))}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            if (item.mediaPath != null) {
                Text("Media notifikasi tersimpan", style = MaterialTheme.typography.labelSmall)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Hapus")
        }
    }
}
