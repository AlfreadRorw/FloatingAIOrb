package com.alfread.alfvision.data

import com.alfread.alfvision.data.network.GroqJsonBuilder
import com.alfread.alfvision.data.network.GroqRequestMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqJsonBuilderTest {
    @Test fun multimodalBodyUsesImageUrlContent() {
        val body = GroqJsonBuilder.chatBody(
            "qwen/qwen3.8-27b",
            listOf(GroqRequestMessage("user", "what is this?", listOf("data:image/jpeg;base64,abc"))),
            0.7f,
            1024
        )
        val root = JSONObject(body)
        val content = root.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("image_url", content.getJSONObject(1).getString("type"))
        assertTrue(root.getString("model").contains("qwen"))
    }
}
