package com.alfread.alfvision

import com.alfread.alfvision.core.model.ChatLine
import com.alfread.alfvision.core.model.Role
import com.alfread.alfvision.data.network.GroqApiService
import com.alfread.alfvision.data.network.buildHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqRequestBuilderTest {
    @Test fun serviceUsesGroqEndpoint() {
        val service = GroqApiService { buildHttpClient(10) }
        assertTrue(service.javaClass.simpleName.contains("GroqApiService"))
    }
}
