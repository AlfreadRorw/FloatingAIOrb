package com.alfread.alfvoicecontrol.ui.commands

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alfread.alfvoicecontrol.AppContainer
import com.alfread.alfvoicecontrol.commands.CommandExecutor
import com.alfread.alfvoicecontrol.data.VoiceCommand
import com.alfread.alfvoicecontrol.voice.CommandMatcher
import com.alfread.alfvoicecontrol.voice.SpeechRecognitionManager
import com.alfread.alfvoicecontrol.voice.SpeechResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCommandsScreen(
    onBack: () -> Unit,
    onAddCommand: () -> Unit,
    onEditCommand: (String) -> Unit
) {
    val context = LocalContext.current
    val container = remember { AppContainer.getInstance(context) }
    val scope = rememberCoroutineScopeCompat()

    val commands by container.commandDao.observeAll().collectAsState(initial = emptyList<VoiceCommand>())
    var pendingDelete by remember { mutableStateOf<VoiceCommand?>(null) }
    var testResultText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Commands") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCommand) {
                Icon(Icons.Filled.Add, contentDescription = "Add command")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (commands.isEmpty()) {
                Text(
                    "No commands yet. Tap + to create your first voice command.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            LazyColumn {
                items(commands) { command ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(command.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("\"${command.triggerPhrase}\"", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Status: ${if (command.enabled) "Active" else "Inactive"}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Switch(
                                    checked = command.enabled,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            container.commandDao.update(command.copy(enabled = checked))
                                        }
                                    }
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = {
                                    testResultText = "Listening..."
                                    val speech = SpeechRecognitionManager(context) { result ->
                                        testResultText = when (result) {
                                            is SpeechResult.Success -> {
                                                val score = CommandMatcher.similarity(
                                                    CommandMatcher.normalize(result.text),
                                                    CommandMatcher.normalize(command.triggerPhrase)
                                                )
                                                val matched = score >= 0.72f
                                                "Detected: \"${result.text}\"\n" +
                                                    "Matched command: ${if (matched) command.name else "none"}\n" +
                                                    "Confidence: ${(score * 100).toInt()}%"
                                            }
                                            is SpeechResult.NoSpeech -> "No speech detected. Try again."
                                            is SpeechResult.Error -> "Error: ${result.message}"
                                            is SpeechResult.Listening -> "Listening..."
                                        }
                                        if (testResultText?.contains("Matched command: ${command.name}") == true) {
                                            val execResult = CommandExecutor.execute(context, command)
                                            CommandExecutor.showResultToast(context, execResult)
                                        }
                                    }
                                    speech.startListening()
                                }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Test")
                                }
                                IconButton(onClick = { onEditCommand(command.id) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { pendingDelete = command }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }

            testResultText?.let { text ->
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    pendingDelete?.let { command ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete command?") },
            text = { Text("\"${command.name}\" will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.audioRecorder.deleteSample(command.audioFilePath)
                        container.commandDao.delete(command)
                    }
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
