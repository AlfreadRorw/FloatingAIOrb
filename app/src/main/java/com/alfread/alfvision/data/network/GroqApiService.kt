package com.alfread.alfvision.data.network

import com.alfread.alfvision.core.model.ChatLine
import com.alfread.alfvision.core.model.Role
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroqApiService(
    private val clientProvider: () -> OkHttpClient
) {
    private val base = "https://api.groq.com/openai/v1"

    fun chat(
        apiKey: String,
        model: String,
        messages: List<ChatLine>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        currentImageBase64: String? = null,
        compareImageBase64: String? = null
    ): Pair<String, IntArray> {
        val contentMessages = JSONArray()
        contentMessages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        messages.forEachIndexed { index, line ->
            val role = when (line.role) {
                Role.USER -> "user"
                Role.ASSISTANT -> "assistant"
                Role.SYSTEM -> "system"
            }
            val hasImage = role == "user" && index == messages.lastIndex && (currentImageBase64 != null || compareImageBase64 != null)
            val message = JSONObject().put("role", role)
            if (hasImage) {
                val parts = JSONArray()
                parts.put(JSONObject().put("type", "text").put("text", line.content))
                currentImageBase64?.let { parts.put(imagePart(it)) }
                compareImageBase64?.let { parts.put(imagePart(it)) }
                message.put("content", parts)
            } else {
                message.put("content", line.content)
            }
            contentMessages.put(message)
        }

        val body = JSONObject()
            .put("model", model)
            .put("messages", contentMessages)
            .put("temperature", temperature.toDouble().coerceIn(0.0, 2.0))
            .put("max_completion_tokens", maxTokens.coerceIn(1, 16384))
            .put("stream", false)

        val request = Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        clientProvider().newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw parseError(response.code, raw)
            return GroqJsonParser.parseCompletion(JSONObject(raw))
        }
    }

    fun test(apiKey: String): List<com.alfread.alfvision.core.model.GroqModel> {
        val request = Request.Builder()
            .url("$base/models")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .get()
            .build()
        clientProvider().newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw parseError(response.code, raw)
            return GroqJsonParser.parseModels(JSONObject(raw))
        }
    }

    private fun imagePart(base64: String): JSONObject = JSONObject().apply {
        put("type", "image_url")
        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64"))
    }

    private fun parseError(code: Int, raw: String): GroqApiException {
        val message = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrNull().orEmpty().ifBlank {
            when (code) {
                401 -> "Groq API key is invalid."
                403 -> "Groq rejected this request."
                429 -> "Rate limit reached."
                500, 502, 503 -> "Groq service is temporarily unavailable."
                else -> "Groq request failed (HTTP $code)."
            }
        }
        return GroqApiException(code, message)
    }
}

fun buildHttpClient(timeoutSeconds: Long): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
    .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
    .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
    .callTimeout(timeoutSeconds + 10, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
