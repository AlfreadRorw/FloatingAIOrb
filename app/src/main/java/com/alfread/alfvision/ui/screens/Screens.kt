package com.alfread.alfvision.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.data.local.RegionPresetEntity
import com.alfread.alfvision.service.ScreenCaptureService
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.navigation.Dest
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(vm: MainViewModel, padding: PaddingValues, onStartVision: () -> Unit, onOverlay: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val network by vm.network.collectAsState()
    val apiReady = vm.hasApiKey()
    val overlayReady = Settings.canDrawOverlays(androidx.compose.ui.platform.LocalContext.current)
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ALF VISION", style = MaterialTheme.typography.headlineMedium)
                Text("AI Screen Assistant", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
        }
        StatusCard("GROQ", if (apiReady) "CONNECTED / KEY READY" else "API KEY REQUIRED", apiReady)
        StatusCard("SCREEN CAPTURE", if (ScreenCaptureService.isRunning) "READY / ACTIVE" else "NOT ACTIVE", ScreenCaptureService.isRunning)
        StatusCard("FLOATING PANEL", if (overlayReady) "READY" else "OVERLAY PERMISSION REQUIRED", overlayReady)
        StatusCard("NETWORK", if (network) "CONNECTED" else "OFFLINE", network)
        Button(onClick = onStartVision, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = true) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("START VISION") }
        if (!overlayReady) OutlinedButton(onClick = onOverlay, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Layers, null); Spacer(Modifier.width(8.dp)); Text("Grant Overlay Permission") }
        Text("Quick Actions", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickCard("Chat", Icons.Default.Chat) { vm.session.setError(null) }
            QuickCard("Capture", Icons.Default.CameraAlt) { vm.capture() }
            QuickCard("Analyze", Icons.Default.AutoAwesome) { vm.quickAction("Analyze") }
        }
        Text("Privacy", style = MaterialTheme.typography.titleLarge)
        Text("Screen capture is used only after you grant MediaProjection permission. Region mode can send only the selected area. Screenshots are not permanently saved by default.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable private fun StatusCard(label: String, value: String, ready: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun RowScope.QuickCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.weight(1f)) { Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Spacer(Modifier.height(8.dp)); Text(label) } }
}

@Composable
fun VisionScreen(vm: MainViewModel, padding: PaddingValues) {
    val settings by vm.settings.collectAsState()
    val regions by vm.regions.collectAsState()
    val image by vm.session.currentImage.collectAsState()
    val previous by vm.session.previousImage.collectAsState()
    var annotating by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RegionPresetEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    if (annotating && image != null) {
        AnnotationEditor(image = image!!, onSave = { vm.session.setImage(it); annotating = false }, onClose = { annotating = false })
    } else Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vision", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.showRegionSelector() }) { Icon(Icons.Default.Crop, null); Spacer(Modifier.width(6.dp)); Text("Select Region") }
            Button(onClick = { vm.capture() }) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Capture") }
            OutlinedButton(onClick = { vm.compare() }, enabled = image != null && previous != null) { Icon(Icons.Default.Compare, null); Spacer(Modifier.width(6.dp)); Text("Compare") }
            OutlinedButton(onClick = { annotating = true }, enabled = image != null) { Icon(Icons.Default.Draw, null); Spacer(Modifier.width(6.dp)); Text("Annotate") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Crop, null)
            Spacer(Modifier.width(8.dp))
            Text("Send only selected region")
            Spacer(Modifier.weight(1f))
            Switch(checked = settings.vision.sendOnlyRegion, onCheckedChange = { v -> vm.updateSettings { it.copy(vision = it.vision.copy(sendOnlyRegion = v)) } })
        }
        Text("Region presets", style = MaterialTheme.typography.titleLarge)
        if (regions.isEmpty()) Text("No saved regions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(regions, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text("${item.width} × ${item.height} at ${item.x}, ${item.y}") },
                    leadingContent = { Icon(Icons.Default.CropFree, null) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.session.setRegion(vmRegion(item)) }) { Icon(Icons.Default.PlayArrow, "Activate region") }
                            IconButton(onClick = { renameTarget = item; renameText = item.name }) { Icon(Icons.Default.Edit, "Rename") }
                            IconButton(onClick = { vm.duplicateRegion(item) }) { Icon(Icons.Default.ContentCopy, "Duplicate") }
                            IconButton(onClick = { vm.deleteRegion(item) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    },
                    modifier = Modifier.clickable { vm.session.setRegion(vmRegion(item)) }
                )
            }
        }
        image?.let { current ->
            Text("Current image: ${current.bytes.size} bytes", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            val preview = remember(current.bytes) { BitmapFactory.decodeByteArray(current.bytes, 0, current.bytes.size)?.asImageBitmap() }
            preview?.let { androidx.compose.foundation.Image(it, contentDescription = "Current captured screenshot", modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) }
        }
    }
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename region") },
            text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { renameTarget?.let { vm.updateRegion(it.copy(name = renameText.trim().ifBlank { it.name })) }; renameTarget = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
}

