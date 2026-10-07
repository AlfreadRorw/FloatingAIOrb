package com.alfread.alfvision.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alfread.alfvision.core.*
import com.alfread.alfvision.core.RegionState
import com.alfread.alfvision.data.local.AIProfileEntity
import com.alfread.alfvision.core.AppContainer
import com.alfread.alfvision.domain.ShizukuCompat
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.util.PermissionUtils
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenOverlaySettings: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apiConfigured by viewModel.apiKeyConfigured.collectAsStateWithLifecycle()
    val connection by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val regions by AppContainer.regions.observe().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var profileDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<AIProfileEntity?>(null) }
    var customProfileName by remember { mutableStateOf("") }
    var customPrompt by remember { mutableStateOf("") }
    var customTemperature by remember { mutableFloatStateOf(settings.temperature) }
    var customMaxTokens by remember { mutableIntStateOf(settings.maxTokens) }
    var customPreferredModel by remember { mutableStateOf(settings.selectedModel) }
    var renameItem by remember { mutableStateOf<com.alfread.alfvision.data.local.RegionPresetEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
        item {
            SectionCard("AI") {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Groq API Key") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    supportingText = { Text(if (apiConfigured) "Protected with Android Keystore" else "Not configured") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                    Button(onClick = { viewModel.saveApiKey(apiKey); apiKey = "" }) { Text("Save") }
                    OutlinedButton(onClick = viewModel::deleteApiKey) { Text("Delete") }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Connection: $connection", Modifier.weight(1f))
                    Button(onClick = viewModel::testConnection, enabled = apiConfigured) { Text("Test") }
                }
                Text("Active model: ${settings.selectedModel}", style = MaterialTheme.typography.bodySmall)
                Text("Last successful request: ${settings.lastSuccessfulRequest}", style = MaterialTheme.typography.bodySmall)
                Text("Last error: ${settings.lastError}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = viewModel::refreshModels, enabled = apiConfigured) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Refresh Models")
                }
                if (models.isNotEmpty()) {
                    models.forEach { model ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(model.id, Modifier.weight(1f))
                            TextButton(onClick = { viewModel.setModel(model.id) }) { Text(if (settings.selectedModel == model.id) "Active" else "Use") }
                        }
                    }
                }
            }
        }
        item {
            SectionCard("Response") {
                Text("Response style")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResponseStyle.values().forEach { style ->
                        FilterChip(selected = settings.responseStyle == style, onClick = { viewModel.setResponseStyle(style) }, label = { Text(style.name.replace('_',' ')) })
                    }
                }
                Text("Temperature: ${"%.2f".format(settings.temperature)}")
                Slider(settings.temperature, { viewModel.setTemperature(it) }, valueRange = 0f..2f)
                Text("Max tokens: ${settings.maxTokens}")
                Slider(settings.maxTokens.toFloat(), { viewModel.setMaxTokens(it.toInt()) }, valueRange = 128f..8192f, steps = 31)
            }
        }
        item {
            SectionCard("Region Presets") {
                if (regions.isEmpty()) Text("Belum ada preset. Gunakan Region di Vision/Floating Panel lalu Save.", style = MaterialTheme.typography.bodySmall)
                regions.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name)
                            Text("${item.width} × ${item.height} @ ${item.x},${item.y}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { RegionState.current = AppContainer.regions.toRegion(item) }) { Text("Use") }
                        TextButton(onClick = { scope.launch { AppContainer.regions.save("${item.name} Copy", AppContainer.regions.toRegion(item)) } }) { Text("Duplicate") }
                        IconButton(onClick = { renameItem = item; renameText = item.name }) { Text("Rename") }
                        IconButton(onClick = { scope.launch { AppContainer.regions.delete(item) } }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        }
        item {
            SectionCard("AI Profiles") {
                profiles.forEach { profile ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        RadioButton(selected = settings.activeProfile == profile.name, onClick = { viewModel.setProfile(profile.name) })
                        Column(Modifier.weight(1f)) {
                            Text(profile.name)
                            Text(profile.systemPrompt.take(90), style = MaterialTheme.typography.bodySmall)
                            Text("Model: ${profile.preferredModel} • Temp: ${"%.2f".format(profile.temperature)} • Max: ${profile.maxTokens}", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = {
                            editingProfile = profile
                            customProfileName = profile.name
                            customPrompt = profile.systemPrompt
                            customTemperature = profile.temperature
                            customMaxTokens = profile.maxTokens
                            customPreferredModel = profile.preferredModel
                            profileDialog = true
                        }) { Icon(Icons.Default.Settings, "Edit profile") }
                        IconButton(onClick = {
                            val now = System.currentTimeMillis()
                            scope.launch {
                                var copyName = "${profile.name} Copy"
                                var suffix = 2
                                while (AppContainer.profiles.get(copyName) != null) {
                                    copyName = "${profile.name} Copy $suffix"
                                    suffix++
                                }
                                AppContainer.profiles.save(profile.copy(name = copyName, createdAt = now, updatedAt = now))
                            }
                        }) { Icon(Icons.Default.Add, "Duplicate profile") }
                        if (profile.name != "General") IconButton(onClick = { scope.launch { AppContainer.profiles.delete(profile.name) } }) { Icon(Icons.Default.Delete, "Delete profile") }
                    }
                }
                OutlinedButton(onClick = {
                    editingProfile = null
                    customProfileName = ""
                    customPrompt = ""
                    customTemperature = settings.temperature
                    customMaxTokens = settings.maxTokens
                    customPreferredModel = settings.selectedModel
                    profileDialog = true
                }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Create profile") }
            }
        }
        item {
            SectionCard("Vision") {
                SettingSwitch("Send Only Selected Region", settings.sendOnlySelectedRegion, viewModel::setSendOnlyRegion)
                SettingSwitch("Auto Analyze", settings.autoAnalyze, viewModel::setAutoAnalyze)
                Text("Auto analyze interval")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(500 to "0.5s", 1000 to "1s", 2000 to "2s", 5000 to "5s", 10000 to "10s", 30000 to "30s").forEach { (value, label) -> FilterChip(selected = settings.autoAnalyzeIntervalMs == value, onClick = { viewModel.setAutoAnalyzeIntervalMs(value) }, label = { Text(label) }) }
                }
                SettingSwitch("Gaming Mode", settings.gamingMode, viewModel::setGamingMode)
                Text("Capture quality: ${settings.captureQuality}")
                Slider(settings.captureQuality.toFloat(), { viewModel.setCaptureQuality(it.toInt()) }, valueRange = 50f..100f, steps = 9)
                Text("Default is Capture Once. Auto mode should be enabled deliberately because it can consume battery, bandwidth, and API quota.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("Floating") {
                Text("Opacity: ${"%.2f".format(settings.opacity)}")
                Slider(settings.opacity, { viewModel.setOpacity(it) }, valueRange = 0.35f..1f)
                SettingSwitch("Snap to edge", settings.snap, viewModel::setSnap)
                SettingSwitch("Lock position", settings.lockPosition, viewModel::setLockPosition)
                SettingSwitch("Animation", settings.animation, viewModel::setAnimation)
                SettingSwitch("Auto hide", settings.autoHide, viewModel::setAutoHide)
                SettingSwitch("Blur / frosted visual", settings.blur, viewModel::setBlur)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PanelStyle.values().forEach { style -> FilterChip(selected = settings.panelStyle == style, onClick = { viewModel.setPanelStyle(style) }, label = { Text(style.name) }) }
                }
            }
        }
        item {
            SectionCard("Appearance") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.values().forEach { mode -> FilterChip(selected = settings.theme == mode, onClick = { viewModel.setTheme(mode) }, label = { Text(mode.name) }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccentColor.values().forEach { accent -> FilterChip(selected = settings.accent == accent, onClick = { viewModel.setAccent(accent) }, label = { Text(accent.name) }) }
                }
            }
        }
        item {
            SectionCard("Privacy") {
                SettingSwitch("Save conversations", settings.saveHistory, viewModel::setSaveHistory)
                SettingSwitch("Save screenshots", settings.saveScreenshots, viewModel::setSaveScreenshots)
                Text("Auto delete")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    AutoDeletePeriod.values().forEach { period -> FilterChip(selected = settings.autoDelete == period, onClick = { viewModel.setAutoDelete(period) }, label = { Text(period.name.replace('_',' ')) }) }
                }
                Text("Screenshots are not permanently stored by default. Images are sent only when a request is made.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { scope.launch { AppContainer.history.deleteAll() } }) { Text("Clear History") }
                    OutlinedButton(onClick = { context.filesDir.resolve("screenshots").deleteRecursively() }) { Text("Clear Screenshots") }
                    OutlinedButton(onClick = { context.cacheDir.deleteRecursively() }) { Text("Clear Cache") }
                }
                OutlinedButton(onClick = viewModel::deleteApiKey) { Text("Delete API Key") }
            }
        }
        item {
            SectionCard("Permissions") {
                PermissionButton("Overlay permission", PermissionUtils.overlayGranted(androidx.compose.ui.platform.LocalContext.current)) { onOpenOverlaySettings() }
                PermissionButton("Microphone", PermissionUtils.microphoneGranted(androidx.compose.ui.platform.LocalContext.current)) { onRequestMicrophone() }
                PermissionButton("Notifications", PermissionUtils.notificationsGranted(androidx.compose.ui.platform.LocalContext.current)) { onRequestNotifications() }
            }
        }
        item {
            SectionCard("Advanced") {
                Text("Network timeout: ${settings.timeoutSeconds}s")
                Slider(settings.timeoutSeconds.toFloat(), { viewModel.setTimeout(it.toInt()) }, valueRange = 10f..120f, steps = 21)
                Text("Retry count: ${settings.retryCount}")
                Slider(settings.retryCount.toFloat(), { viewModel.setRetryCount(it.toInt()) }, valueRange = 0f..5f, steps = 4)
                SettingSwitch("Debug mode", settings.debugMode, viewModel::setDebug)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Shizuku")
                        Text(ShizukuCompat(androidx.compose.ui.platform.LocalContext.current).statusText(), style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.Security, null)
                }
                Text(ShizukuCompat(androidx.compose.ui.platform.LocalContext.current).enhancedModeExplanation(), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("About") {
                Text("ALF Vision Panel 1.0.0")
                Text("Native Kotlin + Jetpack Compose + Material 3")
                Text("Privacy: screen capture is user-triggered through MediaProjection. No backend stores your API key.", style = MaterialTheme.typography.bodySmall)
                Text("Groq vision default: ${Constants.DEFAULT_MODEL}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (renameItem != null) {
        AlertDialog(
            onDismissRequest = { renameItem = null },
            confirmButton = {
                Button(onClick = {
                    val item = renameItem
                    if (item != null && renameText.isNotBlank()) scope.launch {
                        AppContainer.regions.update(item.copy(name = renameText.trim()))
                        renameItem = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameItem = null }) { Text("Cancel") } },
            title = { Text("Rename region") },
            text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("Name") }, singleLine = true) }
        )
    }

    if (profileDialog) {
        AlertDialog(
            onDismissRequest = { profileDialog = false; editingProfile = null },
            confirmButton = {
                Button(onClick = {
                    if (customProfileName.isNotBlank() && customPrompt.isNotBlank()) {
                        val now = System.currentTimeMillis()
                        val old = editingProfile
                        scope.launch {
                            if (old != null && old.name != customProfileName.trim()) {
                                AppContainer.profiles.delete(old.name)
                            }
                            val profile = AIProfileEntity(
                                name = customProfileName.trim(),
                                systemPrompt = customPrompt.trim(),
                                temperature = customTemperature.coerceIn(0f, 2f),
                                maxTokens = customMaxTokens.coerceIn(128, 16384),
                                preferredModel = customPreferredModel.trim().ifBlank { settings.selectedModel },
                                createdAt = old?.createdAt ?: now,
                                updatedAt = now
                            )
                            AppContainer.profiles.save(profile)
                            viewModel.setProfile(profile.name)
                            profileDialog = false
                            editingProfile = null
                            customProfileName = ""
                            customPrompt = ""
                        }
                    }
                }) { Text(if (editingProfile == null) "Create" else "Save") }
            },
            dismissButton = { TextButton(onClick = { profileDialog = false; editingProfile = null }) { Text("Cancel") } },
            title = { Text(if (editingProfile == null) "Create AI Profile" else "Edit AI Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(customProfileName, { customProfileName = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(customPrompt, { customPrompt = it }, label = { Text("System prompt") }, minLines = 4)
                    OutlinedTextField(customPreferredModel, { customPreferredModel = it }, label = { Text("Preferred model") }, singleLine = true)
                    Text("Temperature: ${"%.2f".format(customTemperature)}")
                    Slider(customTemperature, { customTemperature = it }, valueRange = 0f..2f)
                    Text("Max tokens: $customMaxTokens")
                    Slider(customMaxTokens.toFloat(), { customMaxTokens = it.toInt() }, valueRange = 128f..8192f, steps = 31)
                    if (models.isNotEmpty()) {
                        Text("Available active models", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            models.take(10).forEach { model ->
                                FilterChip(
                                    selected = customPreferredModel == model.id,
                                    onClick = { customPreferredModel = model.id },
                                    label = { Text(model.id.take(26), style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = { Text(title, style = MaterialTheme.typography.titleMedium); content() }) }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Switch(checked, onChange) }
}

@Composable
private fun PermissionButton(title: String, granted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); AssistChip(onClick = onClick, label = { Text(if (granted) "Granted" else "Grant") }) }
}
