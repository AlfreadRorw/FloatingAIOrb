package com.alfread.alfvision.ui.components

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.alfread.alfvision.domain.AnnotationAction
import com.alfread.alfvision.domain.ImageProcessor

enum class AnnotationTool { RECTANGLE, CIRCLE, ARROW, LINE, BLUR, TEXT, CROP }

@Composable
fun AnnotationEditorDialog(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onApply: (Bitmap) -> Unit
) {
    var tool by remember { mutableStateOf(AnnotationTool.RECTANGLE) }
    val actions = remember { mutableStateListOf<AnnotationAction>() }
    var start by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var current by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var textValue by remember { mutableStateOf("Marked area") }
    val context = LocalContext.current
    val processor = remember(context) { ImageProcessor(context) }
    val displayed = bitmap.asImageBitmap()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = { Button(onClick = { onApply(processor.annotate(bitmap, actions)) }) { Text("Apply") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { actions.clear() }) { Text("Clear") }
                TextButton(onClick = { if (actions.isNotEmpty()) actions.removeAt(actions.lastIndex) }) { Text("Undo") }
            }
        },
        title = { Text("Annotate Screenshot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(AnnotationTool.values().toList()) { item ->
                        FilterChip(selected = tool == item, onClick = { tool = item }, label = { Text(item.name) })
                    }
                }
                if (tool == AnnotationTool.TEXT) {
                    OutlinedTextField(textValue, { textValue = it }, label = { Text("Text") }, singleLine = true)
                }
                Box(Modifier.fillMaxWidth().height(320.dp).onSizeChanged { canvasSize = it }) {
                    Image(displayed, "Screenshot to annotate", Modifier.fillMaxSize())
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(tool) {
                            detectDragGestures(
                                onDragStart = { start = it.x to it.y; current = it.x to it.y },
                                onDrag = { change, drag ->
                                    change.consume()
                                    current = ((current?.first ?: 0f) + drag.x) to ((current?.second ?: 0f) + drag.y)
                                },
                                onDragEnd = {
                                    val s = start; val e = current
                                    if (s != null && e != null) {
                                        val safeWidth = canvasSize.width.coerceAtLeast(1)
                                        val safeHeight = canvasSize.height.coerceAtLeast(1)
                                        val scaleX = bitmap.width.toFloat() / safeWidth
                                        val scaleY = bitmap.height.toFloat() / safeHeight
                                        val x1 = (minOf(s.first, e.first) * scaleX).coerceIn(0f, bitmap.width.toFloat())
                                        val y1 = (minOf(s.second, e.second) * scaleY).coerceIn(0f, bitmap.height.toFloat())
                                        val x2 = (maxOf(s.first, e.first) * scaleX).coerceIn(0f, bitmap.width.toFloat())
                                        val y2 = (maxOf(s.second, e.second) * scaleY).coerceIn(0f, bitmap.height.toFloat())
                                        when (tool) {
                                            AnnotationTool.RECTANGLE -> actions += AnnotationAction.Rect(RectF(x1, y1, x2, y2))
                                            AnnotationTool.CIRCLE -> actions += AnnotationAction.Circle(RectF(x1, y1, x2, y2))
                                            AnnotationTool.ARROW -> actions += AnnotationAction.Arrow(x1, y1, x2, y2)
                                            AnnotationTool.LINE -> actions += AnnotationAction.Line(x1, y1, x2, y2)
                                            AnnotationTool.BLUR -> actions += AnnotationAction.Blur(RectF(x1, y1, x2, y2))
                                            AnnotationTool.TEXT -> actions += AnnotationAction.Text(x1, y1.coerceAtLeast(32f), textValue)
                                            AnnotationTool.CROP -> {
                                                val cropped = processor.cropByRect(bitmap, RectF(x1, y1, x2.coerceAtLeast(x1 + 1f), y2.coerceAtLeast(y1 + 1f)))
                                                onApply(processor.annotate(cropped, emptyList()))
                                            }
                                        }
                                    }
                                    start = null; current = null
                                }
                            )
                        },
                        onDraw = {}
                    )
                }
            }
        }
    )
}
