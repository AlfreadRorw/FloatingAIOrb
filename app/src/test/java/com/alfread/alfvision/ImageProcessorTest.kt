package com.alfread.alfvision

import com.alfread.alfvision.core.model.Region
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageProcessorTest {
    @Test fun regionClampsToImageBounds() {
        val result = Region(950, 950, 200, 200, 1000, 1000).normalized(1000, 1000)
        assertEquals(50, result.width)
        assertEquals(50, result.height)
    }
}
