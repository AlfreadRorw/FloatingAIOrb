package com.alfread.alfvision.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alfread.alfvision.core.AppContainer
import com.alfread.alfvision.core.CaptureEventBus
import com.alfread.alfvision.core.RegionState
import com.alfread.alfvision.ui.MainViewModel
import com.alfread.alfvision.ui.components.AnnotationEditorDialog
import com.alfread.alfvision.ui.components.CompareDialog
import kotlinx.coroutines.launch

@Composable
fun VisionScreen(
    viewModel: MainViewModel,
    onSelectRegion: () -> Unit,
    onCapture: () -> Unit
) {
    val frame by viewModel.frame.collectAsStateWithLifecycle()
    val previous by viewModel.previousFrame.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("ini kenapa?") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var annotate by remember { mutableStateOf(false) }
    var compare by remember { mutableStateOf(false) }
    var workingImage by remember { mutableStateOf<Bitmap?>(null) }
    var pinnedImage by remember { mutableStateOf<Bitmap?>(null) }
    var targetLanguage by remember { mutableStateOf("Indonesian") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Vision", style = MaterialTheme.typography.headlineSmall)
        Text("Pilih region di atas aplikasi lain, lalu capture dan analisis.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSelectRegion) { Icon(Icons.Default.Crop, null); Spacer(Modifier.width(5.dp)); Text("REGION") }
            OutlinedButton(onClick = onCapture) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(5.dp)); Text("CAPTURE") }
            if (frame != null) OutlinedButton(onClick = { workingImage = frame!!.bitmap; annotate = true }) { Text("ANNOTATE") }
            if (previous?.bitmap != null && frame != null) OutlinedButton(onClick = { compare = true }) { Text("COMPARE") }
            if (frame != null) OutlinedButton(onClick = { pinnedImage = frame!!.bitmap }) { Text("PIN") }
        }
        Text(
            RegionState.current?.let { "Region: ${it.width} × ${it.height} @ ${it.x},${it.y}" } ?: "Region: not selected",
            style = MaterialTheme.typography.bodySmall
        )
        if (frame != null) {
            Image(
                frame!!.bitmap.asImageBitmap(),
                contentDescription = "Current captured region",
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Surface(Modifier.fillMaxWidth().height(220.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(contentAlignment = Alignment.Center) { Text("Belum ada capture") }
            }
        }
        Text("Current Image • Previous Image ${if (previous != null) "available" else "not available"} • Pinned Image ${if (pinnedImage != null) "available" else "not pinned"}", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Indonesian", "English", "Malay", "Chinese", "Japanese", "Korean", "Arabic", "Spanish", "French", "German").forEach { lang ->
                FilterChip(selected = targetLanguage == lang, onClick = { targetLanguage = lang }, label = { Text(lang, fontSize = 10.sp) })
            }
        }
        OutlinedTextField(prompt, { prompt = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Question") }, minLines = 2)
        OutlinedButton(
            onClick = {
                prompt = "Translate all clearly visible text in this image into $targetLanguage. Preserve names, numbers, and important formatting."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("TRANSLATE TO $targetLanguage") }
        Button(
            onClick = {
                val image = workingImage ?: frame?.bitmap ?: return@Button
                busy = true
                scope.launch {
                    AppContainer.groq.chat(prompt, images = listOf(image), model = settings.selectedModel)
                        .onSuccess { answer = it.result.text }
                        .onFailure { answer = it.message ?: "Request gagal." }
                    busy = false
                }
            },
            enabled = frame != null && prompt.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Send, null); Spacer(Modifier.width(7.dp)); Text(if (busy) "Analyzing…" else "ANALYZE")
        }
        if (answer.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("ALF AI", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(answer)
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ALF AI response", answer))
                    }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Copy") }
                }
            }
        }
    }

    if (annotate && workingImage != null) {
        AnnotationEditorDialog(workingImage!!, onCancel = { annotate = false }, onApply = { edited -> workingImage = edited; annotate = false })
    }
    if (compare && previous != null && frame != null) {
        CompareDialog(previous!!.bitmap, frame!!.bitmap, settings.selectedModel, onDismiss = { compare = false }, onResult = { answer = it })
    }
}
