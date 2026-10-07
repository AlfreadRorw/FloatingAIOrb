package com.alfread.alfvision.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alfread.alfvision.core.AppContainer
import com.alfread.alfvision.data.local.ConversationEntity
import com.alfread.alfvision.ui.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import androidx.compose.ui.platform.LocalContext

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<ConversationEntity?>(null) }
    var messages by remember { mutableStateOf(emptyList<com.alfread.alfvision.data.local.MessageEntity>()) }
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    val context = LocalContext.current

    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
            title = { Text(selected!!.title) },
            text = {
                Column(Modifier.heightIn(max = 420.dp)) {
                    messages.forEach { Text("${it.role}: ${it.content}", Modifier.padding(vertical = 5.dp)) }
                }
            }
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            confirmButton = { Button(onClick = { val item = renameTarget; if (item != null && renameText.isNotBlank()) scope.launch { AppContainer.history.renameConversation(item, renameText); renameTarget = null } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
            title = { Text("Rename conversation") },
            text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("Name") }, singleLine = true) }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("History", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { scope.launch { AppContainer.history.deleteAll() } }) {
                Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Delete All")
            }
        }
        if (conversations.isEmpty()) {
            Text("Belum ada percakapan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(conversations) { item ->
                    Card(onClick = {
                        selected = item
                        scope.launch { messages = AppContainer.history.getMessages(item.id) }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.profile, style = MaterialTheme.typography.labelSmall)
                            Text(SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.updatedAt)), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { selected = item; scope.launch { messages = AppContainer.history.getMessages(item.id) } }) { Text("Open") }
                                TextButton(onClick = {
                                    scope.launch {
                                        val cardMessages = AppContainer.history.getMessages(item.id)
                                        val transcript = messagesForShare(cardMessages)
                                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, transcript)
                                        }, "Share conversation"))
                                    }
                                }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(3.dp)); Text("Share") }
                                TextButton(onClick = {
                                    scope.launch {
                                        val cardMessages = AppContainer.history.getMessages(item.id)
                                        val transcript = messagesForShare(cardMessages)
                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText(item.title, transcript))
                                    }
                                }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(3.dp)); Text("Copy") }
                                TextButton(onClick = { renameTarget = item; renameText = item.title }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(3.dp)); Text("Rename") }
                                TextButton(onClick = { scope.launch { AppContainer.history.deleteConversation(item) } }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(3.dp)); Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun messagesForShare(messages: List<com.alfread.alfvision.data.local.MessageEntity>): String = messages.joinToString("\n\n") { "${it.role}: ${it.content}" }

