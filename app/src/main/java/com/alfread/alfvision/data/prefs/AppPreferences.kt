package com.alfread.alfvision.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alfread.alfvision.core.AccentColor
import com.alfread.alfvision.core.AutoDeletePeriod
import com.alfread.alfvision.core.Constants
import com.alfread.alfvision.core.PanelStyle
import com.alfread.alfvision.core.ResponseStyle
import com.alfread.alfvision.core.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

class AppPreferences(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val selectedModel = stringPreferencesKey("selected_model")
        val responseStyle = stringPreferencesKey("response_style")
        val temperature = floatPreferencesKey("temperature")
        val maxTokens = intPreferencesKey("max_tokens")
        val saveHistory = booleanPreferencesKey("save_history")
        val saveScreenshots = booleanPreferencesKey("save_screenshots")
        val sendOnlySelectedRegion = booleanPreferencesKey("send_only_selected_region")
        val autoAnalyze = booleanPreferencesKey("auto_analyze")
        val autoAnalyzeIntervalMs = intPreferencesKey("auto_analyze_interval_ms")
        val gamingMode = booleanPreferencesKey("gaming_mode")
        val captureQuality = intPreferencesKey("capture_quality")
        val panelWidth = intPreferencesKey("panel_width")
        val panelHeight = intPreferencesKey("panel_height")
        val panelX = intPreferencesKey("panel_x")
        val panelY = intPreferencesKey("panel_y")
        val orbSize = intPreferencesKey("orb_size")
        val opacity = floatPreferencesKey("opacity")
        val snap = booleanPreferencesKey("snap")
        val lockPosition = booleanPreferencesKey("lock_position")
        val animation = booleanPreferencesKey("animation")
        val autoHide = booleanPreferencesKey("auto_hide")
        val blur = booleanPreferencesKey("blur")
        val panelStyle = stringPreferencesKey("panel_style")
        val timeoutSeconds = intPreferencesKey("timeout_seconds")
        val retryCount = intPreferencesKey("retry_count")
        val debugMode = booleanPreferencesKey("debug_mode")
        val autoDelete = stringPreferencesKey("auto_delete")
        val activeProfile = stringPreferencesKey("active_profile")
        val lastSuccessfulRequest = stringPreferencesKey("last_successful_request")
        val lastError = stringPreferencesKey("last_error")
    }

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { p ->
        AppSettings(
            theme = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
            accent = runCatching { AccentColor.valueOf(p[Keys.accent] ?: AccentColor.BLUE.name) }.getOrDefault(AccentColor.BLUE),
            selectedModel = p[Keys.selectedModel] ?: Constants.DEFAULT_MODEL,
            responseStyle = runCatching { ResponseStyle.valueOf(p[Keys.responseStyle] ?: ResponseStyle.NORMAL.name) }.getOrDefault(ResponseStyle.NORMAL),
            temperature = (p[Keys.temperature] ?: 0.7f).coerceIn(0f, 2f),
            maxTokens = (p[Keys.maxTokens] ?: 2048).coerceIn(128, 16384),
            saveHistory = p[Keys.saveHistory] ?: true,
            saveScreenshots = p[Keys.saveScreenshots] ?: false,
            sendOnlySelectedRegion = p[Keys.sendOnlySelectedRegion] ?: true,
            autoAnalyze = p[Keys.autoAnalyze] ?: false,
            autoAnalyzeIntervalMs = (p[Keys.autoAnalyzeIntervalMs] ?: 5000).coerceIn(500, 30000),
            gamingMode = p[Keys.gamingMode] ?: false,
            captureQuality = (p[Keys.captureQuality] ?: 85).coerceIn(50, 100),
            panelWidth = (p[Keys.panelWidth] ?: Constants.PANEL_DEFAULT_WIDTH).coerceIn(Constants.PANEL_MIN_WIDTH, 720),
            panelHeight = (p[Keys.panelHeight] ?: Constants.PANEL_DEFAULT_HEIGHT).coerceIn(Constants.PANEL_MIN_HEIGHT, 1000),
            panelX = p[Keys.panelX] ?: 24,
            panelY = p[Keys.panelY] ?: 80,
            orbSize = (p[Keys.orbSize] ?: Constants.ORB_DEFAULT_SIZE).coerceIn(48, 120),
            opacity = (p[Keys.opacity] ?: 0.96f).coerceIn(0.35f, 1f),
            snap = p[Keys.snap] ?: true,
            lockPosition = p[Keys.lockPosition] ?: false,
            animation = p[Keys.animation] ?: true,
            autoHide = p[Keys.autoHide] ?: false,
            blur = p[Keys.blur] ?: true,
            panelStyle = runCatching { PanelStyle.valueOf(p[Keys.panelStyle] ?: PanelStyle.GLASS.name) }.getOrDefault(PanelStyle.GLASS),
            timeoutSeconds = (p[Keys.timeoutSeconds] ?: 45).coerceIn(10, 180),
            retryCount = (p[Keys.retryCount] ?: 2).coerceIn(0, 5),
            debugMode = p[Keys.debugMode] ?: false,
            autoDelete = runCatching { AutoDeletePeriod.valueOf(p[Keys.autoDelete] ?: AutoDeletePeriod.NEVER.name) }.getOrDefault(AutoDeletePeriod.NEVER),
            activeProfile = p[Keys.activeProfile] ?: "General",
            lastSuccessfulRequest = p[Keys.lastSuccessfulRequest] ?: "Never",
            lastError = p[Keys.lastError] ?: "None"
        )
    }

    suspend fun update(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.appSettingsDataStore.edit(block)
    }

    suspend fun setTheme(value: ThemeMode) = setEnum(Keys.theme, value)
    suspend fun setAccent(value: AccentColor) = setEnum(Keys.accent, value)
    suspend fun setSelectedModel(value: String) = update { it[Keys.selectedModel] = value }
    suspend fun setResponseStyle(value: ResponseStyle) = setEnum(Keys.responseStyle, value)
    suspend fun setTemperature(value: Float) = update { it[Keys.temperature] = value.coerceIn(0f, 2f) }
    suspend fun setMaxTokens(value: Int) = update { it[Keys.maxTokens] = value.coerceIn(128, 16384) }
    suspend fun setSaveHistory(value: Boolean) = update { it[Keys.saveHistory] = value }
    suspend fun setSaveScreenshots(value: Boolean) = update { it[Keys.saveScreenshots] = value }
    suspend fun setSendOnlySelectedRegion(value: Boolean) = update { it[Keys.sendOnlySelectedRegion] = value }
    suspend fun setAutoAnalyze(value: Boolean) = update { it[Keys.autoAnalyze] = value }
    suspend fun setAutoAnalyzeIntervalMs(value: Int) = update { it[Keys.autoAnalyzeIntervalMs] = value.coerceIn(500, 30000) }
    suspend fun setGamingMode(value: Boolean) = update { it[Keys.gamingMode] = value }
    suspend fun setCaptureQuality(value: Int) = update { it[Keys.captureQuality] = value.coerceIn(50, 100) }
    suspend fun setPanelSize(width: Int, height: Int) = update { it[Keys.panelWidth] = width; it[Keys.panelHeight] = height }
    suspend fun setPanelPosition(x: Int, y: Int) = update { it[Keys.panelX] = x; it[Keys.panelY] = y }
    suspend fun setOrbSize(value: Int) = update { it[Keys.orbSize] = value }
    suspend fun setOpacity(value: Float) = update { it[Keys.opacity] = value.coerceIn(0.35f, 1f) }
    suspend fun setSnap(value: Boolean) = update { it[Keys.snap] = value }
    suspend fun setLockPosition(value: Boolean) = update { it[Keys.lockPosition] = value }
    suspend fun setAnimation(value: Boolean) = update { it[Keys.animation] = value }
    suspend fun setAutoHide(value: Boolean) = update { it[Keys.autoHide] = value }
    suspend fun setBlur(value: Boolean) = update { it[Keys.blur] = value }
    suspend fun setPanelStyle(value: PanelStyle) = setEnum(Keys.panelStyle, value)
    suspend fun setTimeout(value: Int) = update { it[Keys.timeoutSeconds] = value }
    suspend fun setRetryCount(value: Int) = update { it[Keys.retryCount] = value }
    suspend fun setDebug(value: Boolean) = update { it[Keys.debugMode] = value }
    suspend fun setAutoDelete(value: AutoDeletePeriod) = setEnum(Keys.autoDelete, value)
    suspend fun setActiveProfile(value: String) = update { it[Keys.activeProfile] = value }
    suspend fun setLastSuccessfulRequest(value: String) = update { it[Keys.lastSuccessfulRequest] = value }
    suspend fun setLastError(value: String) = update { it[Keys.lastError] = value.take(240) }

    suspend fun snapshot(): AppSettings = settings.first()

    private suspend fun <T : Enum<T>> setEnum(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: T) {
        update { it[key] = value.name }
    }
}

data class AppSettings(
    val theme: ThemeMode,
    val accent: AccentColor,
    val selectedModel: String,
    val responseStyle: ResponseStyle,
    val temperature: Float,
    val maxTokens: Int,
    val saveHistory: Boolean,
    val saveScreenshots: Boolean,
    val sendOnlySelectedRegion: Boolean,
    val autoAnalyze: Boolean,
    val autoAnalyzeIntervalMs: Int,
    val gamingMode: Boolean,
    val captureQuality: Int,
    val panelWidth: Int,
    val panelHeight: Int,
    val panelX: Int,
    val panelY: Int,
    val orbSize: Int,
    val opacity: Float,
    val snap: Boolean,
    val lockPosition: Boolean,
    val animation: Boolean,
    val autoHide: Boolean,
    val blur: Boolean,
    val panelStyle: PanelStyle,
    val timeoutSeconds: Int,
    val retryCount: Int,
    val debugMode: Boolean,
    val autoDelete: AutoDeletePeriod,
    val activeProfile: String,
    val lastSuccessfulRequest: String,
    val lastError: String
)
