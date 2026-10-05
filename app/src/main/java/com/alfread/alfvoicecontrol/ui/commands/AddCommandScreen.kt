package com.alfread.alfvoicecontrol.ui.commands

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alfread.alfvoicecontrol.AppContainer
import com.alfread.alfvoicecontrol.data.ActionType
import com.alfread.alfvoicecontrol.data.InstalledAppInfo
import com.alfread.alfvoicecontrol.data.InstalledAppsRepository
import com.alfread.alfvoicecontrol.data.VoiceCommand
import kotlinx.coroutines.launch
import java.util.UUID

private enum class RecordingStage { IDLE, COUNTDOWN, RECORDING, DONE }

@Composable
fun AddCommandScreen(
    existingCommandId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val container = remember { AppContainer.getInstance(context) }
    val scope = rememberCoroutineScope()

    var commandId by remember { mutableStateOf(existingCommandId ?: UUID.randomUUID().toString()) }
    var commandName by remember { mutableStateOf("") }
    var triggerPhrase by remember { mutableStateOf("") }
    var actionType by remember { mutableStateOf(ActionType.OPEN_APP) }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var audioFilePath by remember { mutableStateOf<String?>(null) }
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var appMenuExpanded by remember { mutableStateOf(false) }
    var recordingStage by remember { mutableStateOf(RecordingStage.IDLE) }
    var countdownValue by remember { mutableStateOf(3) }

    val installedApps = remember { InstalledAppsRepository.listLaunchableApps(context) }

    LaunchedEffect(existingCommandId) {
        if (existingCommandId != null) {
            container.commandDao.getById(existingCommandId)?.let { existing ->
                commandId = existing.id
                commandName = existing.name
                triggerPhrase = existing.triggerPhrase
                actionType = existing.actionType
                audioFilePath = existing.audioFilePath
                selectedApp = installedApps.firstOrNull { it.packageName == existing.targetPackage }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCountdownAndRecord(scope, container, commandId) { stage -> recordingStage = stage }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingCommandId == null) "Add Command" else "Edit Command") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            OutlinedTextField(
                value = commandName,
                onValueChange = { commandName = it },
                label = { Text("Command name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = actionMenuExpanded,
                onExpandedChange = { actionMenuExpanded = it }
            ) {
                TextField(
                    value = actionTypeLabel(actionType),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Action") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionMenuExpanded) },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = actionMenuExpanded, onDismissRequest = { actionMenuExpanded = false }) {
                    ActionType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(actionTypeLabel(type)) },
                            onClick = { actionType = type; actionMenuExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (actionType == ActionType.OPEN_APP) {
                ExposedDropdownMenuBox(
                    expanded = appMenuExpanded,
                    onExpandedChange = { appMenuExpanded = it }
                ) {
                    TextField(
                        value = selectedApp?.label ?: "Choose application",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Application") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appMenuExpanded) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = appMenuExpanded, onDismissRequest = { appMenuExpanded = false }) {
                        installedApps.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.label) },
                                onClick = { selectedApp = app; appMenuExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = triggerPhrase,
                onValueChange = { triggerPhrase = it },
                label = { Text("Trigger phrase") },
                placeholder = { Text("e.g. \"Buka TikTok\"") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    when (recordingStage) {
                        RecordingStage.IDLE -> {
                            Text(if (audioFilePath != null) "Voice sample saved" else "No recording yet")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    startCountdownAndRecord(scope, container, commandId) { stage -> recordingStage = stage }
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }) { Text("RECORD") }
                        }
                        RecordingStage.COUNTDOWN -> {
                            Text("Get Ready", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("$countdownValue", style = MaterialTheme.typography.titleLarge)
                        }
                        RecordingStage.RECORDING -> {
                            Text("SPEAK NOW", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                val path = container.audioRecorder.stopRecording()
                                audioFilePath = path
                                recordingStage = RecordingStage.DONE
                            }) { Text("STOP") }
                        }
                        RecordingStage.DONE -> {
                            Text("Recording complete")
                            Spacer(Modifier.height(8.dp))
                            Row {
                                OutlinedButton(onClick = {
                                    audioFilePath?.let { path ->
                                        runCatching {
                                            MediaPlayer().apply {
                                                setDataSource(path)
                                                prepare()
                                                start()
                                            }
                                        }
                                    }
                                }) { Text("PLAY") }
                                Spacer(Modifier.height(0.dp))
                                OutlinedButton(onClick = {
                                    container.audioRecorder.deleteSample(audioFilePath)
                                    audioFilePath = null
                                    recordingStage = RecordingStage.IDLE
                                }) { Text("RE-RECORD") }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        val command = VoiceCommand(
                            id = commandId,
                            name = commandName.ifBlank { triggerPhrase },
                            triggerPhrase = triggerPhrase,
                            actionType = actionType,
                            targetPackage = if (actionType == ActionType.OPEN_APP) selectedApp?.packageName else null,
                            targetAppLabel = if (actionType == ActionType.OPEN_APP) selectedApp?.label else null,
                            audioFilePath = audioFilePath,
                            enabled = true
                        )
                        container.commandDao.upsert(command)
                        onSaved()
                    }
                },
                enabled = triggerPhrase.isNotBlank() && (actionType != ActionType.OPEN_APP || selectedApp != null),
                modifier = Modifier.fillMaxWidth()
            ) { Text("SAVE COMMAND") }
        }
    }
}

private fun startCountdownAndRecord(
    scope: kotlinx.coroutines.CoroutineScope,
    container: AppContainer,
    commandId: String,
    onStageChanged: (RecordingStage) -> Unit
) {
    scope.launch {
        onStageChanged(RecordingStage.COUNTDOWN)
        kotlinx.coroutines.delay(1000)
        onStageChanged(RecordingStage.COUNTDOWN)
        kotlinx.coroutines.delay(1000)
        kotlinx.coroutines.delay(1000)
        runCatching { container.audioRecorder.startRecording(commandId) }
        onStageChanged(RecordingStage.RECORDING)
    }
}

private fun actionTypeLabel(type: ActionType): String = when (type) {
    ActionType.SCREEN_ON -> "Screen On"
    ActionType.SCREEN_OFF -> "Screen Off"
    ActionType.OPEN_APP -> "Open App"
}
