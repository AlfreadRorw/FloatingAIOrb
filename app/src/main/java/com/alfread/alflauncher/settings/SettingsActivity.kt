package com.alfread.alflauncher.settings

import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alfread.alflauncher.data.LauncherSettings
import com.alfread.alflauncher.launcher.LauncherViewModel
import com.alfread.alflauncher.ui.AlfTheme

class SettingsActivity : ComponentActivity() {
    private val vm by viewModels<LauncherViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsState()
            AlfTheme(dark = true) {
                SettingsScreen(
                    settings = settings,
                    onSave = vm::saveSettings,
                    onDefault = {
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                        } else {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    onWallpaper = {
                        startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: LauncherSettings,
    onSave: (LauncherSettings) -> Unit,
    onDefault: () -> Unit,
    onWallpaper: () -> Unit
) {
    var local by remember(settings) { mutableStateOf(settings) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ALF Launcher", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text("Home Screen")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Show app labels")
            Switch(local.labels, onCheckedChange = { local = local.copy(labels = it) })
        }
        Text("Grid columns: ${local.columns}")
        Slider(
            value = local.columns.toFloat(),
            onValueChange = { local = local.copy(columns = it.toInt().coerceIn(3, 6)) },
            valueRange = 3f..6f,
            steps = 2
        )
        Text("Grid rows: ${local.rows}")
        Slider(
            value = local.rows.toFloat(),
            onValueChange = { local = local.copy(rows = it.toInt().coerceIn(4, 7)) },
            valueRange = 4f..7f,
            steps = 2
        )
        Text("Icon size: ${local.iconSize}dp")
        Slider(
            value = local.iconSize.toFloat(),
            onValueChange = { local = local.copy(iconSize = it.toInt().coerceIn(42, 72)) },
            valueRange = 42f..72f
        )
        Text("Dock icons: ${local.dockSlots}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4,5,6,7,8).forEach { n ->
                TextButton(onClick = { local = local.copy(dockSlots = n) }) { Text("$n") }
            }
        }
        Text("Dock opacity: ${local.dockOpacity}%")
        Slider(
            value = local.dockOpacity.toFloat(),
            onValueChange = { local = local.copy(dockOpacity = it.toInt().coerceIn(35, 95)) },
            valueRange = 35f..95f
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("24-hour clock")
            Switch(local.clock24h, onCheckedChange = { local = local.copy(clock24h = it) })
        }
        Text("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Light" to 0, "Dark" to 1, "System" to 2).forEach { (name, value) ->
                TextButton(onClick = { local = local.copy(darkMode = value) }) { Text(name) }
            }
        }
        Button(onClick = { onSave(local) }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
        Button(onClick = onDefault, modifier = Modifier.fillMaxWidth()) { Text("Set as default launcher") }
        Button(onClick = onWallpaper, modifier = Modifier.fillMaxWidth()) { Text("Wallpaper settings") }
    }
}
