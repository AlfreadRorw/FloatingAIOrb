package com.alfread.alfvision

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object GroqClient {
    private val executor = Executors.newCachedThreadPool()

    fun ask(
        apiKey: String,
        model: String,
        systemPrompt: String,
        question: String,
        bitmap: Bitmap?,
        callback: (Result<String>) -> Unit
    ) {
        executor.execute {
            try {
                val response = request(apiKey, model, systemPrompt, question, bitmap)
                callback(Result.success(response))
            } catch (t: Throwable) {
                callback(Result.failure(t))
            }
        }
    }

    private fun request(apiKey: String, model: String, systemPrompt: String, question: String, bitmap: Bitmap?): String {
        val url = URL("https://api.groq.com/openai/v1/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 25_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.4)
            .put("max_completion_tokens", 1400)

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))

        if (bitmap == null) {
            messages.put(JSONObject().put("role", "user").put("content", question))
        } else {
            val imageData = encodeJpeg(bitmap)
            val content = JSONArray()
                .put(JSONObject().put("type", "text").put("text", question))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageData")))
            messages.put(JSONObject().put("role", "user").put("content", content))
        }
        body.put("messages", messages)

        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("Groq $code: ${parseError(text)}")
        }
        return JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
    }

    private fun parseError(text: String): String = runCatching { JSONObject(text).getJSONObject("error").optString("message") }.getOrDefault(text.take(500))

    private fun encodeJpeg(bitmap: Bitmap): String {
        val max = 1600
        val scale = minOf(1f, max.toFloat() / maxOf(bitmap.width, bitmap.height))
        val b = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width*scale).toInt().coerceAtLeast(1), (bitmap.height*scale).toInt().coerceAtLeast(1), true) else bitmap
        val out = java.io.ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 78, out)
        if (b !== bitmap) b.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
