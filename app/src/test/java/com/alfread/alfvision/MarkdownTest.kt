package com.alfread.alfvision

import androidx.compose.ui.graphics.Color
import com.alfread.alfvision.ui.components.renderMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MarkdownTest {
    @Test fun boldMarkersAreRemoved() {
        val out = renderMarkdown("**Jawaban:** B", Color.Gray)
        assertEquals("Jawaban: B", out.text)
    }

    @Test fun bulletsBecomeDots() {
        val out = renderMarkdown("*   **Adjust Settings:** tap toggles\n* Second", Color.Gray)
        assertEquals("• Adjust Settings: tap toggles\n• Second", out.text)
    }

    @Test fun headingsAndFencesHaveNoSymbols() {
        val out = renderMarkdown("## Judul\n```\nx = 1\n```\n`kode` biasa", Color.Gray)
        assertFalse(out.text.contains("#") || out.text.contains("`"))
    }
}
