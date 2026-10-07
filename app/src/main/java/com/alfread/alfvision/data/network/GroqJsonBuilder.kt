package com.alfread.alfvision.data.network

import org.json.JSONArray
import org.json.JSONObject

object GroqJsonBuilder {
    fun chatBody(
        model: String,
        messages: List<GroqRequestMessage>,
        temperature: Float,
        maxTokens: Int
    ): String {
        return JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().apply {
                        put("role", message.role)
                        if (message.imageDataUris.isEmpty()) {
                            put("content", message.text)
                        } else {
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", message.text)
                                })
                                message.imageDataUris.take(3).forEach { uri ->
                                    put(JSONObject().apply {
                                        put("type", "image_url")
                                        put("image_url", JSONObject().put("url", uri))
                                    })
                                }
                            })
                        }
                    })
                }
            })
            put("temperature", temperature.coerceIn(0f, 2f).toDouble())
            put("max_completion_tokens", maxTokens.coerceIn(128, 16384))
            put("stream", false)
        }.toString()
    }
}
