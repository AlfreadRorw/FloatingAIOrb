package com.alfread.alfvoicecontrol.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alfread.alfvoicecontrol.AppContainer
import com.alfread.alfvoicecontrol.data.AlfSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageCommands: () -> Unit,
    onConfigurePin: () -> Unit,
    onConfigureDeviceAdmin: () -> Unit
) {
    val context = LocalContext.current
    val container = remember { AppContainer.getInstance(context) }
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = AlfSettings())
    var wakeWordInput by remember(settings.wakeWord) { mutableStateOf(settings.wakeWord) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingRow("Voice Control", settings.voiceControlEnabled) {
                scope.launch { container.settingsRepository.setVoiceControlEnabled(it) }
            }
            Divider()

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = wakeWordInput,
                    onValueChange = { wakeWordInput = it },
                    label = { Text("Wake Word") },
                    modifier = Modifier.weight(1f)
                )
            }
            Button(onClick = { scope.launch { container.settingsRepository.setWakeWord(wakeWordInput) } }) {
                Text("SAVE WAKE WORD")
            }
            Divider()

            SettingRow("Listening Mode: Background", settings.backgroundListening) {
                scope.launch { container.settingsRepository.setBackgroundListening(it) }
            }
            Divider()
            SettingRow("Screen Wake", settings.screenWakeEnabled) {
                scope.launch { container.settingsRepository.setScreenWakeEnabled(it) }
            }
            Divider()
            SettingRow("Screen Off Command", settings.screenOffEnabled) {
                scope.launch { container.settingsRepository.setScreenOffEnabled(it) }
            }
            Divider()
            SettingRow("Notifications", settings.notificationsEnabled) {
                scope.launch { container.settingsRepository.setNotificationsEnabled(it) }
            }
            Divider()

            SettingsLinkRow("Voice Commands", onManageCommands)
            Divider()
            SettingsLinkRow("ALF PIN", onConfigurePin)
            Divider()
            SettingsLinkRow("Device Administrator", onConfigureDeviceAdmin)
            Divider()

            SettingsLinkRow("Battery Optimization") {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
            Divider()

            Text(
                "About: ALF Voice Control",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsLinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onClick) { Text("Manage") }
    }
}
