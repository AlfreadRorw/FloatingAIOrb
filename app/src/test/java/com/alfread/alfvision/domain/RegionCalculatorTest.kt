package com.alfread.alfvision.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionCalculatorTest {
    @Test
    fun constrainStaysInsideBounds() {
        val result = RegionCalculator.constrain(-50, -30, 1200, 1200, 1080, 1920)
        assertEquals(0, result.x)
        assertEquals(0, result.y)
        assertEquals(1080, result.width)
        assertEquals(1200, result.height)
    }

    @Test
    fun scaleRegionToImage() {
        val result = RegionCalculator.scaleToImage(
            com.alfread.alfvision.core.RegionRect(100, 200, 300, 400, 1000, 2000),
            500,
            1000
        )
        assertEquals(50, result.x)
        assertEquals(100, result.y)
        assertEquals(150, result.width)
        assertEquals(200, result.height)
    }
}
