package com.alfread.alfdownloader.network

import com.alfread.alfdownloader.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Api(baseUrl: String = "http://127.0.0.1:8080") {
    private val baseUrl = baseUrl.trim().trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    private fun errorText(text: String, code: Int): String {
        val parsed = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
        return parsed?.takeIf { it.isNotBlank() } ?: text.take(300).ifBlank { "HTTP $code" }
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        readTimeout: Int = 6000
    ): String = withContext(Dispatchers.IO) {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 2500
            conn.readTimeout = readTimeout
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error(errorText(text, code))
            text
        } finally {
            conn.disconnect()
        }
    }

    suspend fun health(): HealthResponse = json.decodeFromString(request("GET", "/api/health", readTimeout = 3000))

    suspend fun info(url: String, playlist: Boolean): MediaInfo {
        val encoded = URLEncoder.encode(url, "UTF-8")
        val extra = if (playlist) "&playlist=1" else ""
        return json.decodeFromString(request("GET", "/api/info?url=$encoded$extra", readTimeout = 60000))
    }

    suspend fun createJob(req: CreateJobRequest): CreateJobResponse {
        val body = json.encodeToString(CreateJobRequest.serializer(), req)
        return json.decodeFromString(request("POST", "/api/jobs", body, 10000))
    }

    suspend fun jobs(): List<Job> =
        json.decodeFromString(ListSerializer(Job.serializer()), request("GET", "/api/jobs"))

    suspend fun cancel(id: String) { request("POST", "/api/jobs/$id/cancel") }
    suspend fun retry(id: String) { request("POST", "/api/jobs/$id/retry") }
    suspend fun delete(id: String) { request("DELETE", "/api/jobs/$id") }
    suspend fun clearFinished() { request("POST", "/api/jobs/clear") }
    suspend fun shutdown() { request("POST", "/api/shutdown") }
    suspend fun updateYtdlp() { request("POST", "/api/update", readTimeout = 10000) }

    suspend fun setConcurrent(n: Int) {
        request("POST", "/api/config", json.encodeToString(ConfigRequest.serializer(), ConfigRequest(n)))
    }
}