private fun vmRegion(item: RegionPresetEntity) = Region(item.x, item.y, item.width, item.height, item.screenWidth, item.screenHeight, item.displayId, item.rotation)

@Composable
fun ChatScreen(vm: MainViewModel, padding: PaddingValues, onRequestMicrophone: () -> Unit) {
    val lines by vm.session.lines.collectAsState()
    val input by vm.session.input.collectAsState()
    val busy by vm.session.busy.collectAsState()
    val error by vm.session.error.collectAsState()
    val voice by vm.session.voiceState.collectAsState()
    Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
        Text("Chat", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lines, key = { it.id }) { line -> MessageCard(line) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = input, onValueChange = vm.session::setInput, modifier = Modifier.weight(1f), minLines = 1, maxLines = 5, placeholder = { Text("Ask anything...") }, enabled = !busy)
            IconButton(onClick = { if (Build.VERSION.SDK_INT >= 23) onRequestMicrophone() }, enabled = !busy) { Icon(if (voice == com.alfread.alfvision.vision.VoiceState.LISTENING) Icons.Default.Stop else Icons.Default.Mic, "Microphone") }
            IconButton(onClick = { vm.ask(input) }, enabled = !busy && input.isNotBlank()) { Icon(Icons.Default.Send, "Send") }
        }
    }
}

@Composable private fun MessageCard(line: ChatLine) {
    val user = line.role == Role.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 340.dp)) { Text(line.content, Modifier.padding(12.dp)) }
    }
}

