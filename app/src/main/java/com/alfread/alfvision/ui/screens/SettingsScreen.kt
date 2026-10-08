package com.alfread.alfvision.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.alfread.alfvision.BuildConfig
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.components.*
import com.alfread.alfvision.ui.theme.color
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, padding: PaddingValues, onOverlay: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val apiReady by vm.apiReady.collectAsState()
    val status by vm.groqStatus.collectAsState()
    val models by vm.models.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val shizukuAvailable by vm.shizuku.available.collectAsState()
    val shizukuGranted by vm.shizuku.permissionGranted.collectAsState()
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var profileExpanded by remember { mutableStateOf(false) }
    var showProfileCreate by remember { mutableStateOf(false) }
    var overlayReady by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { overlayReady = Settings.canDrawOverlays(context) }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader("Settings", "Atur AI, panel, tampilan, dan privasi") }

        // ------------------------------------------------------------ AI
        item {
            SettingsCard("AI", Icons.Default.AutoAwesome) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Groq API Key", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (apiReady) "Tersimpan" else "Belum diatur",
                        color = if (apiReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (apiReady) "Ganti API key" else "Masukkan API key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show or hide key")
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { vm.saveApiKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save") }
                    OutlinedButton(onClick = vm::testConnection) { Text("Test") }
                    TextButton(onClick = vm::deleteApiKey) { Text("Delete") }
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }

                val visionModels = models.filter { it.supportsVision }.ifEmpty { models }
                ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = !modelExpanded }) {
                    OutlinedTextField(
                        value = settings.activeModel, onValueChange = {}, readOnly = true, label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        if (visionModels.isEmpty()) {
                            DropdownMenuItem(text = { Text("Tekan Test untuk memuat model") }, onClick = { modelExpanded = false })
                        }
                        visionModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.id) },
                                onClick = { vm.updateSettings { it.copy(activeModel = model.id) }; modelExpanded = false }
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsCard("Response", Icons.Default.Tune) {
                EnumDropdown("Style", settings.responseStyle.label, ResponseStyle.entries) { s -> vm.updateSettings { it.copy(responseStyle = s) } }
                SliderRow("Temperature", settings.temperature, 0f..2f) { v -> vm.updateSettings { it.copy(temperature = v) } }
                SliderRow("Max tokens", settings.maxTokens.toFloat(), 128f..16384f, format = { it.roundToInt().toString() }) { v ->
                    vm.updateSettings { it.copy(maxTokens = v.roundToInt()) }
                }
                val selected = profiles.firstOrNull { it.id == settings.activeProfileId }?.name ?: profiles.firstOrNull()?.name ?: "General"
                ExposedDropdownMenuBox(expanded = profileExpanded, onExpandedChange = { profileExpanded = !profileExpanded }) {
                    OutlinedTextField(
                        value = selected, onValueChange = {}, readOnly = true, label = { Text("Active profile") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(profileExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = profileExpanded, onDismissRequest = { profileExpanded = false }) {
                        profiles.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.updateSettings { it.copy(activeProfileId = p.id) }; profileExpanded = false })
                        }
                        DropdownMenuItem(text = { Text("Create custom profile") }, onClick = { profileExpanded = false; showProfileCreate = true })
                    }
                }
            }
        }

        // ------------------------------------------------------------ VISION & PANEL
        item {
            SettingsCard("Vision", Icons.Default.Visibility) {
                SwitchRow("Auto Analyze", settings.vision.autoAnalyze, supporting = "Capture + analyze otomatis sesuai interval") { c ->
                    vm.updateSettings { it.copy(vision = it.vision.copy(autoAnalyze = c)) }
                }
                if (settings.vision.autoAnalyze) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AutoAnalyzeInterval.entries.forEach { interval ->
                            FilterChip(
                                selected = settings.vision.interval == interval,
                                onClick = { vm.updateSettings { it.copy(vision = it.vision.copy(interval = interval)) } },
                                label = { Text(interval.label) }
                            )
                        }
                    }
                }
                SwitchRow("Freeze Frame", settings.vision.freezeFrame) { c -> vm.updateSettings { it.copy(vision = it.vision.copy(freezeFrame = c)) } }
                SwitchRow("Screenshot Preview", settings.vision.screenshotPreview) { c -> vm.updateSettings { it.copy(vision = it.vision.copy(screenshotPreview = c)) } }
                SwitchRow("Save Screenshots", settings.vision.saveScreenshots) { c -> vm.updateSettings { it.copy(vision = it.vision.copy(saveScreenshots = c)) } }
                SwitchRow("Send only selected region", settings.vision.sendOnlyRegion) { c -> vm.updateSettings { it.copy(vision = it.vision.copy(sendOnlyRegion = c)) } }
                SliderRow("JPEG quality", settings.vision.quality.toFloat(), 40f..100f, format = { "${it.roundToInt()}%" }) { v ->
                    vm.updateSettings { it.copy(vision = it.vision.copy(quality = v.roundToInt())) }
                }
            }
        }

        item {
            SettingsCard("Floating panel", Icons.Default.Layers) {
                SliderRow("Opacity", settings.floating.opacity, 0.55f..1f, format = { "${(it * 100).roundToInt()}%" }) { v ->
                    vm.updateSettings { it.copy(floating = it.floating.copy(opacity = v)) }
                }
                SliderRow("Orb size", settings.floating.orbSizeDp.toFloat(), 44f..96f, format = { "${it.roundToInt()} dp" }) { v ->
                    vm.updateSettings { it.copy(floating = it.floating.copy(orbSizeDp = v.roundToInt())) }
                }
                SliderRow("Panel width", settings.floating.panelWidthDp.toFloat(), 280f..520f, format = { "${it.roundToInt()} dp" }) { v ->
                    vm.updateSettings { it.copy(floating = it.floating.copy(panelWidthDp = v.roundToInt())) }
                }
                SliderRow("Panel height", settings.floating.panelHeightDp.toFloat(), 300f..820f, format = { "${it.roundToInt()} dp" }) { v ->
                    vm.updateSettings { it.copy(floating = it.floating.copy(panelHeightDp = v.roundToInt())) }
                }
                Text("Ukuran berlaku saat panel dibuka ulang.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow("Snap to edge", settings.floating.snapToEdge) { c -> vm.updateSettings { it.copy(floating = it.floating.copy(snapToEdge = c)) } }
                SwitchRow("Lock position", settings.floating.locked) { c -> vm.updateSettings { it.copy(floating = it.floating.copy(locked = c)) } }
                SwitchRow("Compact mode", settings.floating.compactMode) { c -> vm.updateSettings { it.copy(floating = it.floating.copy(compactMode = c)) } }
                SwitchRow("Auto hide", settings.floating.autoHide) { c -> vm.updateSettings { it.copy(floating = it.floating.copy(autoHide = c)) } }
            }
        }

        // ------------------------------------------------------------ APPEARANCE
        item {
            SettingsCard("Appearance", Icons.Default.Palette) {
                Text("Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.theme == mode,
                            onClick = { vm.updateSettings { it.copy(theme = mode) } },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Text("Accent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Accent.entries.forEach { item ->
                        val selected = item == settings.accent
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(item.color())
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { vm.updateSettings { it.copy(accent = item) } },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Icon(Icons.Default.Check, item.label, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------ PRIVACY & PERMISSIONS
        item {
            SettingsCard("Privacy", Icons.Default.Security) {
                SwitchRow("Save conversations", settings.saveHistory) { c -> vm.updateSettings { it.copy(saveHistory = c) } }
                Text("Auto delete", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1, 7, 30).forEach { days ->
                        FilterChip(
                            selected = settings.autoDeleteDays == days,
                            onClick = { vm.updateSettings { it.copy(autoDeleteDays = days) }; vm.cleanupHistory(days) },
                            label = { Text(if (days == 0) "Never" else "$days day${if (days == 1) "" else "s"}") }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.clearHistory() }) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear history")
                    }
                    OutlinedButton(onClick = vm::clearScreenshots) { Text("Clear screenshots") }
                }
            }
        }

        item {
            SettingsCard("Permissions", Icons.Default.Lock) {
                PermissionRow("Overlay", overlayReady) { onOverlay() }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.Notifications, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Notification settings") }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.Mic, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("App permissions (mikrofon)") }
            }
        }

        item {
            SettingsCard("Advanced", Icons.Default.Settings) {
                SwitchRow("Debug logging", settings.debugLogging) { c -> vm.updateSettings { it.copy(debugLogging = c) } }
                Text(
                    "Shizuku: " + when {
                        !shizukuAvailable -> "Not available"
                        shizukuGranted -> "Connected / permission granted"
                        else -> "Connected / permission not granted"
                    },
                    fontSize = 13.sp
                )
                Text(
                    "Shizuku bersifat opsional dan tidak menggantikan MediaProjection atau melewati keamanan Android.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
            }
        }

        item {
            SettingsCard("About", Icons.Default.Info) {
                Text("ALF Vision Panel ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Native Kotlin + Jetpack Compose + Material 3. Request Groq dikirim langsung dari perangkat lewat HTTPS.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                )
            }
        }
    }
    if (showProfileCreate) ProfileDialog(vm, onDismiss = { showProfileCreate = false })
}

@Composable
private fun SettingsCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    AlfCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, MaterialTheme.colorScheme.primary, size = 34)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Warning, null,
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text("$label permission", Modifier.weight(1f))
        if (!granted) TextButton(onClick = onFix) { Text("Aktifkan") } else Text("Aktif", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumDropdown(label: String, selected: String, entries: List<T>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(
            selected, {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            entries.forEach { e ->
                DropdownMenuItem(
                    text = { Text((e as? ResponseStyle)?.label ?: (e as? ThemeMode)?.label ?: e.name) },
                    onClick = { onSelect(e); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ProfileDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("You are a helpful ALF Vision assistant.") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                OutlinedTextField(prompt, { prompt = it }, label = { Text("System prompt") }, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) vm.createProfile(name.trim(), prompt.trim()); onDismiss() }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
