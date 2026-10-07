package com.alfread.alfvision.data.repository

import android.graphics.Bitmap
import com.alfread.alfvision.core.Constants
import com.alfread.alfvision.core.ResponseStyle
import com.alfread.alfvision.data.network.GroqApiService
import com.alfread.alfvision.data.network.GroqError
import com.alfread.alfvision.data.network.GroqModelInfo
import com.alfread.alfvision.data.network.GroqRequestMessage
import com.alfread.alfvision.data.network.GroqResult
import com.alfread.alfvision.data.prefs.AppPreferences
import com.alfread.alfvision.data.secure.SecureStore
import com.alfread.alfvision.domain.ImageProcessor
import com.alfread.alfvision.domain.PromptBuilder
import kotlinx.coroutines.delay
import kotlin.math.pow

class GroqRepository(
    private val client: GroqApiService,
    private val preferences: AppPreferences,
    private val secureStore: SecureStore,
    private val imageProcessor: ImageProcessor
) {
    suspend fun chat(
        prompt: String,
        systemPrompt: String? = null,
        images: List<Bitmap> = emptyList(),
        model: String? = null,
        style: ResponseStyle? = null,
        temperature: Float? = null,
        maxTokens: Int? = null,
        retries: Int? = null
    ): Result<GroqResultWithMetadata> {
        val settings = preferences.snapshot()
        val selectedModel = model ?: settings.selectedModel.ifBlank { Constants.DEFAULT_MODEL }
        val system = buildString {
            if (!systemPrompt.isNullOrBlank()) append(systemPrompt.trim()).append('\n')
            append(PromptBuilder.styleInstruction(style ?: settings.responseStyle))
        }
        val prepared = images.take(3).map { imageProcessor.prepare(it, settings.captureQuality) }
        val messages = buildList {
            if (system.isNotBlank()) add(GroqRequestMessage("system", system))
            add(GroqRequestMessage("user", prompt, prepared.map { it.dataUri }))
        }

        var attempt = 0
        val maxRetries = (retries ?: settings.retryCount).coerceIn(0, 5)
        try {
            while (true) {
                try {
                    val result = client.chat(
                        model = selectedModel,
                        messages = messages,
                        temperature = temperature ?: settings.temperature,
                        maxTokens = maxTokens ?: settings.maxTokens
                    )
                    preferences.setLastSuccessfulRequest(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
                    preferences.setLastError("")
                    return Result.success(GroqResultWithMetadata(result, prepared.map { it.bytes.size }))
                } catch (error: GroqError.RateLimit) {
                    if (attempt >= maxRetries) {
                        preferences.setLastError(error.message ?: "Rate limit reached.")
                        return Result.failure(error)
                    }
                    delay((500L * 2.0.pow(attempt.toDouble())).toLong().coerceAtMost(4000L))
                    attempt++
                } catch (error: GroqError.Server) {
                    if (attempt >= maxRetries) {
                        preferences.setLastError(error.message ?: "Server error")
                        return Result.failure(error)
                    }
                    delay((500L * 2.0.pow(attempt.toDouble())).toLong().coerceAtMost(4000L))
                    attempt++
                } catch (error: GroqError) {
                    preferences.setLastError(error.message ?: "Request failed")
                    return Result.failure(error)
                }
            }
        } finally {
            prepared.forEach { preparedImage ->
                if (!preparedImage.bitmap.isRecycled) preparedImage.bitmap.recycle()
            }
        }
    }

    suspend fun testConnection(): Result<String> {
        return runCatching {
            val models = client.listModels()
            if (models.none { it.id == Constants.DEFAULT_MODEL }) {
                Result.failure<String>(GroqError.ModelUnavailable("Model vision default tidak ditemukan di akun ini."))
            } else {
                Result.success("Connected • ${models.size} active models")
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun listModels(): Result<List<GroqModelInfo>> = runCatching { client.listModels() }
}

data class GroqResultWithMetadata(
    val result: GroqResult,
    val imageBytes: List<Int>
)

