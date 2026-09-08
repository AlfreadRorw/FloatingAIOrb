package com.example.floatingaiorb

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object AIClient {
    const val DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    const val DEFAULT_MODEL = "qwen/qwen3.6-27b"

    data class Result(val text: String, val rawStatus: Int)

    fun chat(apiKey: String, endpoint: String, model: String, userText: String, image: Bitmap? = null): Result {
        require(apiKey.isNotBlank()) { "API key belum diisi." }
        val selectedModel = model.ifBlank { DEFAULT_MODEL }
        val content = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", userText.trim()))
            if (image != null) {
                put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", bitmapDataUrl(image))))
            }
        }
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            put(JSONObject().put("role", "user").put("content", content))
        }
        val payload = JSONObject().apply {
            put("model", selectedModel)
            put("temperature", 0.65)
            put("max_tokens", if (image != null) 420 else 260)
            if (selectedModel.startsWith("qwen/")) {
                put("reasoning_effort", "none")
                put("reasoning_format", "hidden")
            }
            put("messages", messages)
        }

        val connection = (URL(endpoint.ifBlank { DEFAULT_ENDPOINT }).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use(BufferedReader::readText) }.orEmpty()
            if (status !in 200..299) {
                val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(
                    when (status) {
                        401 -> "API key ditolak. Cek key di SETUP."
                        429 -> "Batas penggunaan AI tercapai. Tunggu sebentar lalu coba lagi."
                        else -> msg?.takeIf { it.isNotBlank() } ?: "HTTP $status: ${body.take(240)}"
                    }
                )
            }
            val raw = JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            Result(cleanReply(raw), status)
        } finally {
            connection.disconnect()
        }
    }

    private val SYSTEM_PROMPT = """
        Kamu adalah Floating AI Orb, asisten Android yang ringkas, natural, hangat, dan pintar.
        Jawab memakai bahasa pengguna. Jangan pernah menampilkan proses berpikir internal, tag <think>, reasoning, atau catatan internal.
        Jangan menulis markdown mentah yang tidak perlu. Gunakan paragraf pendek dan bullet sederhana hanya bila membantu.
        Jika menerima foto atau screenshot, jelaskan hanya hal yang benar-benar terlihat. Jangan mengarang detail yang tidak tampak.
        Untuk percakapan biasa, utamakan jawaban langsung dan tidak bertele-tele.
    """.trimIndent()

    fun cleanReply(raw: String): String {
        var text = raw.trim()
        text = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE).replace(text, "")
        val hidden = text.indexOf("<think>", ignoreCase = true)
        if (hidden >= 0) text = text.substring(0, hidden)
        text = text.replace("</think>", "", ignoreCase = true)
        text = text.replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")
        text = text.replace("**", "").replace("__", "").replace("```", "")
        text = text.replace(Regex("\\n{3,}"), "\\n\\n")
        return text.trim().ifBlank { "Aku belum mendapat jawaban. Coba ulangi ya." }
    }

    fun cleanForSpeech(raw: String): String = cleanReply(raw)

    private fun bitmapDataUrl(bitmap: Bitmap): String {
        val scaled = scale(bitmap, 1024)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 76, out)
        if (scaled !== bitmap) scaled.recycle()
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun scale(bitmap: Bitmap, maxSide: Int): Bitmap {
        val side = maxOf(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
    }
}
