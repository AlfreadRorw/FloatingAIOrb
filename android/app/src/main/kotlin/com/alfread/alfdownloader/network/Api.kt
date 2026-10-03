package com.alfread.alfdownloader.network

import com.alfread.alfdownloader.model.*
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Api(private val baseUrl: String = "http://127.0.0.1:8080") {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun request(method: String, path: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 2500
        conn.readTimeout = 5000
        conn.setRequestProperty("Accept", "application/json")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) error(text.ifBlank { "HTTP $code" })
        text
    }

    suspend fun health(): HealthResponse = json.decodeFromString(request("GET", "/api/health"))

    suspend fun info(url: String): MediaInfo {
        val encoded = URLEncoder.encode(url, "UTF-8")
        return json.decodeFromString(request("GET", "/api/info?url=$encoded"))
    }

    suspend fun createJob(url: String, quality: String): CreateJobResponse {
        val body = json.encodeToString(CreateJobRequest.serializer(), CreateJobRequest(url, quality))
        return json.decodeFromString(request("POST", "/api/jobs", body))
    }

    suspend fun job(id: String): Job = json.decodeFromString(request("GET", "/api/jobs/$id"))

    suspend fun jobs(): List<Job> = json.decodeFromString(request("GET", "/api/jobs"))

    suspend fun cancel(id: String) { request("POST", "/api/jobs/$id/cancel") }
}
