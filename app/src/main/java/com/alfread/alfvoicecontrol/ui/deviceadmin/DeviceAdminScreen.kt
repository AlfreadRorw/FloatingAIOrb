package com.alfread.alfvoicecontrol.ui.deviceadmin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alfread.alfvoicecontrol.screen.ScreenController

@Composable
fun DeviceAdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isActive by remember { mutableStateOf(ScreenController.isDeviceAdminActive(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isActive = ScreenController.isDeviceAdminActive(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Administrator") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Status:", style = MaterialTheme.typography.bodyLarge)
            Text(if (isActive) "Enabled" else "Not enabled", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "Screen-off command requires Device Administrator permission. ALF only " +
                    "uses it to lock the screen - it cannot read your PIN, pattern or password.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            if (!isActive) {
                Button(onClick = { launcher.launch(ScreenController.requestDeviceAdminIntent(context)) }) {
                    Text("ENABLE")
                }
            }
        }
    }
}
