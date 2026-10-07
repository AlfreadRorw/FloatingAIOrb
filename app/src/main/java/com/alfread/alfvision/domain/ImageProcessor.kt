package com.alfread.alfvision.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Base64
import com.alfread.alfvision.core.Constants
import com.alfread.alfvision.core.RegionRect
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

class ImageProcessor(private val context: Context) {
    fun crop(bitmap: Bitmap, region: RegionRect): Bitmap {
        val scaled = RegionCalculator.scaleToImage(region, bitmap.width, bitmap.height)
        val left = scaled.x.coerceIn(0, bitmap.width - 1)
        val top = scaled.y.coerceIn(0, bitmap.height - 1)
        val right = (left + scaled.width).coerceIn(left + 1, bitmap.width)
        val bottom = (top + scaled.height).coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    fun prepare(bitmap: Bitmap, quality: Int): PreparedImage {
        var working = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        working = downsample(working, Constants.MAX_IMAGE_DIMENSION)
        var jpegQuality = quality.coerceIn(50, 100)
        var bytes = encodeJpeg(working, jpegQuality)
        while (bytes.size > Constants.MAX_IMAGE_BYTES && jpegQuality > 50) {
            jpegQuality -= 8
            bytes = encodeJpeg(working, jpegQuality)
        }
        if (bytes.size > Constants.MAX_IMAGE_BYTES) {
            val smaller = downsample(working, 1100)
            working.recycle()
            working = smaller
            jpegQuality = 62
            bytes = encodeJpeg(working, jpegQuality)
        }
        val dataUri = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        return PreparedImage(working, bytes, dataUri)
    }

    fun annotate(
        source: Bitmap,
        actions: List<AnnotationAction>
    ): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(3f, source.width / 180f)
            setColor(android.graphics.Color.rgb(110, 168, 255))
        }
        actions.forEach { action ->
            when (action) {
                is AnnotationAction.Rect -> canvas.drawRect(action.rect, strokePaint)
                is AnnotationAction.Circle -> canvas.drawOval(action.rect, strokePaint)
                is AnnotationAction.Line -> canvas.drawLine(action.x1, action.y1, action.x2, action.y2, strokePaint)
                is AnnotationAction.Arrow -> drawArrow(canvas, strokePaint, action)
                is AnnotationAction.Text -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = max(24f, source.width / 28f)
                        setColor(android.graphics.Color.rgb(255, 255, 255))
                        style = Paint.Style.FILL
                    }
                    canvas.drawText(action.text, action.x, action.y, paint)
                }
                is AnnotationAction.Blur -> blurRegion(output, action.rect)
            }
        }
        return output
    }

    fun cropByRect(source: Bitmap, rect: RectF): Bitmap {
        val left = rect.left.coerceIn(0f, source.width - 1f).toInt()
        val top = rect.top.coerceIn(0f, source.height - 1f).toInt()
        val right = rect.right.coerceIn((left + 1).toFloat(), source.width.toFloat()).toInt()
        val bottom = rect.bottom.coerceIn((top + 1).toFloat(), source.height.toFloat()).toInt()
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    private fun downsample(source: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = max(source.width, source.height)
        if (maxSide <= maxDimension) return source
        val scale = maxDimension.toFloat() / maxSide
        val dst = Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (dst !== source) source.recycle()
        return dst
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        return output.toByteArray()
    }

    private fun drawArrow(canvas: Canvas, paint: Paint, action: AnnotationAction.Arrow) {
        canvas.drawLine(action.x1, action.y1, action.x2, action.y2, paint)
        val angle = kotlin.math.atan2((action.y2 - action.y1).toDouble(), (action.x2 - action.x1).toDouble())
        val size = 26f
        val a1 = angle + Math.PI * 0.82
        val a2 = angle - Math.PI * 0.82
        canvas.drawLine(action.x2, action.y2, action.x2 + size * kotlin.math.cos(a1).toFloat(), action.y2 + size * kotlin.math.sin(a1).toFloat(), paint)
        canvas.drawLine(action.x2, action.y2, action.x2 + size * kotlin.math.cos(a2).toFloat(), action.y2 + size * kotlin.math.sin(a2).toFloat(), paint)
    }

    private fun blurRegion(bitmap: Bitmap, rect: RectF) {
        val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = rect.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.toInt().coerceIn(top + 1, bitmap.height)
        val block = max(8, min(24, (right - left) / 24))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val bx = min(block, right - x)
                val by = min(block, bottom - y)
                val cx = x + bx / 2
                val cy = y + by / 2
                val color = bitmap.getPixel(cx.coerceIn(0, bitmap.width - 1), cy.coerceIn(0, bitmap.height - 1))
                paint.color = color
                val canvas = Canvas(bitmap)
                canvas.drawRect(RectF(x.toFloat(), y.toFloat(), (x + bx).toFloat(), (y + by).toFloat()), paint)
                x += bx
            }
            y += block
        }
    }
}

data class PreparedImage(val bitmap: Bitmap, val bytes: ByteArray, val dataUri: String)

sealed class AnnotationAction {
    data class Rect(val rect: RectF) : AnnotationAction()
    data class Circle(val rect: RectF) : AnnotationAction()
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : AnnotationAction()
    data class Arrow(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : AnnotationAction()
    data class Text(val x: Float, val y: Float, val text: String) : AnnotationAction()
    data class Blur(val rect: RectF) : AnnotationAction()
}
