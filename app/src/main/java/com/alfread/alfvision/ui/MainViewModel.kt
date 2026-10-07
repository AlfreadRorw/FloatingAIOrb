package com.alfread.alfvision.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfread.alfvision.core.AppContainer
import com.alfread.alfvision.core.Constants
import com.alfread.alfvision.core.CapturedFrame
import com.alfread.alfvision.data.network.GroqError
import com.alfread.alfvision.data.network.GroqModelInfo
import com.alfread.alfvision.data.prefs.AppSettings
import com.alfread.alfvision.data.local.AIProfileEntity
import com.alfread.alfvision.core.VisionEventBus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    val settings: StateFlow<AppSettings> = AppContainer.preferences.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        defaultSettings()
    )

    private val _apiKeyConfigured = MutableStateFlow(AppContainer.secureStore.hasApiKey())
    val apiKeyConfigured: StateFlow<Boolean> = _apiKeyConfigured.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Not tested")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _models = MutableStateFlow<List<GroqModelInfo>>(emptyList())
    val models: StateFlow<List<GroqModelInfo>> = _models.asStateFlow()

    private val _frame = MutableStateFlow<CapturedFrame?>(null)
    val frame: StateFlow<CapturedFrame?> = _frame.asStateFlow()
    private val _previousFrame = MutableStateFlow<CapturedFrame?>(null)
    val previousFrame: StateFlow<CapturedFrame?> = _previousFrame.asStateFlow()

    private val _profiles = MutableStateFlow<List<AIProfileEntity>>(emptyList())
    val profiles: StateFlow<List<AIProfileEntity>> = _profiles.asStateFlow()

    val conversations = AppContainer.history.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { AppContainer.profiles.ensureDefaults() }
        viewModelScope.launch { AppContainer.profiles.observe().collect { _profiles.value = it } }
        viewModelScope.launch {
            VisionEventBus.frames.collect { value ->
                _previousFrame.value = _frame.value
                _frame.value = value
            }
        }
    }

    fun saveApiKey(value: String) {
        _error.value = null
        if (value.trim().length < 10) {
            _error.value = "API key terlalu pendek."
            return
        }
        AppContainer.secureStore.saveApiKey(value.trim())
        _apiKeyConfigured.value = true
        _connectionStatus.value = "Saved securely"
    }

    fun deleteApiKey() {
        AppContainer.secureStore.deleteApiKey()
        _apiKeyConfigured.value = false
        _connectionStatus.value = "Not configured"
    }

    fun testConnection() {
        viewModelScope.launch {
            _connectionStatus.value = "Testing…"
            val result = AppContainer.groq.testConnection()
            result.onSuccess { _connectionStatus.value = it }
                .onFailure { error -> _connectionStatus.value = error.userMessage(); _error.value = error.userMessage() }
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            AppContainer.groq.listModels().onSuccess { _models.value = it }
                .onFailure { _error.value = it.userMessage() }
        }
    }

    fun setModel(model: String) = viewModelScope.launch { AppContainer.preferences.setSelectedModel(model) }
    fun setTheme(value: com.alfread.alfvision.core.ThemeMode) = viewModelScope.launch { AppContainer.preferences.setTheme(value) }
    fun setAccent(value: com.alfread.alfvision.core.AccentColor) = viewModelScope.launch { AppContainer.preferences.setAccent(value) }
    fun setResponseStyle(value: com.alfread.alfvision.core.ResponseStyle) = viewModelScope.launch { AppContainer.preferences.setResponseStyle(value) }
    fun setTemperature(value: Float) = viewModelScope.launch { AppContainer.preferences.setTemperature(value) }
    fun setMaxTokens(value: Int) = viewModelScope.launch { AppContainer.preferences.setMaxTokens(value) }
    fun setSaveHistory(value: Boolean) = viewModelScope.launch { AppContainer.preferences.setSaveHistory(value) }
    fun setSaveScreenshots(value: Boolean) = viewModelScope.launch { AppContainer.preferences.setSaveScreenshots(value) }
    fun setSendOnlyRegion(value: Boolean) = viewModelScope.launch { AppContainer.preferences.setSendOnlySelectedRegion(value) }
    fun setAutoAnalyze(value: Boolean) = viewModelScope.launch { AppContainer.preferences.setAutoAnalyze(value) }
    fun setAutoAnalyzeIntervalMs(value: Int) = viewModelScope.launch { AppContainer.preferences.setAutoAnalyzeIntervalMs(value) }
    fun setGamingMode(value: Boolean) = viewModelScope.launch { AppContainer.preferences.setGamingMode(value) }
    fun setCaptureQuality(value: Int) = viewModelScope.launch { AppContainer.preferences.setCaptureQuality(value) }
    fun setOpacity(value: Float) = viewModelScope.launch { AppContainer.preferences.setOpacity(value) }
    fun setSnap(value: Boolean) = viewModelScope.launch { AppContainer.preferences.setSnap(value) }
    fun setLockPosition(value: Boolean) = viewModelScope.launch { AppContainer.preferences.setLockPosition(value) }
    fun setAnimation(value: Boolean) = viewModelScope.launch { AppContainer.preferences.setAnimation(value) }
    fun setAutoHide(value: Boolean) = viewModelScope.launch { AppContainer.preferences.setAutoHide(value) }
    fun setBlur(value: Boolean) = viewModelScope.launch { AppContainer.preferences.setBlur(value) }
    fun setPanelStyle(value: com.alfread.alfvision.core.PanelStyle) = viewModelScope.launch { AppContainer.preferences.setPanelStyle(value) }
    fun setTimeout(value: Int) = viewModelScope.launch { AppContainer.preferences.setTimeout(value) }
    fun setRetryCount(value: Int) = viewModelScope.launch { AppContainer.preferences.setRetryCount(value) }
    fun setDebug(value: Boolean) = viewModelScope.launch { AppContainer.preferences.setDebug(value) }
    fun setAutoDelete(value: com.alfread.alfvision.core.AutoDeletePeriod) = viewModelScope.launch { AppContainer.preferences.setAutoDelete(value) }
    fun setProfile(value: String) = viewModelScope.launch { AppContainer.preferences.setActiveProfile(value) }

    fun clearError() { _error.value = null }

    private fun Throwable.userMessage(): String = when (this) {
        is GroqError.MissingApiKey -> "Groq API Key belum diatur."
        is GroqError.RateLimit -> "Rate limit reached."
        is GroqError.ModelUnavailable -> message ?: "Model tidak tersedia."
        is GroqError.Network -> message ?: "Internet tidak tersedia."
        is GroqError.Auth -> message ?: "API key tidak valid."
        else -> message ?: "Terjadi kesalahan."
    }
}

private fun defaultSettings(): AppSettings = AppSettings(
    theme = com.alfread.alfvision.core.ThemeMode.DARK,
    accent = com.alfread.alfvision.core.AccentColor.BLUE,
    selectedModel = Constants.DEFAULT_MODEL,
    responseStyle = com.alfread.alfvision.core.ResponseStyle.NORMAL,
    temperature = 0.7f,
    maxTokens = 2048,
    saveHistory = true,
    saveScreenshots = false,
    sendOnlySelectedRegion = true,
    autoAnalyze = false,
    autoAnalyzeIntervalMs = 5000,
    gamingMode = false,
    captureQuality = 85,
    panelWidth = 360,
    panelHeight = 520,
    panelX = 24,
    panelY = 80,
    orbSize = 64,
    opacity = 0.96f,
    snap = true,
    lockPosition = false,
    animation = true,
    autoHide = false,
    blur = true,
    panelStyle = com.alfread.alfvision.core.PanelStyle.GLASS,
    timeoutSeconds = 45,
    retryCount = 2,
    debugMode = false,
    autoDelete = com.alfread.alfvision.core.AutoDeletePeriod.NEVER,
    activeProfile = "General",
    lastSuccessfulRequest = "Never",
    lastError = "None"
)
