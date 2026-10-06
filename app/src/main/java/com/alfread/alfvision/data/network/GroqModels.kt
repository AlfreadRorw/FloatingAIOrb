package com.alfread.alfvision.data.network

import org.json.JSONArray
import org.json.JSONObject

internal object GroqJsonParser {
    fun parseCompletion(json: JSONObject): Pair<String, IntArray> {
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: throw GroqApiException(500, "Groq returned no choices.")
        val message = choice.optJSONObject("message")
            ?: throw GroqApiException(500, "Groq returned an invalid message.")
        val content = message.optString("content", "").trim()
        if (content.isEmpty()) throw GroqApiException(500, "Groq returned an empty response.")
        val usage = json.optJSONObject("usage")
        return content to intArrayOf(
            usage?.optInt("prompt_tokens", 0) ?: 0,
            usage?.optInt("completion_tokens", 0) ?: 0,
            usage?.optInt("total_tokens", 0) ?: 0
        )
    }

    fun parseModels(json: JSONObject): List<com.alfread.alfvision.core.model.GroqModel> {
        val data: JSONArray = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                val context = item.optLong("context_window", 0L).takeIf { it > 0 }
                val maxCompletion = item.optLong("max_completion_tokens", 0L).takeIf { it > 0 }
                add(
                    com.alfread.alfvision.core.model.GroqModel(
                        id = id,
                        active = item.optBoolean("active", true),
                        ownedBy = item.optString("owned_by", null),
                        contextWindow = context,
                        maxCompletionTokens = maxCompletion,
                        supportsVision = id == "qwen/qwen3.8-27b" || id.contains("vision", true) || id.contains("qwen", true) && id.contains("27b", true)
                    )
                )
            }
        }.filter { it.active }
    }
}

data class GroqErrorInfo(val code: Int, val message: String)

class GroqApiException(val code: Int, override val message: String) : Exception(message)
