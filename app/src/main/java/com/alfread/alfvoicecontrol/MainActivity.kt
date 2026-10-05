package com.alfread.alfvoicecontrol

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alfread.alfvoicecontrol.admin.AlfDeviceAdminReceiver
import com.alfread.alfvoicecontrol.commands.CommandExecutor
import com.alfread.alfvoicecontrol.commands.CommandMatcher
import com.alfread.alfvoicecontrol.data.AppRepository
import com.alfread.alfvoicecontrol.data.LocalStore
import com.alfread.alfvoicecontrol.model.ActionType
import com.alfread.alfvoicecontrol.model.AppSettings
import com.alfread.alfvoicecontrol.model.AppTarget
import com.alfread.alfvoicecontrol.model.VoiceCommand
import com.alfread.alfvoicecontrol.screen.ScreenWakeManager
import com.alfread.alfvoicecontrol.security.AppPinManager
import com.alfread.alfvoicecontrol.service.VoiceMonitoringService
import com.alfread.alfvoicecontrol.voice.VoiceRecorder
import com.alfread.alfvoicecontrol.voice.VoiceRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var store: LocalStore
    private lateinit var pinManager: AppPinManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LocalStore(applicationContext)
        pinManager = AppPinManager(store)
        lifecycleScope.launch(Dispatchers.IO) { store.seedDefaults() }
        setContent {
            ALFVoiceTheme {
                ALFVoiceControlApp(
                    store = store,
                    pinManager = pinManager,
                    activity = this@MainActivity
                )
            }
        }
    }

}

private enum class Screen { HOME, SETUP, COMMANDS, EDITOR, RECORDING, SETTINGS, BACKGROUND, TEST }

@Composable
private fun ALFVoiceTheme(content: @Composable () -> Unit) {
    val darkColors = androidx.compose.material3.darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF8B70FF),
        secondary = androidx.compose.ui.graphics.Color(0xFF42A5FF),
        tertiary = androidx.compose.ui.graphics.Color(0xFFB084FF),
        background = androidx.compose.ui.graphics.Color(0xFF090A0F),
        surface = androidx.compose.ui.graphics.Color(0xFF11131A),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1A1D27),
        onBackground = androidx.compose.ui.graphics.Color(0xFFF4F5F7),
        onSurface = androidx.compose.ui.graphics.Color(0xFFF4F5F7)
    )
    MaterialTheme(colorScheme = darkColors, content = content)
}

