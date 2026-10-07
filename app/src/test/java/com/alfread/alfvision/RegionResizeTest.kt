package com.alfread.alfvision

import androidx.compose.ui.graphics.Color
import com.alfread.alfvision.core.model.Accent
import com.alfread.alfvision.core.model.Region
import com.alfread.alfvision.service.REGION_BR
import com.alfread.alfvision.service.REGION_MOVE
import com.alfread.alfvision.service.REGION_TL
import com.alfread.alfvision.service.hitTestRegion
import com.alfread.alfvision.service.resizeRegion
import com.alfread.alfvision.ui.theme.color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionResizeTest {
    private val region = Region(100, 100, 400, 400, 1000, 2000)

    @Test fun moveIsClampedInsideScreen() {
        val moved = resizeRegion(region, REGION_MOVE, 5000f, -5000f, 1000, 2000)
        assertEquals(600, moved.x)
        assertEquals(0, moved.y)
        assertEquals(400, moved.width)
    }

    @Test fun resizeKeepsMinimumSize() {
        val shrunk = resizeRegion(region, REGION_BR, -5000f, -5000f, 1000, 2000)
        assertTrue(shrunk.width >= 80 && shrunk.height >= 80)
    }

    @Test fun topLeftResizeChangesOrigin() {
        val resized = resizeRegion(region, REGION_TL, -50f, -50f, 1000, 2000)
        assertEquals(50, resized.x)
        assertEquals(450, resized.width)
    }

    @Test fun hitTestFindsCornerAndBody() {
        assertEquals(REGION_TL, hitTestRegion(region, 105f, 105f, 30f))
        assertEquals(REGION_MOVE, hitTestRegion(region, 300f, 300f, 30f))
    }

    @Test fun accentColorIsValidArgb() {
        // Regresi FC: Color(ULong) memakai color space invalid dan crash saat tema dibuat.
        val color: Color = Accent.PURPLE.color()
        assertEquals(1f, color.alpha, 0.01f)
        assertEquals(0x7B / 255f, color.red, 0.01f)
    }
}
