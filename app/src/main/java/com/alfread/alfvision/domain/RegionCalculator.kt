package com.alfread.alfvision.domain

import com.alfread.alfvision.core.RegionRect
import kotlin.math.max
import kotlin.math.min

object RegionCalculator {
    fun constrain(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        minSize: Int = 80
    ): RegionRect {
        val safeW = max(minSize, min(width, sourceWidth))
        val safeH = max(minSize, min(height, sourceHeight))
        val safeX = x.coerceIn(0, max(0, sourceWidth - safeW))
        val safeY = y.coerceIn(0, max(0, sourceHeight - safeH))
        return RegionRect(safeX, safeY, safeW, safeH, sourceWidth, sourceHeight)
    }

    fun scaleToImage(region: RegionRect, imageWidth: Int, imageHeight: Int): RegionRect {
        val sx = imageWidth.toFloat() / region.sourceWidth.coerceAtLeast(1)
        val sy = imageHeight.toFloat() / region.sourceHeight.coerceAtLeast(1)
        return RegionRect(
            x = (region.x * sx).toInt(),
            y = (region.y * sy).toInt(),
            width = (region.width * sx).toInt().coerceAtLeast(1),
            height = (region.height * sy).toInt().coerceAtLeast(1),
            sourceWidth = imageWidth,
            sourceHeight = imageHeight,
            rotation = region.rotation,
            displayId = region.displayId
        )
    }
}