@Composable
private fun ALFVoiceControlApp(
    store: LocalStore,
    pinManager: AppPinManager,
    activity: MainActivity
) {
    val settings by store.settingsFlow.collectAsState(initial = AppSettings())
    val commands by store.commandsFlow.collectAsState(initial = emptyList())
    var screen by rememberSaveable { mutableStateOf(if (settings.firstRunCompleted) Screen.HOME.name else Screen.SETUP.name) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var hasPin by remember { mutableStateOf(false) }
    var pinGateVisible by remember { mutableStateOf(false) }
    var pinGateError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val notificationLauncher = rememberLauncherForActivityResult(RequestPermission()) { startListening(activity) }
    val micLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startListening(activity)
            }
        }
    }

    fun requestAndStartListening() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startListening(activity)
    }

    LaunchedEffect(screen) { hasPin = pinManager.hasPin() }
    fun navigate(next: Screen) { screen = next.name }

    if (pinGateVisible) {
        PinVerifyDialog(
            onDismiss = { pinGateVisible = false },
            onVerify = { pin ->
                scope.launch {
                    if (pinManager.verify(pin)) {
                        pinGateVisible = false
                        navigate(Screen.SETTINGS)
                    } else {
                        pinGateError = "Incorrect ALF PIN."
                    }
                }
            },
            error = pinGateError
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (Screen.valueOf(screen)) {
            Screen.SETUP -> SetupScreen(
                settings = settings,
                onFinish = {
                    scope.launch {
                        store.updateSettings { it.copy(firstRunCompleted = true) }
                    }
                    navigate(Screen.HOME)
                }
            )
            Screen.HOME -> HomeScreen(
                settings = settings,
                commands = commands,
                onStart = { requestAndStartListening() },
                onStop = { activity.stopService(Intent(activity, VoiceMonitoringService::class.java)) },
                onCommands = { navigate(Screen.COMMANDS) },
                onSettings = {
                    if (hasPin) { pinGateVisible = true; pinGateError = "" } else navigate(Screen.SETTINGS)
                }
            )
            Screen.COMMANDS -> CommandsScreen(
                commands = commands,
                onBack = { navigate(Screen.HOME) },
                onAdd = { editingId = null; navigate(Screen.EDITOR) },
                onEdit = { editingId = it.id; navigate(Screen.EDITOR) },
                onDelete = { command ->
                    command.audioFile?.let { VoiceRecorder(activity).delete(it) }
                    scope.launch { store.deleteCommand(command.id) }
                },
                onToggle = { command -> scope.launch { store.upsertCommand(command.copy(enabled = !command.enabled)) } },
                onTest = { editingId = it.id; navigate(Screen.TEST) }
            )
            Screen.EDITOR -> {
                val current = commands.firstOrNull { it.id == editingId }
                CommandEditorScreen(
                    initial = current,
                    onBack = { navigate(Screen.COMMANDS) },
                    onSave = { command ->
                        scope.launch { store.upsertCommand(command) }
                        navigate(Screen.COMMANDS)
                    },
                    onRecord = { commandId ->
                        editingId = commandId
                        navigate(Screen.RECORDING)
                    }
                )
            }
            Screen.RECORDING -> {
                val current = commands.firstOrNull { it.id == editingId }
                if (current == null) {
                    navigate(Screen.COMMANDS)
                } else {
                    RecordingScreen(
                        command = current,
                        onCancel = { navigate(Screen.EDITOR) },
                        onSave = { path ->
                            scope.launch { store.upsertCommand(current.copy(audioFile = path)) }
                            navigate(Screen.EDITOR)
                        }
                    )
                }
            }
            Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                hasPin = hasPin,
                deviceAdmin = ScreenWakeManager(activity).isDeviceAdminEnabled(),
                onBack = { navigate(Screen.HOME) },
                onSettingsUpdate = { transform -> scope.launch { store.updateSettings(transform) } },
                onListeningToggle = { enabled -> if (enabled) requestAndStartListening() else activity.stopService(Intent(activity, VoiceMonitoringService::class.java)) },
                onBackground = { navigate(Screen.BACKGROUND) },
                onAdmin = { requestDeviceAdmin(activity) },
                onPin = { showPinDialog(activity, pinManager) },
            )
            Screen.BACKGROUND -> BackgroundOperationScreen(
                onBack = { navigate(Screen.SETTINGS) },
                onBattery = { openBatterySettings(activity) }
            )
            Screen.TEST -> {
                val current = commands.firstOrNull { it.id == editingId }
                if (current == null) navigate(Screen.COMMANDS)
                else TestCommandScreen(command = current, settings = settings, onBack = { navigate(Screen.COMMANDS) }, activity = activity)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

@Composable
private fun SetupScreen(settings: AppSettings, onFinish: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var notificationGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val micLauncher = rememberLauncherForActivityResult(RequestPermission()) { micGranted = it }
    val notificationLauncher = rememberLauncherForActivityResult(RequestPermission()) { notificationGranted = it }

    LaunchedEffect(Unit) {
        // Actual permission state is checked here rather than assuming first-run defaults.
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to ALF Voice Control", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Control your phone with your voice while keeping commands and recordings local to this app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        SetupStep("1", "Microphone", "Required for recording and voice recognition", micGranted || false) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            SetupStep("2", "Notifications", "Required for visible foreground listening status", notificationGranted) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        SetupStep("3", "Wake word", "Default: \"Alf\". Change it later in Settings.", true) {}
        SetupStep("4", "ALF PIN", "Optional app-only PIN for protecting settings.", false) {}
        SetupStep("5", "Background operation", "Battery optimization can affect OEM background behavior.", false) {}
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            enabled = micGranted
        ) {
            Text("GET STARTED")
        }
        Spacer(Modifier.height(8.dp))
        Text("Android security stays in control. ALF never replaces or bypasses the device lock screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SetupStep(number: String, title: String, subtitle: String, complete: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Text(number, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (complete) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            else TextButton(onClick = onClick) { Text("SET") }
        }
    }
}

@Composable
private fun HomeScreen(
    settings: AppSettings,
    commands: List<VoiceCommand>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCommands: () -> Unit,
    onSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ALF VOICE CONTROL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Voice assistant", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null) }
        }
        Spacer(Modifier.height(18.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))), contentAlignment = Alignment.Center) {
                        Text("ALF", fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Listening", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (settings.listeningEnabled) "ACTIVE" else "OFF", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Wake Word", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("\"${settings.wakeWord}\"", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(18.dp))
                if (settings.listeningEnabled) {
                    OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("STOP LISTENING")
                    }
                } else {
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Mic, null)
                        Spacer(Modifier.width(8.dp))
                        Text("START LISTENING")
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionTitle("VOICE COMMANDS")
        commands.take(4).forEach { CommandSummary(it) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCommands, modifier = Modifier.fillMaxWidth()) { Text("MANAGE COMMANDS") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun CommandSummary(command: VoiceCommand) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(command.name, fontWeight = FontWeight.SemiBold)
                Text("\"${command.triggerPhrase}\"", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (command.enabled) "ACTIVE" else "OFF", style = MaterialTheme.typography.labelSmall, color = if (command.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CommandsScreen(
    commands: List<VoiceCommand>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (VoiceCommand) -> Unit,
    onDelete: (VoiceCommand) -> Unit,
    onToggle: (VoiceCommand) -> Unit,
    onTest: (VoiceCommand) -> Unit
) {
    AppScaffold("Voice Commands", onBack) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onAdd) { Text("ADD COMMAND") }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(commands, key = { it.id }) { command ->
                    CommandCard(command, onEdit, onDelete, onToggle, onTest)
                }
            }
        }
    }
}

