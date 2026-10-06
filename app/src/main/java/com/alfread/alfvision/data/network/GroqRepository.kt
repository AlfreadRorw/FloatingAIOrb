package com.alfread.alfvision.data.network

import com.alfread.alfvision.core.model.*
import com.alfread.alfvision.core.util.SecureStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class GroqRepository(
    private val secureStore: SecureStore,
    private val clientProvider: () -> okhttp3.OkHttpClient,
    private val timeoutProvider: () -> Long,
    private val retryProvider: () -> Int
) {
    private fun service() = GroqApiService(clientProvider)

    suspend fun ask(
        model: String,
        messages: List<ChatLine>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        imageBase64: String? = null,
        compareImageBase64: String? = null
    ): AiResult = withContext(Dispatchers.IO) {
        val apiKey = secureStore.getApiKey()?.takeIf { it.isNotBlank() }
            ?: throw GroqApiException(401, "Groq API Key belum diatur.")
        var last: GroqApiException? = null
        val attempts = retryProvider().coerceIn(0, 4) + 1
        repeat(attempts) { attempt ->
            try {
                val (text, usage) = service().chat(apiKey, model, messages, systemPrompt, temperature, maxTokens, imageBase64, compareImageBase64)
                return@withContext AiResult(text, model, ModelUsage(usage[0], usage[1], usage[2]))
            } catch (e: CancellationException) {
                throw e
            } catch (e: GroqApiException) {
                last = e
                if (e.code == 429 && attempt < attempts - 1) {
                    delay((600L * (1L shl attempt)).coerceAtMost(4_000L))
                } else if (e.code in 500..599 && attempt < attempts - 1) {
                    delay((400L * (1L shl attempt)).coerceAtMost(3_000L))
                } else break
            } catch (t: Throwable) {
                throw GroqApiException(-1, "Network error. Check your internet connection.")
            }
        }
        throw (last ?: GroqApiException(-1, "Groq request failed."))
    }

    suspend fun listModels(): List<GroqModel> = withContext(Dispatchers.IO) {
        val key = secureStore.getApiKey()?.takeIf { it.isNotBlank() }
            ?: throw GroqApiException(401, "Groq API Key belum diatur.")
        service().test(key)
    }
}
