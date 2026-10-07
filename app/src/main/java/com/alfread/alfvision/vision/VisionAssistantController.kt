package com.alfread.alfvision.vision

import android.util.Base64
import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.data.network.GroqApiException
import com.alfread.alfvision.data.network.GroqRepository
import com.alfread.alfvision.data.repository.HistoryRepository
import com.alfread.alfvision.data.repository.ProfileRepository
import com.alfread.alfvision.data.repository.SettingsRepository
import com.alfread.alfvision.service.FloatingPanelService
import com.alfread.alfvision.service.ScreenCaptureService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class VisionAssistantController(
    private val settings: SettingsRepository,
    private val profiles: ProfileRepository,
    private val history: HistoryRepository,
    private val groq: GroqRepository,
    private val capture: CaptureCoordinator,
    private val imageProcessor: ImageProcessor,
    private val imageStorage: ImageStorage,
    private val session: SessionStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var conversationId: Long? = null
    private var autoJob: Job? = null
    private var activeAskJob: Job? = null
    private var lastPrompt: String? = null
    private var lastCompare: Boolean = false

    fun resetConversation() { conversationId = null }
    fun resumeConversation(id: Long?) { conversationId = id }

    fun capture(region: Region? = session.region.value, fullScreen: Boolean = region == null) {
        scope.launch { captureNow(region, fullScreen) }
    }

    /**
     * Ambil screenshot. Panel/overlay disembunyikan (bukan dihapus) lalu dimunculkan kembali.
     * FIX: sebelumnya view dihapus tetapi referensinya tidak di-null-kan sehingga panel tidak
     * pernah muncul lagi setelah capture pertama. Ada juga timeout supaya tidak stuck "busy".
     */
    suspend fun captureNow(region: Region? = session.region.value, @Suppress("UNUSED_PARAMETER") fullScreen: Boolean = region == null): Boolean {
        if (!ScreenCaptureService.isRunning) {
            session.setError("Screen capture belum aktif. Tekan START VISION dan izinkan screen capture.")
            return false
        }
        session.setBusy(true)
        session.setError(null)
        var success = false
        try {
            val vision = settings.flow.first().vision
            val effectiveRegion = if (vision.sendOnlyRegion) region else null
            FloatingPanelService.suppressForCapture(true)
            delay(280)
            val result = withTimeout(10_000) {
                capture.request(
                    region = effectiveRegion,
                    quality = vision.quality,
                    maxBytes = vision.maxImageBytes,
                    fullScreen = effectiveRegion == null
                )
            }
            session.setImage(
                PendingImage(
                    result.payload.bytes,
                    result.payload.mimeType,
                    if (effectiveRegion != null) "Region" else "Screen"
                )
            )
            success = true
        } catch (e: TimeoutCancellationException) {
            session.setError("Capture timeout. Pastikan screen capture aktif lalu coba lagi.")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            session.setError(errorMessage(t))
        } finally {
            FloatingPanelService.suppressForCapture(false)
            session.setBusy(false)
        }
        return success
    }

    fun ask(prompt: String, compare: Boolean = false): Job? {
        val cleaned = prompt.trim()
        if (cleaned.isEmpty()) return null
        lastPrompt = cleaned
        lastCompare = compare
        activeAskJob?.cancel()
        val job = scope.launch {
            session.setBusy(true)
            session.setError(null)
            try {
                val currentSettings = settings.flow.first()
                val profile = profiles.get(currentSettings.activeProfileId)
                val system = buildSystemPrompt(profile, currentSettings.responseStyle)
                val current = (session.currentImage.value ?: session.pinnedImage.value)?.bytes
                val previous = if (compare) session.previousImage.value?.bytes else null
                val imagePath = if (currentSettings.vision.saveScreenshots && current != null) imageStorage.save(current, "history") else null
                session.addUser(cleaned, imagePath)
                if (session.input.value.trim() == cleaned) session.setInput("")
                if (currentSettings.saveHistory) ensureConversation(profile?.id ?: 0, cleaned)
                val recent = session.lines.value.takeLast(30)
                val ai = groq.ask(
                    model = profile?.preferredModel?.takeIf { it.isNotBlank() } ?: currentSettings.activeModel,
                    messages = recent,
                    systemPrompt = system,
                    temperature = profile?.temperature ?: currentSettings.temperature,
                    maxTokens = profile?.maxTokens ?: currentSettings.maxTokens,
                    imageBase64 = current?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
                    compareImageBase64 = previous?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                )
                session.addAssistant(ai.text, ai.model, ai.usage, imagePath)
                if (currentSettings.saveHistory) saveLastLines(ai.model, ai.usage.totalTokens)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                session.setError(errorMessage(t))
            } finally {
                session.setBusy(false)
            }
        }
        activeAskJob = job
        job.invokeOnCompletion { if (activeAskJob === job) activeAskJob = null }
        return job
    }

    fun cancelRequest() { activeAskJob?.cancel(); activeAskJob = null; session.setBusy(false) }

    fun retryLast() {
        val prompt = lastPrompt ?: return
        // Hapus pesan user yang gagal supaya tidak dobel saat retry.
        session.removeLastUser()
        ask(prompt, compare = lastCompare)
    }

    private fun promptFor(action: String): String = when (action) {
        "Analyze" -> "Analyze what is happening in this selected screen area. Focus on facts, visible UI, likely state, and practical next steps."
        "Explain" -> "Jelaskan apa yang sedang terjadi di area layar ini dan kenapa hal tersebut terjadi."
        "Read" -> "Read all clearly visible text in this image. Preserve line breaks where useful."
        "Translate" -> "Translate the visible text into Indonesian. Preserve names, numbers, and important formatting."
        "Summarize" -> "Summarize the important information visible in this screen area."
        "Find Error" -> "Find possible errors or abnormal behavior visible in this screen area. Explain evidence and fixes."
        "Extract Text" -> "Extract visible text exactly as readable. Return text only, with sensible line breaks."
        "Describe" -> "Describe the visible screen area precisely and briefly."
        "Help Me" -> "Based on this screen area, tell me what I should do next. Give practical steps and do not invent hidden information."
        else -> action
    }

    fun quickAction(action: String) {
        ask(promptFor(action))
    }

    fun compare() {
        val current = session.currentImage.value
        val previous = session.previousImage.value
        if (current == null || previous == null) {
            session.setError("Compare membutuhkan dua screenshot. Capture dua kali terlebih dahulu.")
            return
        }
        ask("The first image is CURRENT and the second image is PREVIOUS. Compare them and explain what changed, what appeared, what disappeared, and the most important differences.", true)
    }

    /** Auto Analyze sekarang benar-benar tersambung ke setting (sebelumnya switch-nya tidak melakukan apa-apa). */
    fun setAutoAnalyze(enabled: Boolean) {
        autoJob?.cancel()
        autoJob = null
        if (!enabled) return
        autoJob = scope.launch {
            while (isActive) {
                val current = settings.flow.first()
                if (!current.vision.autoAnalyze) break
                if (!session.busy.value && ScreenCaptureService.isRunning) {
                    val region = session.region.value
                    if (captureNow(region, region == null)) {
                        ask(promptFor("Analyze"))?.join()
                    }
                }
                delay(current.vision.interval.millis.coerceAtLeast(2_000))
            }
        }
    }

    fun stop() { autoJob?.cancel(); scope.cancel() }

    private suspend fun ensureConversation(profileId: Long, prompt: String) {
        if (conversationId == null) conversationId = history.createConversation(prompt.take(60), profileId)
        val id = conversationId ?: return
        val latest = session.lines.value.lastOrNull() ?: return
        history.addMessage(id, latest)
    }

    private suspend fun saveLastLines(model: String, tokens: Int) {
        val id = conversationId ?: return
        val last = session.lines.value.takeLast(1).firstOrNull() ?: return
        if (last.role == Role.ASSISTANT) history.addMessage(id, last.copy(model = model, tokenUsage = tokens))
    }

    private fun buildSystemPrompt(profile: AiProfile?, style: ResponseStyle): String {
        val styleText = when (style) {
            ResponseStyle.SHORT -> "Keep the answer concise."
            ResponseStyle.NORMAL -> "Give a clear normal-length answer."
            ResponseStyle.DETAILED -> "Give detailed reasoning and useful context."
            ResponseStyle.TECHNICAL -> "Use precise technical terminology and implementation-level detail when relevant."
            ResponseStyle.STEP_BY_STEP -> "Answer using numbered step-by-step instructions when actions are needed."
        }
        return listOf(profile?.systemPrompt ?: "You are ALF Vision, a helpful screen assistant.", styleText).joinToString(" ")
    }

    private fun errorMessage(t: Throwable): String = when (t) {
        is GroqApiException -> when (t.code) {
            401 -> "Groq API Key belum diatur atau tidak valid."
            403 -> "Groq menolak request ini. Periksa akses atau model."
            429 -> "Rate limit reached. Tunggu beberapa saat atau ganti model."
            500, 502, 503 -> "Groq sedang bermasalah sementara. Coba lagi nanti."
            else -> t.message
        }
        else -> t.message ?: "Terjadi kesalahan."
    }
}
