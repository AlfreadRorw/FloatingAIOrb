package com.alfread.alflauncher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherDataStore by preferencesDataStore("alf_launcher")

data class LauncherSettings(
    val columns: Int = 4,
    val rows: Int = 5,
    val iconSize: Int = 54,
    val labels: Boolean = true,
    val dockSlots: Int = 5,
    val dockOpacity: Int = 72,
    val darkMode: Int = 2,
    val clock24h: Boolean = false
)

class LauncherDataStore(private val context: Context) {
    private val columns = intPreferencesKey("columns")
    private val rows = intPreferencesKey("rows")
    private val iconSize = intPreferencesKey("icon_size")
    private val labels = booleanPreferencesKey("labels")
    private val dockSlots = intPreferencesKey("dock_slots")
    private val dockOpacity = intPreferencesKey("dock_opacity")
    private val darkMode = intPreferencesKey("dark_mode")
    private val clock24h = booleanPreferencesKey("clock_24h")
    private val dockPackages = stringPreferencesKey("dock_packages")

    val settings: Flow<LauncherSettings> = context.launcherDataStore.data.map {
        LauncherSettings(
            columns = it[columns] ?: 4,
            rows = it[rows] ?: 5,
            iconSize = it[iconSize] ?: 54,
            labels = it[labels] ?: true,
            dockSlots = it[dockSlots] ?: 5,
            dockOpacity = it[dockOpacity] ?: 72,
            darkMode = it[darkMode] ?: 2,
            clock24h = it[clock24h] ?: false
        )
    }

    val dock: Flow<List<String>> = context.launcherDataStore.data.map {
        it[dockPackages]?.split("|")?.filter(String::isNotBlank) ?: emptyList()
    }

    suspend fun save(settings: LauncherSettings) {
        context.launcherDataStore.edit {
            it[columns] = settings.columns
            it[rows] = settings.rows
            it[iconSize] = settings.iconSize
            it[labels] = settings.labels
            it[dockSlots] = settings.dockSlots
            it[dockOpacity] = settings.dockOpacity
            it[darkMode] = settings.darkMode
            it[clock24h] = settings.clock24h
        }
    }

    suspend fun saveDock(packages: List<String>) {
        context.launcherDataStore.edit { it[dockPackages] = packages.joinToString("|") }
    }
}
