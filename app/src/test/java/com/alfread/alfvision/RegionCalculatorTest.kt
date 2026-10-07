package com.alfread.alfvision

import com.alfread.alfvision.core.model.Region
import org.junit.Assert.assertEquals
import org.junit.Test

class RegionCalculatorTest {
    @Test fun normalizedRegionScalesCoordinates() {
        val source = Region(100, 200, 400, 300, 1000, 2000)
        val result = source.normalized(2000, 4000)
        assertEquals(200, result.x)
        assertEquals(400, result.y)
        assertEquals(800, result.width)
        assertEquals(600, result.height)
    }
}