@Composable
private fun CommandCard(
    command: VoiceCommand,
    onEdit: (VoiceCommand) -> Unit,
    onDelete: (VoiceCommand) -> Unit,
    onToggle: (VoiceCommand) -> Unit,
    onTest: (VoiceCommand) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(command.name, fontWeight = FontWeight.Bold)
                    Text(command.actionType.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Switch(checked = command.enabled, onCheckedChange = { onToggle(command) })
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Test") }, onClick = { menuOpen = false; onTest(command) })
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit(command) })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete(command) })
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text("\"${command.triggerPhrase}\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (command.audioFile != null) Text("Voice sample saved locally", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Divider(Modifier.padding(vertical = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onEdit(command) }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("EDIT") }
                Button(onClick = { onTest(command) }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text("TEST") }
            }
        }
    }
}

@Composable
private fun CommandEditorScreen(
    initial: VoiceCommand?,
    onBack: () -> Unit,
    onSave: (VoiceCommand) -> Unit,
    onRecord: (String) -> Unit
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var phrase by rememberSaveable(initial?.id) { mutableStateOf(initial?.triggerPhrase ?: "") }
    var action by rememberSaveable(initial?.id) { mutableStateOf(initial?.actionType?.name ?: ActionType.SCREEN_ON.name) }
    var targetPackage by rememberSaveable(initial?.id) { mutableStateOf(initial?.targetPackage) }
    var targetAppName by rememberSaveable(initial?.id) { mutableStateOf(initial?.targetAppName) }
    var audioFile by rememberSaveable(initial?.id) { mutableStateOf(initial?.audioFile) }
    var appPicker by remember { mutableStateOf(false) }
    var actionsOpen by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val apps = remember { AppRepository(context).getLaunchableApps() }
    val id = initial?.id ?: remember { java.util.UUID.randomUUID().toString() }

    AppScaffold(if (initial == null) "Add Command" else "Edit Command", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Command name") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            Box {
                OutlinedButton(onClick = { actionsOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Action: ${ActionType.from(action).name.replace('_', ' ')}")
                }
                DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                    ActionType.entries.forEach { type -> DropdownMenuItem(text = { Text(type.name.replace('_', ' ')) }, onClick = { action = type.name; actionsOpen = false }) }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (ActionType.from(action) == ActionType.OPEN_APP) {
                OutlinedButton(onClick = { appPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(targetAppName ?: "Choose installed application")
                }
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(phrase, { phrase = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Trigger phrase") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Voice sample", fontWeight = FontWeight.SemiBold)
                    Text("The recording stays inside the app. Speech-to-text remains the primary command matching method.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRecord(id) }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text(if (audioFile == null) "RECORD" else "RE-RECORD") }
                        if (audioFile != null) {
                            OutlinedButton(onClick = { VoiceRecorder(context).play(audioFile!!) }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("PLAY") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSave(
                        VoiceCommand(
                            id = id,
                            name = name.trim().ifBlank { "Voice Command" },
                            triggerPhrase = phrase.trim(),
                            actionType = ActionType.from(action),
                            targetPackage = targetPackage,
                            targetAppName = targetAppName,
                            audioFile = audioFile,
                            enabled = initial?.enabled ?: true,
                            createdAt = initial?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                enabled = phrase.isNotBlank() && (ActionType.from(action) != ActionType.OPEN_APP || targetPackage != null),
                modifier = Modifier.fillMaxWidth()
            ) { Text("SAVE COMMAND") }
        }
    }

    if (appPicker) {
        AppPickerDialog(apps, onDismiss = { appPicker = false }) { target ->
            targetPackage = target.packageName
            targetAppName = target.label
            if (name.isBlank()) name = "Open ${target.label}"
            if (phrase.isBlank()) phrase = "Buka ${target.label}"
            appPicker = false
        }
    }
}

@Composable
private fun AppPickerDialog(apps: List<AppTarget>, onDismiss: () -> Unit, onPick: (AppTarget) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) { apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }.take(80) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose installed app") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search apps") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(420.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Text(
                            app.label,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 13.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}

@Composable
private fun RecordingScreen(command: VoiceCommand, onCancel: () -> Unit, onSave: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var stage by remember { mutableStateOf("GET READY") }
    var countdown by remember { mutableStateOf(3) }
    var recording by remember { mutableStateOf(false) }
    var filePath by remember { mutableStateOf<String?>(command.audioFile) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val transition = rememberInfiniteTransition(label = "wave")
    val wave by transition.animateFloat(0.5f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "wave")

    LaunchedEffect(Unit) {
        for (n in 3 downTo 1) {
            countdown = n
            delay(1000L)
        }
        stage = "SPEAK NOW"
        val file = runCatching { recorder.start(command.id) }.getOrNull()
        if (file == null) {
            stage = "RECORDING FAILED"
            return@LaunchedEffect
        }
        recording = true
        val start = System.currentTimeMillis()
        while (recording && System.currentTimeMillis() - start < 6000L) {
            elapsed = System.currentTimeMillis() - start
            delay(100L)
        }
        if (recording) {
            filePath = recorder.stop()?.absolutePath
            recording = false
            stage = "RECORDING COMPLETE"
        }
    }

    val safeCancel: () -> Unit = {
        if (filePath != command.audioFile) recorder.delete(filePath)
        onCancel()
    }

    AppScaffold("Voice Recording", safeCancel) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Mic, null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(18.dp))
            Text(command.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("\"${command.triggerPhrase}\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(30.dp))
            if (stage == "GET READY") Text("$countdown", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
            else Text(stage, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                repeat(18) { index ->
                    val height = (28 + ((index % 5) * 9) * wave).dp
                    Box(Modifier.padding(horizontal = 2.dp).width(5.dp).height(height).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = if (recording) 0.85f else 0.25f)))
                }
            }
            if (recording) {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = {
                    filePath = recorder.stop()?.absolutePath
                    recording = false
                    stage = "RECORDING COMPLETE"
                }) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("STOP") }
            } else if (stage == "RECORDING COMPLETE" && filePath != null) {
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { VoiceRecorder(context).play(filePath!!) }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("PLAY") }
                    OutlinedButton(onClick = safeCancel) { Text("RE-RECORD") }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onSave(filePath!!) }, modifier = Modifier.fillMaxWidth()) { Text("SAVE") }
            }
            if (elapsed > 0L) Text("${elapsed / 1000.0} s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TestCommandScreen(command: VoiceCommand, settings: AppSettings, onBack: () -> Unit, activity: MainActivity) {
    var detected by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }
    val recognizer = remember { VoiceRecognizer(activity) }
    val executor = remember { CommandExecutor(activity) }

    fun startTest() {
        error = null
        detected = ""
        score = 0f
        listening = true
        recognizer.recognizeOnce(
            preferOnDevice = true,
            onPartial = { detected = it },
            onResult = { text, _ ->
                detected = text
                score = CommandMatcher.match(text, listOf(command), settings.confidenceThreshold)?.score ?: 0f
                listening = false
            },
            onError = { _, message -> error = message; listening = false }
        )
    }

    AppScaffold("Test Voice Command", onBack) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp)) {
                    Text(command.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Expected: \"${command.triggerPhrase}\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Text("Detected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(if (detected.isBlank()) "Waiting for speech..." else "\"$detected\"")
                    Spacer(Modifier.height(10.dp))
                    Text("Match score: ${(score * 100).toInt()}%")
                    LinearProgressIndicator(progress = score, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (score >= settings.confidenceThreshold && !listening) {
                OutlinedButton(
                    onClick = {
                        executor.execute(command, settings.screenWakeEnabled, settings.screenOffEnabled)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("RUN ACTION") }
            }
            Button(onClick = { if (listening) recognizer.stop() else startTest() }, modifier = Modifier.fillMaxWidth()) {
                Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, null)
                Spacer(Modifier.width(8.dp))
                Text(if (listening) "STOP" else "LISTEN ONCE")
            }
            Text("The score is ALF's local phrase-match score, not biometric speaker verification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    hasPin: Boolean,
    deviceAdmin: Boolean,
    onBack: () -> Unit,
    onSettingsUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onListeningToggle: (Boolean) -> Unit,
    onBackground: () -> Unit,
    onAdmin: () -> Unit,
    onPin: () -> Unit
) {
    AppScaffold("Settings", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            var wakeDialog by remember { mutableStateOf(false) }
            SettingSwitch("Voice Control", settings.listeningEnabled, onListeningToggle)
            SettingRow("Wake Word", "\"${settings.wakeWord}\"", onClick = { wakeDialog = true })
            if (wakeDialog) {
                WakeWordDialog(settings.wakeWord, settings.wakeWordAudioFile, onDismiss = { wakeDialog = false }) { value, audioFile ->
                    onSettingsUpdate { it.copy(wakeWord = value, wakeWordAudioFile = audioFile) }
                    wakeDialog = false
                }
            }
            SettingSwitch("Screen Wake", settings.screenWakeEnabled) { value -> onSettingsUpdate { s -> s.copy(screenWakeEnabled = value) } }
            SettingSwitch("Screen Off Command", settings.screenOffEnabled) { value -> onSettingsUpdate { s -> s.copy(screenOffEnabled = value) } }
            SettingRow("Foreground notification", "Required while microphone monitoring is active")
            Spacer(Modifier.height(8.dp))
            Text("Matching threshold: ${(settings.confidenceThreshold * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            Slider(value = settings.confidenceThreshold, onValueChange = { onSettingsUpdate { s -> s.copy(confidenceThreshold = it) } }, valueRange = 0.50f..0.95f)
            Divider(Modifier.padding(vertical = 8.dp))
            Text("SECURITY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            SettingRow("ALF PIN", if (hasPin) "Configured" else "Not configured", Icons.Default.Lock, onPin)
            SettingRow("Device Administrator", if (deviceAdmin) "Enabled" else "Not enabled", Icons.Default.Shield, onAdmin)
            Divider(Modifier.padding(vertical = 8.dp))
            Text("BACKGROUND OPERATION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            SettingRow("Battery Optimization", "Open Android battery settings", Icons.Default.BatterySaver, onBackground)
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Android limitations", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("ALF can wake the screen but cannot bypass a PIN, pattern, or password. Background app launch can also be blocked by Android/OEM policy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("ALF Voice Control 1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun WakeWordDialog(current: String, currentAudioFile: String?, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context) }
    var value by remember { mutableStateOf(current) }
    var audioFile by remember { mutableStateOf(currentAudioFile) }
    var recording by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!recording) onDismiss() },
        title = { Text("Wake Word") },
        text = {
            Column {
                OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Wake phrase") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                Text("Record a local reference sample. This sample is not biometric speaker verification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!recording) {
                            val file = runCatching { recorder.start("wake-word") }.getOrNull()
                            if (file == null) {
                                message = "Could not start recording."
                            } else {
                                recording = true
                                message = "SPEAK NOW"
                                scope.launch {
                                    delay(4000L)
                                    if (recording) {
                                        audioFile = recorder.stop()?.absolutePath
                                        recording = false
                                        message = "Recording complete."
                                    }
                                }
                            }
                        } else {
                            audioFile = recorder.stop()?.absolutePath
                            recording = false
                            message = "Recording complete."
                        }
                    }) {
                        Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (recording) "STOP" else "RECORD WAKE WORD")
                    }
                    if (audioFile != null && !recording) {
                        OutlinedButton(onClick = { VoiceRecorder(context).play(audioFile!!) }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("PLAY") }
                    }
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(message, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { TextButton(enabled = !recording, onClick = { if (value.trim().isNotBlank()) onSave(value.trim(), audioFile) }) { Text("SAVE") } },
        dismissButton = { TextButton(enabled = !recording, onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BackgroundOperationScreen(onBack: () -> Unit, onBattery: () -> Unit) {
    AppScaffold("Background Operation", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Keep listening reliable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Some Android OEMs, including aggressive battery managers, can stop foreground/background work sooner than stock Android. ALF cannot bypass those policies with root, exploits, or hidden APIs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBattery, modifier = Modifier.fillMaxWidth()) { Text("OPEN BATTERY SETTINGS") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Recommended setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Keep ALF out of battery optimization when your phone offers that option, allow notifications, and start listening while ALF is visible. Android 14+ restricts microphone foreground-service creation from the background.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun startListening(activity: MainActivity) {
    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
    val intent = Intent(activity, VoiceMonitoringService::class.java).setAction(VoiceMonitoringService.ACTION_START)
    runCatching { ContextCompat.startForegroundService(activity, intent) }
}

private fun requestDeviceAdmin(activity: MainActivity) {
    val admin = ComponentName(activity, AlfDeviceAdminReceiver::class.java)
    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "ALF needs Device Administrator only for the optional Screen Off voice command.")
    }
    activity.startActivity(intent)
}

private fun openBatterySettings(activity: MainActivity) {
    runCatching {
        activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }.onFailure {
        activity.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun showPinDialog(activity: MainActivity, pinManager: AppPinManager) {
    activity.startActivity(Intent(activity, PinActivity::class.java))
}

@Composable
private fun PinVerifyDialog(onDismiss: () -> Unit, onVerify: (String) -> Unit, error: String) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter ALF PIN") },
        text = {
            Column {
                Text("This PIN protects ALF settings only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ALF PIN") },
                    singleLine = true
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onVerify(pin) }) { Text("UNLOCK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

class PinActivity : ComponentActivity() {
    private lateinit var manager: AppPinManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalStore(applicationContext)
        manager = AppPinManager(store)
        setContent {
            ALFVoiceTheme {
                var pin by remember { mutableStateOf("") }
                var confirm by remember { mutableStateOf("") }
                var message by remember { mutableStateOf("") }
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("ALF PIN", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("This PIN protects ALF settings only. It does not replace Android's lock screen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, modifier = Modifier.fillMaxWidth(), label = { Text("PIN") }, singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(confirm, { if (it.length <= 6 && it.all(Char::isDigit)) confirm = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm PIN") }, singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        if (pin.length in 4..6 && pin == confirm) {
                            lifecycleScope.launch { manager.setPin(pin); finish() }
                        } else message = "PIN must match and contain 4 to 6 digits."
                    }, modifier = Modifier.fillMaxWidth()) { Text("SAVE PIN") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { lifecycleScope.launch { manager.clearPin(); finish() } }, modifier = Modifier.fillMaxWidth()) { Text("REMOVE PIN") }
                }
            }
        }
    }
}

