package com.alfread.alfvision.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alfread.alfvision.AlfVisionApplication
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.data.local.ConversationEntity
import com.alfread.alfvision.data.local.RegionPresetEntity
import com.alfread.alfvision.service.FloatingPanelService
import com.alfread.alfvision.service.ScreenCaptureService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as AlfVisionApplication).container

    val settings = c.settingsRepository.flow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )

    val conversations: StateFlow<List<ConversationEntity>> =
        c.historyRepository.observeConversations().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val regions: StateFlow<List<RegionPresetEntity>> =
        c.regionRepository.observe().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val profiles = c.profileRepository.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val session = c.sessionStore
    val network = c.networkMonitor.connected
    val shizuku = c.shizukuCompat

    private val _groqStatus = MutableStateFlow<String?>(null)
    val groqStatus: StateFlow<String?> = _groqStatus

    private val _models = MutableStateFlow(
        listOf(
            GroqModel(
                "qwen/qwen3.8-27b",
                true,
                supportsVision = true
            )
        )
    )

    val models: StateFlow<List<GroqModel>> = _models

    fun saveApiKey(value: String) {
        c.secureStore.putApiKey(value)
        _groqStatus.value = "API key saved securely on device."
    }

    fun deleteApiKey() {
        c.secureStore.deleteApiKey()
        _groqStatus.value = "API key deleted."
    }

    fun hasApiKey(): Boolean =
        c.secureStore.hasApiKey()

    fun testConnection() {
        viewModelScope.launch {
            _groqStatus.value = "Testing connection..."

            runCatching {
                c.groqRepository.listModels()
            }.onSuccess { list ->
                _models.value = list
                _groqStatus.value =
                    "Connected. ${list.size} models available."
            }.onFailure {
                _groqStatus.value =
                    it.message ?: "Connection failed."
            }
        }
    }

    fun updateSettings(
        transform: (AppSettings) -> AppSettings
    ) {
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

    fun stopCapture() {
        val intent = Intent(
            c.appContext,
            ScreenCaptureService::class.java
        ).apply {
            action = ScreenCaptureService.ACTION_STOP
        }

        ContextCompat.startService(
            c.appContext,
            intent
        )
    }

    private fun startFloatingService(intent: Intent) {
        ContextCompat.startForegroundService(
            c.appContext,
            intent
        )
    }

    fun capture() {
        c.controller.capture(c.sessionStore.region.value)
    }

    fun ask(prompt: String) {
        c.controller.ask(prompt)
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
        if (days > 0) {
            viewModelScope.launch {
                c.historyRepository.deleteOlderThan(
                    System.currentTimeMillis() -
                        days * 86_400_000L
                )
            }
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            c.historyRepository.deleteConversation(id)
        }
    }

    fun saveRegion(
        name: String,
        region: Region
    ) {
        viewModelScope.launch {
            c.regionRepository.save(name, region)
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
                    item.x,
                    item.y,
                    item.width,
                    item.height,
                    item.screenWidth,
                    item.screenHeight,
                    item.displayId,
                    item.rotation
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

    fun createProfile(
        name: String,
        prompt: String
    ) {
        viewModelScope.launch {
            c.profileRepository.create(
                AiProfile(
                    name = name,
                    systemPrompt = prompt
                )
            )
        }
    }
}