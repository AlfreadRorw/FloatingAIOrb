package com.alfread.alfvoicecontrol.ui.home

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alfread.alfvoicecontrol.AppContainer
import com.alfread.alfvoicecontrol.data.VoiceCommand
import com.alfread.alfvoicecontrol.service.VoiceListeningService

@Composable
fun HomeScreen(
    onManageCommands: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val container = remember(context) { AppContainer.getInstance(context) }

    val settings by container.settingsRepository.settingsFlow.collectAsState(
        initial = com.alfread.alfvoicecontrol.data.AlfSettings()
    )
    val commands by container.commandDao.observeAll().collectAsState(initial = emptyList<VoiceCommand>())
    val isListening by VoiceListeningService.isRunning.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("ALF VOICE CONTROL", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("Voice Assistant", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Listening")
                    Text(if (isListening) "● ACTIVE" else "○ OFF")
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Wake Word")
                    Text("\"${settings.wakeWord}\"")
                }
                Spacer(Modifier.height(16.dp))
                if (isListening) {
                    OutlinedButton(
                        onClick = { VoiceListeningService.stop(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("STOP LISTENING") }
                } else {
                    Button(
                        onClick = { VoiceListeningService.start(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("START LISTENING") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("VOICE COMMANDS", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(commands) { command ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(command.name, style = MaterialTheme.typography.bodyLarge)
                        Text("\"${command.triggerPhrase}\"", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onManageCommands, modifier = Modifier.fillMaxWidth()) {
            Text("MANAGE COMMANDS")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("SETTINGS")
        }
    }
}

@Composable
private fun <T> remember(key: Any?, calculation: () -> T): T =
    androidx.compose.runtime.remember(key) { calculation() }
