package com.alfread.alfvision.data.network

data class GroqRequestMessage(
    val role: String,
    val text: String,
    val imageDataUris: List<String> = emptyList()
)

data class GroqResult(
    val text: String,
    val model: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null
)

data class GroqModelInfo(
    val id: String,
    val active: Boolean,
    val contextWindow: Int,
    val maxCompletionTokens: Int
)

sealed class GroqError(message: String, val code: Int? = null) : Exception(message) {
    class MissingApiKey : GroqError("Groq API Key belum diatur.")
    class Auth(code: Int, message: String) : GroqError(message, code)
    class RateLimit(message: String) : GroqError(message, 429)
    class Server(code: Int, message: String) : GroqError(message, code)
    class Network(message: String) : GroqError(message)
    class InvalidResponse(message: String) : GroqError(message)
    class ModelUnavailable(message: String) : GroqError(message)
}
