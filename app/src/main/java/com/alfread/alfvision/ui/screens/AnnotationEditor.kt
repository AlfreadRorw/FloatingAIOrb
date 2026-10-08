package com.alfread.alfvision.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfread.alfvision.core.model.PendingImage
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private enum class Tool { RECTANGLE, CIRCLE, ARROW, LINE, TEXT, BLUR }
private data class Mark(val tool: Tool, val start: Offset, val end: Offset, val text: String = "")

@Composable
fun AnnotationEditor(
    image: PendingImage,
    onSave: (PendingImage) -> Unit,
    onClose: () -> Unit
) {
    val bitmap = remember(image.bytes) { android.graphics.BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size) }
    // FIX: onClose() tidak boleh dipanggil langsung saat composition.
    if (bitmap == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var tool by remember { mutableStateOf(Tool.RECTANGLE) }
    var draft by remember { mutableStateOf<Mark?>(null) }
    var marks by remember { mutableStateOf(listOf<Mark>()) }
    var redo by remember { mutableStateOf(listOf<Mark>()) }
    var text by remember { mutableStateOf("") }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            Text("Annotate", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (marks.isNotEmpty()) { redo = redo + marks.last(); marks = marks.dropLast(1) } }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
            IconButton(onClick = { if (redo.isNotEmpty()) { marks = marks + redo.last(); redo = redo.dropLast(1) } }) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
            Button(onClick = {
                // FIX: koordinat mark (ruang canvas) dikonversi ke ruang bitmap asli.
                val scaleX = if (canvasSize.width > 0) bitmap.width.toFloat() / canvasSize.width else 1f
                val scaleY = if (canvasSize.height > 0) bitmap.height.toFloat() / canvasSize.height else 1f
                val output = renderAnnotations(bitmap, marks, scaleX, scaleY)
                val bytes = java.io.ByteArrayOutputStream().use { out -> output.compress(Bitmap.CompressFormat.JPEG, 90, out); out.toByteArray() }
                output.recycle()
                onSave(PendingImage(bytes, "image/jpeg", "Annotated"))
            }) { Text("Save") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolChip("Rect", Tool.RECTANGLE, tool, Icons.Default.CropSquare) { tool = it }
            ToolChip("Circle", Tool.CIRCLE, tool, Icons.Default.Circle) { tool = it }
            ToolChip("Arrow", Tool.ARROW, tool, Icons.AutoMirrored.Filled.ArrowForward) { tool = it }
            ToolChip("Line", Tool.LINE, tool, Icons.Default.Minimize) { tool = it }
            ToolChip("Text", Tool.TEXT, tool, Icons.Default.TextFields) { tool = it }
            ToolChip("Blur", Tool.BLUR, tool, Icons.Default.BlurOn) { tool = it }
        }
        if (tool == Tool.TEXT) {
            OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().padding(8.dp), label = { Text("Text to place (tap gambar)") })
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(ratio).onSizeChanged { canvasSize = it }) {
                androidx.compose.foundation.Image(
                    bitmap.asImageBitmap(),
                    contentDescription = "Image being annotated",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(tool, text) {
                            detectTapGestures { offset ->
                                if (tool == Tool.TEXT && text.isNotBlank()) {
                                    marks = marks + Mark(tool, offset, offset, text)
                                    redo = emptyList()
                                }
                            }
                        }
                        .pointerInput(tool) {
                            detectDragGestures(
                                onDragStart = { start -> if (tool != Tool.TEXT) draft = Mark(tool, start, start) },
                                onDrag = { change, drag -> change.consume(); draft = draft?.let { it.copy(end = it.end + drag) } },
                                onDragEnd = { draft?.let { marks = marks + it; redo = emptyList() }; draft = null },
                                onDragCancel = { draft = null }
                            )
                        }
                ) {
                    fun drawMark(mark: Mark, alpha: Float = 1f) {
                        val red = Color.Red.copy(alpha = alpha)
                        val topLeft = Offset(min(mark.start.x, mark.end.x), min(mark.start.y, mark.end.y))
                        val boxSize = Size(abs(mark.end.x - mark.start.x), abs(mark.end.y - mark.start.y))
                        when (mark.tool) {
                            // FIX: sebelumnya warna Color.Transparent sehingga rect/circle tidak terlihat.
                            Tool.RECTANGLE -> drawRect(red, topLeft = topLeft, size = boxSize, style = Stroke(4f))
                            Tool.CIRCLE -> drawOval(red, topLeft = topLeft, size = boxSize, style = Stroke(4f))
                            Tool.LINE -> drawLine(red, mark.start, mark.end, 4f)
                            Tool.ARROW -> drawArrow(mark.start, mark.end, alpha)
                            Tool.TEXT -> drawContext.canvas.nativeCanvas.drawText(mark.text, mark.start.x, mark.start.y, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.RED; textSize = 42f })
                            Tool.BLUR -> drawRect(Color.Black.copy(alpha = 0.35f), topLeft = topLeft, size = boxSize)
                        }
                    }
                    marks.forEach { drawMark(it) }
                    draft?.let { drawMark(it, 0.65f) }
                }
            }
        }
    }
    // FIX: bitmap TIDAK lagi di-recycle di onDispose; Image masih memakainya saat recomposition
    // sehingga menyebabkan crash "Canvas: trying to use a recycled bitmap".
}

