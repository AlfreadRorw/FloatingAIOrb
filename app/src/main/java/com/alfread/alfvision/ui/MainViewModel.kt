package com.alfread.alfvision.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alfread.alfvision.AlfVisionApplication
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.data.local.ConversationEntity
import com.alfread.alfvision.data.local.RegionPresetEntity
import com.alfread.alfvision.service.FloatingPanelService
import com.alfread.alfvision.service.ScreenCaptureService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as AlfVisionApplication).container

    val settings: StateFlow<AppSettings> =
        c.settingsRepository.flow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings()
        )

    val conversations: StateFlow<List<ConversationEntity>> =
        c.historyRepository.observeConversations()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val regions: StateFlow<List<RegionPresetEntity>> =
        c.regionRepository.observe()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val profiles =
        c.profileRepository.observe()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val session = c.sessionStore
    val network = c.networkMonitor.connected
    val shizuku = c.shizukuCompat

    private val _groqStatus = MutableStateFlow<String?>(null)
    val groqStatus: StateFlow<String?> = _groqStatus

    private val _models = MutableStateFlow(
        listOf(
            GroqModel(
                id = "qwen/qwen3.8-27b",
                active = true,
                supportsVision = true
            )
        )
    )

    val models: StateFlow<List<GroqModel>> = _models

    fun saveApiKey(value: String) {
        val key = value.trim()
        if (key.isEmpty()) {
            _groqStatus.value = "API key cannot be empty."
            return
        }
        c.secureStore.putApiKey(key)
        _groqStatus.value = "API key saved securely on device."
    }

    fun deleteApiKey() {
        c.secureStore.deleteApiKey()
        _groqStatus.value = "API key deleted."
    }

    fun hasApiKey(): Boolean = c.secureStore.hasApiKey()

    fun testConnection() {
        viewModelScope.launch {
            _groqStatus.value = "Testing connection..."
            runCatching {
                c.groqRepository.listModels()
            }.onSuccess { list ->
                _models.value = list
                _groqStatus.value = "Connected. ${list.size} models available."
            }.onFailure { error ->
                _groqStatus.value = error.message ?: "Connection failed."
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            c.settingsRepository.update(transform)
        }
    }

    fun showRegionSelector() {
        startFloating()

        val intent = Intent(
            c.appContext,
            FloatingPanelService::class.java
        ).apply {
            action = FloatingPanelService.ACTION_REGION
        }

        startFloatingService(intent)
    }

    fun clearScreenshots() {
        c.imageStorage.clear()
    }

    fun startFloating() {
        val intent = Intent(
            c.appContext,
            FloatingPanelService::class.java
        ).apply {
            action = FloatingPanelService.ACTION_SHOW
        }

        startFloatingService(intent)
    }

    private fun startFloatingService(intent: Intent) {
        c.appContext.startService(intent)
    }

    fun stopCapture() {
        val intent = Intent(
            c.appContext,
            ScreenCaptureService::class.java
        ).apply {
            action = ScreenCaptureService.ACTION_STOP
        }

        c.appContext.startService(intent)
    }

    fun capture() {
        c.controller.capture(c.sessionStore.region.value)
    }

    fun ask(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty()) return
        c.controller.ask(text)
    }

    fun quickAction(action: String) {
        c.controller.quickAction(action)
    }

    fun compare() {
        c.controller.compare()
    }

    fun stopRequest() {
        c.controller.cancelRequest()
    }

    fun retryLast() {
        c.controller.retryLast()
    }

    fun clearHistory() {
        viewModelScope.launch {
            c.historyRepository.deleteAll()
        }
    }

    fun cleanupHistory(days: Int) {
        if (days <= 0) return

        viewModelScope.launch {
            val cutoff =
                System.currentTimeMillis() - days * 86_400_000L
            c.historyRepository.deleteOlderThan(cutoff)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            c.historyRepository.deleteConversation(id)
        }
    }

    fun saveRegion(name: String, region: Region) {
        viewModelScope.launch {
            c.regionRepository.save(name.trim(), region)
        }
    }

    fun deleteRegion(item: RegionPresetEntity) {
        viewModelScope.launch {
            c.regionRepository.delete(item)
        }
    }

    fun duplicateRegion(item: RegionPresetEntity) {
        viewModelScope.launch {
            c.regionRepository.save(
                "${item.name} Copy",
                Region(
                    x = item.x,
                    y = item.y,
                    width = item.width,
                    height = item.height,
                    screenWidth = item.screenWidth,
                    screenHeight = item.screenHeight,
                    displayId = item.displayId,
                    rotation = item.rotation
                )
            )
        }
    }

    fun updateRegion(item: RegionPresetEntity) {
        viewModelScope.launch {
            c.regionRepository.update(item)
        }
    }

    fun openConversation(id: Long) {
        viewModelScope.launch {
            val lines = c.historyRepository.getMessages(id)
            c.sessionStore.clearChat()

            lines.forEach { line ->
                if (line.role == Role.USER) {
                    c.sessionStore.addUser(
                        line.content,
                        line.imagePath
                    )
                } else {
                    c.sessionStore.addAssistant(
                        line.content,
                        line.model ?: settings.value.activeModel,
                        ModelUsage(
                            totalTokens = line.tokenUsage ?: 0
                        ),
                        line.imagePath
                    )
                }
            }
        }
    }

    fun pinCurrent() {
        c.sessionStore.pinCurrent()
    }

    fun voice() {
        c.voiceInputManager.start()
    }

    fun createProfile(name: String, prompt: String) {
        val profileName = name.trim()
        if (profileName.isEmpty()) return

        viewModelScope.launch {
            c.profileRepository.create(
                AiProfile(
                    name = profileName,
                    systemPrompt = prompt
                )
            )
        }
    }
}
