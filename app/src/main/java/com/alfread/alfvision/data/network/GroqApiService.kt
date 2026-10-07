package com.alfread.alfvision.data.network

/**
 * Small service-facing contract over the HTTP client. Keeping this type separate makes
 * the repository independent from the concrete transport implementation.
 */
class GroqApiService(private val client: GroqApiClient) {
    suspend fun chat(
        model: String,
        messages: List<GroqRequestMessage>,
        temperature: Float,
        maxTokens: Int
    ): GroqResult = client.chat(model, messages, temperature, maxTokens)

    suspend fun listModels(): List<GroqModelInfo> = client.listModels()
}
