package com.alfread.alfvision.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.data.local.AppSettingsDao
import com.alfread.alfvision.data.local.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch

private val Context.alfSettingsDataStore by preferencesDataStore("alf_settings")

class SettingsRepository(private val context: Context, private val dao: AppSettingsDao) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val model = stringPreferencesKey("model")
        val profile = longPreferencesKey("profile")
        val temperature = floatPreferencesKey("temperature")
        val maxTokens = intPreferencesKey("max_tokens")
        val responseStyle = stringPreferencesKey("response_style")
        val saveHistory = booleanPreferencesKey("save_history")
        val autoDeleteDays = intPreferencesKey("auto_delete_days")
        val debug = booleanPreferencesKey("debug")
        val timeout = longPreferencesKey("timeout")
        val retries = intPreferencesKey("retries")
        val shizuku = booleanPreferencesKey("shizuku")
        val autoAnalyze = booleanPreferencesKey("auto_analyze")
        val interval = stringPreferencesKey("interval")
        val quality = intPreferencesKey("quality")
        val maxImageBytes = intPreferencesKey("max_image_bytes")
        val saveScreenshots = booleanPreferencesKey("save_screenshots")
        val sendOnlyRegion = booleanPreferencesKey("send_only_region")
        val screenshotPreview = booleanPreferencesKey("screenshot_preview")
        val freezeFrame = booleanPreferencesKey("freeze_frame")
        val panelWidth = intPreferencesKey("panel_width")
        val panelHeight = intPreferencesKey("panel_height")
        val orbSize = intPreferencesKey("orb_size")
        val opacity = floatPreferencesKey("opacity")
        val snap = booleanPreferencesKey("snap")
        val locked = booleanPreferencesKey("locked")
        val compact = booleanPreferencesKey("compact")
        val autoHide = booleanPreferencesKey("auto_hide")
        val autoHideMillis = longPreferencesKey("auto_hide_millis")
        val animation = booleanPreferencesKey("animation")
        val panelX = intPreferencesKey("panel_x")
        val panelY = intPreferencesKey("panel_y")
    }

    private fun read(p: Preferences): AppSettings =
        AppSettings(
            theme = enumOrDefault(p[K.theme], ThemeMode.SYSTEM),
            accent = enumOrDefault(p[K.accent], Accent.PURPLE),
            activeModel = p[K.model] ?: "qwen/qwen3.8-27b",
            activeProfileId = p[K.profile] ?: 0,
            temperature = (p[K.temperature] ?: 0.2f).coerceIn(0f, 2f),
            maxTokens = (p[K.maxTokens] ?: 1024).coerceIn(128, 16384),
            responseStyle = enumOrDefault(p[K.responseStyle], ResponseStyle.NORMAL),
            saveHistory = p[K.saveHistory] ?: true,
            autoDeleteDays = p[K.autoDeleteDays] ?: 0,
            debugLogging = p[K.debug] ?: false,
            networkTimeoutSeconds = (p[K.timeout] ?: 30).coerceIn(10, 120),
            retryCount = (p[K.retries] ?: 2).coerceIn(0, 4),
            shizukuEnhanced = p[K.shizuku] ?: false,
            vision = VisionSettings(
                autoAnalyze = p[K.autoAnalyze] ?: false,
                interval = enumOrDefault(p[K.interval], AutoAnalyzeInterval.TEN_SECONDS),
                quality = (p[K.quality] ?: 88).coerceIn(40, 95),
                maxImageBytes = (p[K.maxImageBytes] ?: 7_000_000).coerceIn(500_000, 19_000_000),
                saveScreenshots = p[K.saveScreenshots] ?: false,
                sendOnlyRegion = p[K.sendOnlyRegion] ?: true,
                screenshotPreview = p[K.screenshotPreview] ?: true,
                freezeFrame = p[K.freezeFrame] ?: true
            ),
            floating = FloatingSettings(
                panelWidthDp = (p[K.panelWidth] ?: 360).coerceIn(280, 520),
                panelHeightDp = (p[K.panelHeight] ?: 540).coerceIn(300, 820),
                orbSizeDp = (p[K.orbSize] ?: 60).coerceIn(44, 100),
                opacity = (p[K.opacity] ?: 0.96f).coerceIn(0.55f, 1f),
                snapToEdge = p[K.snap] ?: true,
                locked = p[K.locked] ?: false,
                compactMode = p[K.compact] ?: false,
                autoHide = p[K.autoHide] ?: false,
                autoHideMillis = (p[K.autoHideMillis] ?: 8_000).coerceIn(2_000, 60_000),
                animation = p[K.animation] ?: true,
                x = p[K.panelX] ?: 24,
                y = p[K.panelY] ?: 140
            )
            )

    val flow: Flow<AppSettings> = context.alfSettingsDataStore.data
        .map { p -> read(p) }
        .catch { emit(AppSettings()) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        var result: AppSettings? = null
        context.alfSettingsDataStore.edit { p ->
            // Baca + tulis dalam satu transaksi DataStore supaya update beruntun tidak saling menimpa.
            val next = transform(read(p))
            result = next
            p[K.theme] = next.theme.name
            p[K.accent] = next.accent.name
            p[K.model] = next.activeModel
            p[K.profile] = next.activeProfileId
            p[K.temperature] = next.temperature
            p[K.maxTokens] = next.maxTokens
            p[K.responseStyle] = next.responseStyle.name
            p[K.saveHistory] = next.saveHistory
            p[K.autoDeleteDays] = next.autoDeleteDays
            p[K.debug] = next.debugLogging
            p[K.timeout] = next.networkTimeoutSeconds
            p[K.retries] = next.retryCount
            p[K.shizuku] = next.shizukuEnhanced
            p[K.autoAnalyze] = next.vision.autoAnalyze
            p[K.interval] = next.vision.interval.name
            p[K.quality] = next.vision.quality
            p[K.maxImageBytes] = next.vision.maxImageBytes
            p[K.saveScreenshots] = next.vision.saveScreenshots
            p[K.sendOnlyRegion] = next.vision.sendOnlyRegion
            p[K.screenshotPreview] = next.vision.screenshotPreview
            p[K.freezeFrame] = next.vision.freezeFrame
            p[K.panelWidth] = next.floating.panelWidthDp
            p[K.panelHeight] = next.floating.panelHeightDp
            p[K.orbSize] = next.floating.orbSizeDp
            p[K.opacity] = next.floating.opacity
            p[K.snap] = next.floating.snapToEdge
            p[K.locked] = next.floating.locked
            p[K.compact] = next.floating.compactMode
            p[K.autoHide] = next.floating.autoHide
            p[K.autoHideMillis] = next.floating.autoHideMillis
            p[K.animation] = next.floating.animation
            p[K.panelX] = next.floating.x
            p[K.panelY] = next.floating.y
        }
        val next = result ?: return
        runCatching {
            dao.upsert(
                AppSettingsEntity(
                    theme = next.theme.name,
                    accent = next.accent.name,
                    model = next.activeModel,
                    profileId = next.activeProfileId,
                    temperature = next.temperature,
                    maxTokens = next.maxTokens,
                    responseStyle = next.responseStyle.name,
                    saveHistory = next.saveHistory,
                    autoDeleteDays = next.autoDeleteDays,
                    debugLogging = next.debugLogging,
                    networkTimeoutSeconds = next.networkTimeoutSeconds,
                    retryCount = next.retryCount,
                    shizukuEnhanced = next.shizukuEnhanced,
                    savedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun savePanelPosition(x: Int, y: Int) = update { it.copy(floating = it.floating.copy(x = x, y = y)) }
    suspend fun savePanelSize(widthDp: Int, heightDp: Int) = update { it.copy(floating = it.floating.copy(panelWidthDp = widthDp, panelHeightDp = heightDp)) }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        runCatching { value?.let { enumValueOf<T>(it) } ?: default }.getOrDefault(default)
}
