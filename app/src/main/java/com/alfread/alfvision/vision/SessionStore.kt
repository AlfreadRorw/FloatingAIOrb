package com.alfread.alfvision.vision

import com.alfread.alfvision.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SessionStore {
    private val _lines = MutableStateFlow<List<ChatLine>>(emptyList())
    val lines: StateFlow<List<ChatLine>> = _lines
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input
    private val _region = MutableStateFlow<Region?>(null)
    val region: StateFlow<Region?> = _region
    private val _currentImage = MutableStateFlow<PendingImage?>(null)
    val currentImage: StateFlow<PendingImage?> = _currentImage
    private val _previousImage = MutableStateFlow<PendingImage?>(null)
    val previousImage: StateFlow<PendingImage?> = _previousImage
    private val _pinnedImage = MutableStateFlow<PendingImage?>(null)
    val pinnedImage: StateFlow<PendingImage?> = _pinnedImage
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState

    fun setInput(value: String) { _input.value = value }
    fun addUser(text: String, imagePath: String? = null, imageBytes: ByteArray? = null) { _lines.value = _lines.value + ChatLine(System.nanoTime(), Role.USER, text, imagePath = imagePath, imageBytes = imageBytes) }
    fun addAssistant(text: String, model: String, usage: ModelUsage, imagePath: String? = null) { _lines.value = _lines.value + ChatLine(System.nanoTime(), Role.ASSISTANT, text, model = model, tokenUsage = usage.totalTokens, imagePath = imagePath) }
    fun clearChat() { _lines.value = emptyList() }
    fun removeLastAssistant() { if (_lines.value.lastOrNull()?.role == Role.ASSISTANT) _lines.value = _lines.value.dropLast(1) }
    fun removeLastUser() { if (_lines.value.lastOrNull()?.role == Role.USER) _lines.value = _lines.value.dropLast(1) }
    fun setRegion(value: Region?) { _region.value = value }
    fun setBusy(value: Boolean) { _busy.value = value }
    fun setError(value: String?) { _error.value = value }
    fun setVoiceState(value: VoiceState) { _voiceState.value = value }
    fun setImage(value: PendingImage) { _previousImage.value = _currentImage.value; _currentImage.value = value }
    fun clearImage() { _currentImage.value = null }
    fun pinCurrent() { _pinnedImage.value = _currentImage.value }
    fun unpin() { _pinnedImage.value = null }
}

enum class VoiceState { IDLE, LISTENING, ERROR }
