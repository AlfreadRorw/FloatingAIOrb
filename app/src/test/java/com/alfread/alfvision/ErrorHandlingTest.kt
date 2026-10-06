package com.alfread.alfvision

import com.alfread.alfvision.data.network.GroqApiException
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorHandlingTest {
    @Test fun rateLimitCodeIsPreserved() {
        assertEquals(429, GroqApiException(429, "Rate limit reached").code)
    }
}
