package com.alfread.alfvision.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alfread.alfvision.data.prefs.AppSettings

@Composable
fun HomeScreen(
    settings: AppSettings,
    apiConfigured: Boolean,
    connectionStatus: String,
    overlayGranted: Boolean,
    microphoneGranted: Boolean,
    notificationsGranted: Boolean,
    onStartVision: () -> Unit,
    onStopVision: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ALF VISION", style = MaterialTheme.typography.headlineMedium)
        Text("AI Screen Assistant", color = MaterialTheme.colorScheme.onSurfaceVariant)

        StatusCard("AI STATUS", if (apiConfigured) "READY" else "API KEY NEEDED", apiConfigured)
        StatusCard("GROQ", connectionStatus, connectionStatus.startsWith("Connected") || connectionStatus.startsWith("Saved"))
        StatusCard("SCREEN CAPTURE", "MediaProjection on demand", true)
        StatusCard("FLOATING PANEL", if (overlayGranted) "READY" else "OVERLAY PERMISSION NEEDED", overlayGranted)

        Button(onClick = onStartVision, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("START VISION")
        }
        OutlinedButton(onClick = onStopVision, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Close, null); Spacer(Modifier.width(8.dp)); Text("STOP VISION")
        }

        Text("Permission Center", style = MaterialTheme.typography.titleMedium)
        PermissionRow("Overlay Permission", overlayGranted, Icons.Default.Visibility) { onOpenOverlaySettings() }
        PermissionRow("Microphone", microphoneGranted, Icons.Default.Mic) { onRequestMicrophone() }
        PermissionRow("Notifications", notificationsGranted, Icons.Default.Notifications) { onRequestNotifications() }
        PermissionRow("API Key", apiConfigured, Icons.Default.Lock, onOpenSettings)

        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("OPEN SETTINGS")
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String, good: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (good) Icons.Default.CheckCircle else Icons.Default.Close, null, tint = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(10.dp))
            Text(title, Modifier.weight(1f))
            AssistChip(onClick = onClick, label = { Text(if (granted) "Granted" else "Grant") })
        }
    }
}
