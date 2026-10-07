package com.alfread.alfvision.data.network

import com.alfread.alfvision.core.Constants
import com.alfread.alfvision.data.prefs.AppPreferences
import com.alfread.alfvision.data.secure.SecureStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Callback
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class GroqApiClient(
    private val preferences: AppPreferences,
    private val secureStore: SecureStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        model: String,
        messages: List<GroqRequestMessage>,
        temperature: Float,
        maxTokens: Int
    ): GroqResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = secureStore.getApiKey() ?: throw GroqError.MissingApiKey()
        val body = GroqJsonBuilder.chatBody(
            model = model,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens
        )

        val request = Request.Builder()
            .url(Constants.GROQ_BASE_URL + "chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val timeoutSeconds = preferences.snapshot().timeoutSeconds

        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            call.timeout().timeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(GroqError.Network("Internet tidak tersedia atau koneksi ke Groq gagal."))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use { closedResponse ->
                        try {
                            val raw = closedResponse.body?.string().orEmpty()
                            if (!closedResponse.isSuccessful) {
                                throw when (closedResponse.code) {
                                    401, 403 -> GroqError.Auth(closedResponse.code, "Groq API Key tidak valid atau akses ditolak.")
                                    429 -> GroqError.RateLimit("Rate limit reached.")
                                    500, 502, 503 -> GroqError.Server(closedResponse.code, "Groq sedang bermasalah. Coba lagi nanti.")
                                    404 -> GroqError.ModelUnavailable("Model tidak tersedia. Pilih model lain.")
                                    else -> GroqError.Server(closedResponse.code, "Groq mengembalikan error ${closedResponse.code}.")
                                }
                            }
                            continuation.resume(parseResult(raw))
                        } catch (error: GroqError) {
                            if (!continuation.isCancelled) continuation.resumeWithException(error)
                        } catch (_: Exception) {
                            if (!continuation.isCancelled) continuation.resumeWithException(GroqError.InvalidResponse("Respons Groq tidak dapat diproses."))
                        }
                    }
                }
            })
        }
    }

    suspend fun listModels(): List<GroqModelInfo> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = secureStore.getApiKey() ?: throw GroqError.MissingApiKey()
        val request = Request.Builder()
            .url(Constants.GROQ_BASE_URL + "models")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .get()
            .build()
        try {
            val call = client.newCall(request)
            call.timeout().timeout(30, TimeUnit.SECONDS)
            call.execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw when (response.code) {
                        401, 403 -> GroqError.Auth(response.code, "Groq API Key tidak valid atau akses ditolak.")
                        429 -> GroqError.RateLimit("Rate limit reached.")
                        else -> GroqError.Server(response.code, "Gagal mengambil daftar model.")
                    }
                }
                val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        add(
                            GroqModelInfo(
                                id = item.optString("id"),
                                active = item.optBoolean("active", true),
                                contextWindow = item.optInt("context_window", 0),
                                maxCompletionTokens = item.optInt("max_completion_tokens", 0)
                            )
                        )
                    }
                }.filter { it.id.isNotBlank() && it.active }.sortedBy { it.id }
            }
        } catch (e: GroqError) {
            throw e
        } catch (e: IOException) {
            throw GroqError.Network("Internet tidak tersedia atau koneksi ke Groq gagal.")
        } catch (e: Exception) {
            throw GroqError.InvalidResponse("Daftar model Groq tidak dapat diproses.")
        }
    }

    private fun parseResult(raw: String): GroqResult {
        val root = JSONObject(raw)
        val choices = root.optJSONArray("choices") ?: throw GroqError.InvalidResponse("Respons tidak memiliki choices.")
        if (choices.length() == 0) throw GroqError.InvalidResponse("Groq tidak mengembalikan jawaban.")
        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw GroqError.InvalidResponse("Respons Groq tidak memiliki message.")
        val content = message.optString("content").trim()
        if (content.isBlank()) throw GroqError.InvalidResponse("Jawaban AI kosong.")
        val usage = root.optJSONObject("usage")
        return GroqResult(
            text = content,
            model = root.optString("model"),
            inputTokens = usage?.optInt("prompt_tokens"),
            outputTokens = usage?.optInt("completion_tokens")
        )
    }
}