@Composable private fun ToolChip(label: String, value: Tool, selected: Tool, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (Tool) -> Unit) {
    FilterChip(selected = value == selected, onClick = { onClick(value) }, label = { Text(label, fontSize = 11.sp) }, leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) })
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(start: Offset, end: Offset, alpha: Float) {
    drawLine(Color.Red.copy(alpha = alpha), start, end, 4f)
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val len = 22.0
    val a1 = angle + Math.PI * 0.83
    val a2 = angle - Math.PI * 0.83
    drawLine(Color.Red.copy(alpha = alpha), end, Offset((end.x + len * cos(a1)).toFloat(), (end.y + len * sin(a1)).toFloat()), 4f)
    drawLine(Color.Red.copy(alpha = alpha), end, Offset((end.x + len * cos(a2)).toFloat(), (end.y + len * sin(a2)).toFloat()), 4f)
}

private fun renderAnnotations(source: Bitmap, marks: List<Mark>, scaleX: Float, scaleY: Float): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.RED; strokeWidth = 6f; style = Paint.Style.STROKE }
    marks.forEach { mark ->
        val sx = mark.start.x * scaleX
        val sy = mark.start.y * scaleY
        val ex = mark.end.x * scaleX
        val ey = mark.end.y * scaleY
        when (mark.tool) {
            Tool.RECTANGLE -> canvas.drawRect(sx, sy, ex, ey, paint)
            Tool.CIRCLE -> canvas.drawOval(min(sx, ex), min(sy, ey), max(sx, ex), max(sy, ey), paint)
            Tool.LINE -> canvas.drawLine(sx, sy, ex, ey, paint)
            Tool.ARROW -> {
                canvas.drawLine(sx, sy, ex, ey, paint)
                val a = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                val len = 35.0
                canvas.drawLine(ex, ey, (ex + len * cos(a + 2.65)).toFloat(), (ey + len * sin(a + 2.65)).toFloat(), paint)
                canvas.drawLine(ex, ey, (ex + len * cos(a - 2.65)).toFloat(), (ey + len * sin(a - 2.65)).toFloat(), paint)
            }
            Tool.TEXT -> { paint.style = Paint.Style.FILL; paint.textSize = 52f; canvas.drawText(mark.text, sx, sy, paint); paint.style = Paint.Style.STROKE }
            Tool.BLUR -> boxBlur(out, min(sx, ex).toInt().coerceAtLeast(0), min(sy, ey).toInt().coerceAtLeast(0), max(sx, ex).toInt().coerceAtMost(out.width), max(sy, ey).toInt().coerceAtMost(out.height))
        }
    }
    return out
}

private fun boxBlur(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int) {
    if (right <= left || bottom <= top) return
    val w = right - left
    val h = bottom - top
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, left, top, w, h)
    val copy = pixels.copyOf()
    for (y in 0 until h) for (x in 0 until w) {
        var rs = 0; var gs = 0; var bs = 0; var count = 0
        for (ky in -2..2) for (kx in -2..2) {
            val nx = (x + kx).coerceIn(0, w - 1); val ny = (y + ky).coerceIn(0, h - 1)
            val c = copy[ny * w + nx]
            rs += (c shr 16) and 255; gs += (c shr 8) and 255; bs += c and 255; count++
        }
        pixels[y * w + x] = android.graphics.Color.argb(255, rs / count, gs / count, bs / count)
    }
    bitmap.setPixels(pixels, 0, w, left, top, w, h)
}
