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

    fun chat(
        apiKey: String,
        endpoint: String,
        model: String,
        userText: String,
        image: Bitmap? = null
    ): Result {
        require(apiKey.isNotBlank()) { "API key belum diisi." }
        val payload = JSONObject().apply {
            put("model", model.ifBlank { DEFAULT_MODEL })
            put("temperature", 0.7)
            put("max_tokens", 800)
            val content = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", userText))
                if (image != null) {
                    put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", bitmapDataUrl(image))))
                }
            }
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", "Kamu adalah Floating AI Orb, asisten Android yang ramah, ringkas, jelas, dan membantu. Jika menerima gambar, jelaskan apa yang terlihat tanpa mengarang detail yang tidak tampak."))
            messages.put(JSONObject().put("role", "user").put("content", content))
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
            val body = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (status !in 200..299) {
                val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(msg?.takeIf { it.isNotBlank() } ?: "HTTP $status: ${body.take(300)}")
            }
            val json = JSONObject(body)
            val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?: "AI tidak mengembalikan jawaban."
            Result(text, status)
        } finally {
            connection.disconnect()
        }
    }

    fun bitmapDataUrl(bitmap: Bitmap): String {
        val scaled = scale(bitmap, 1024)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
        if (scaled !== bitmap) scaled.recycle()
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun scale(bitmap: Bitmap, maxSide: Int): Bitmap {
        val side = maxOf(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }
}