@Composable
fun HistoryScreen(vm: MainViewModel, padding: PaddingValues) {
    val list by vm.conversations.collectAsState()
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize().padding(padding).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::clearHistory, enabled = list.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(4.dp)); Text("Delete all") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { item ->
                Card(onClick = { vm.openConversation(item.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.updatedAt)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        IconButton(onClick = { confirmDelete = item.id }) { Icon(Icons.Default.Delete, "Delete conversation") }
                    }
                }
            }
        }
    }
    if (confirmDelete != null) AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Delete conversation?") }, text = { Text("This cannot be undone.") }, confirmButton = { TextButton(onClick = { vm.deleteConversation(confirmDelete!!); confirmDelete = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, padding: PaddingValues, onOverlay: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    val profiles by vm.profiles.collectAsState()
    var profileExpanded by remember { mutableStateOf(false) }
    var showProfileCreate by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            SectionTitle("AI")
            OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Groq API Key") }, visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show or hide key") } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveApiKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save") }
                OutlinedButton(onClick = vm::testConnection) { Text("Test Connection") }
                TextButton(onClick = vm::deleteApiKey) { Text("Delete") }
            }
            vm.groqStatus.collectAsState().value?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        }
        item {
            ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = !modelExpanded }) {
                OutlinedTextField(value = settings.activeModel, onValueChange = {}, readOnly = true, label = { Text("Model") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                    vm.models.collectAsState().value.filter { it.supportsVision }.forEach { model -> DropdownMenuItem(text = { Text(model.id) }, onClick = { vm.updateSettings { it.copy(activeModel = model.id) }; modelExpanded = false }) }
                }
            }
        }
        item {
            SectionTitle("Response")
            EnumDropdown("Style", settings.responseStyle.label, ResponseStyle.entries) { selected -> vm.updateSettings { it.copy(responseStyle = selected) } }
            SliderRow("Temperature", settings.temperature, 0f..2f) { v -> vm.updateSettings { it.copy(temperature = v) } }
            SliderRow("Max Tokens", settings.maxTokens.toFloat(), 128f..16384f) { v -> vm.updateSettings { it.copy(maxTokens = v.toInt()) } }
        }
        item {
            SectionTitle("Profile")
            ExposedDropdownMenuBox(expanded = profileExpanded, onExpandedChange = { profileExpanded = !profileExpanded }) {
                val selected = profiles.firstOrNull { it.id == settings.activeProfileId }?.name ?: "General"
                OutlinedTextField(value = selected, onValueChange = {}, readOnly = true, label = { Text("Active profile") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = profileExpanded, onDismissRequest = { profileExpanded = false }) {
                    profiles.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.updateSettings { it.copy(activeProfileId = p.id) }; profileExpanded = false }) }
                    DropdownMenuItem(text = { Text("Create custom profile") }, onClick = { profileExpanded = false; showProfileCreate = true })
                }
            }
        }
        item {
            SectionTitle("Vision")
            SwitchRow("Auto Analyze", settings.vision.autoAnalyze) { checked -> vm.updateSettings { current -> current.copy(vision = current.vision.copy(autoAnalyze = checked)) } }
            SwitchRow("Freeze Frame", settings.vision.freezeFrame) { checked -> vm.updateSettings { current -> current.copy(vision = current.vision.copy(freezeFrame = checked)) } }
            SwitchRow("Screenshot Preview", settings.vision.screenshotPreview) { checked -> vm.updateSettings { current -> current.copy(vision = current.vision.copy(screenshotPreview = checked)) } }
            SwitchRow("Save Screenshots", settings.vision.saveScreenshots) { checked -> vm.updateSettings { current -> current.copy(vision = current.vision.copy(saveScreenshots = checked)) } }
            SwitchRow("Send Only Selected Region", settings.vision.sendOnlyRegion) { checked -> vm.updateSettings { current -> current.copy(vision = current.vision.copy(sendOnlyRegion = checked)) } }
        }
        item {
            SectionTitle("Floating")
            SliderRow("Opacity", settings.floating.opacity, 0.55f..1f) { v -> vm.updateSettings { it.copy(floating = it.floating.copy(opacity = v)) } }
            SwitchRow("Snap to edge", settings.floating.snapToEdge) { checked -> vm.updateSettings { current -> current.copy(floating = current.floating.copy(snapToEdge = checked)) } }
            SwitchRow("Lock position", settings.floating.locked) { checked -> vm.updateSettings { current -> current.copy(floating = current.floating.copy(locked = checked)) } }
            SwitchRow("Compact mode", settings.floating.compactMode) { checked -> vm.updateSettings { current -> current.copy(floating = current.floating.copy(compactMode = checked)) } }
            SwitchRow("Auto hide", settings.floating.autoHide) { checked -> vm.updateSettings { current -> current.copy(floating = current.floating.copy(autoHide = checked)) } }
        }
        item {
            SectionTitle("Appearance")
            EnumDropdown("Theme", settings.theme.label, ThemeMode.entries) { selected -> vm.updateSettings { it.copy(theme = selected) } }
            AccentPicker(settings.accent) { accent -> vm.updateSettings { current -> current.copy(accent = accent) } }
        }
        item {
            SectionTitle("Privacy")
            SwitchRow("Save Conversations", settings.saveHistory) { checked -> vm.updateSettings { current -> current.copy(saveHistory = checked) } }
            Text("Auto Delete")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1, 7, 30).forEach { days ->
                    FilterChip(selected = settings.autoDeleteDays == days, onClick = { vm.updateSettings { current -> current.copy(autoDeleteDays = days) }; vm.cleanupHistory(days) }, label = { Text(if (days == 0) "Never" else "${days} day${if (days == 1) "" else "s"}") })
                }
            }
            Button(onClick = { vm.clearHistory() }) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("Clear History") }
            OutlinedButton(onClick = vm::clearScreenshots) { Text("Clear Screenshots") }
        }
        item {
            SectionTitle("Permissions")
            OutlinedButton(onClick = onOverlay, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Layers, null); Spacer(Modifier.width(6.dp)); Text("Overlay Permission") }
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }, modifier = Modifier.fillMaxWidth()) { Text("Notification settings") }
        }
        item {
            SectionTitle("Advanced")
            SwitchRow("Debug logging", settings.debugLogging) { checked -> vm.updateSettings { current -> current.copy(debugLogging = checked) } }
            Text("Shizuku: ${if (vm.shizuku.available.collectAsState().value) if (vm.shizuku.permissionGranted.collectAsState().value) "Connected / Permission granted" else "Connected / Permission not granted" else "Not available"}", fontSize = 13.sp)
            Text("Shizuku is optional and does not replace MediaProjection or bypass Android security.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        item {
            SectionTitle("About")
            Text("ALF Vision Panel 1.0.0", style = MaterialTheme.typography.titleMedium)
            Text("Native Kotlin + Jetpack Compose + Material 3. Groq API calls are sent directly from the device over HTTPS.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showProfileCreate) ProfileDialog(vm, onDismiss = { showProfileCreate = false })
}

@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleLarge) }
@Composable private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChange) } }
@Composable private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) { Column { Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Text("${"%.2f".format(value)}") }; Slider(value, onChange, valueRange = range) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun <T : Enum<T>> EnumDropdown(label: String, selected: String, entries: List<T>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(selected, {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded, { expanded = false }) { entries.forEach { e -> DropdownMenuItem(text = { Text((e as? ResponseStyle)?.label ?: (e as? ThemeMode)?.label ?: e.name) }, onClick = { onSelect(e); expanded = false }) } }
    }
}

@Composable private fun AccentPicker(accent: Accent, onChange: (Accent) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Accent.entries.forEach { item -> FilterChip(selected = item == accent, onClick = { onChange(item) }, label = { Text(item.label) }) }
    }
}

@Composable private fun ProfileDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("You are a helpful ALF Vision assistant.") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create profile") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Name") }); OutlinedTextField(prompt, { prompt = it }, label = { Text("System prompt") }, minLines = 3) } },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) vm.createProfile(name.trim(), prompt.trim()); onDismiss() }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
