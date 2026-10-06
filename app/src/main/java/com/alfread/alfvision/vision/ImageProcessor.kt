package com.alfread.alfvision.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.alfread.alfvision.core.model.ImagePayload
import com.alfread.alfvision.core.model.Region
import java.io.ByteArrayOutputStream

class ImageProcessor {
    fun decode(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    fun cropRegion(source: Bitmap, region: Region): Bitmap {
        val normalized = region.normalized(source.width, source.height)
        return Bitmap.createBitmap(source, normalized.x, normalized.y, normalized.width, normalized.height)
    }

    fun resizeForUpload(bitmap: Bitmap, maxDimension: Int = 1600): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest.toFloat()
        val matrix = Matrix().apply { setScale(scale, scale) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun encodeJpeg(bitmap: Bitmap, quality: Int, maxBytes: Int): ImagePayload {
        var working = resizeForUpload(bitmap)
        var q = quality.coerceIn(40, 95)
        var encoded = compress(working, q)
        while (encoded.size > maxBytes && (q > 50 || maxOf(working.width, working.height) > 900)) {
            if (q > 50) q -= 5
            else working = resizeForUpload(working, maxOf(900, maxOf(working.width, working.height) * 3 / 4))
            encoded = compress(working, q)
        }
        return ImagePayload(encoded, "image/jpeg", working.width, working.height, encoded.size)
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun addSimpleAnnotation(base: Bitmap, rects: List<Rect>, text: String?): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (out.width.coerceAtMost(out.height) * 0.006f).coerceAtLeast(3f)
            color = android.graphics.Color.RED
        }
        rects.forEach { canvas.drawRect(it, paint) }
        if (!text.isNullOrBlank()) {
            paint.style = Paint.Style.FILL
            paint.textSize = (out.width * 0.035f).coerceAtLeast(28f)
            canvas.drawText(text, 24f, paint.textSize + 24f, paint)
        }
        return out
    }
}
