package com.alfread.alfvision.domain

import android.graphics.Bitmap
import com.alfread.alfvision.core.RegionRect

class VisionAnalyzer(private val imageProcessor: ImageProcessor) {
    fun prepareRegion(fullFrame: Bitmap, region: RegionRect?): Bitmap {
        return if (region == null) fullFrame else imageProcessor.crop(fullFrame, region)
    }

    fun prepareForUpload(bitmap: Bitmap, quality: Int): PreparedImage = imageProcessor.prepare(bitmap, quality)
}
