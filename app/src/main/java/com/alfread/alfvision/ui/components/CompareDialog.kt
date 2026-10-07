package com.alfread.alfvision.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.alfread.alfvision.core.AppContainer
import kotlinx.coroutines.launch

@Composable
fun CompareDialog(
    before: Bitmap,
    after: Bitmap,
    model: String,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit
) {
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Row { Icon(Icons.Default.Compare, null); Spacer(Modifier.width(6.dp)); Text("Compare Before / After") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Image(before.asImageBitmap(), "Before screenshot", Modifier.weight(1f).height(150.dp), contentScale = ContentScale.Fit)
                    Image(after.asImageBitmap(), "After screenshot", Modifier.weight(1f).height(150.dp), contentScale = ContentScale.Fit)
                }
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            AppContainer.groq.chat(
                                "Compare these two screenshots. Explain what changed, what disappeared, what appeared, and which differences matter.",
                                images = listOf(before, after), model = model
                            ).onSuccess { result = it.result.text }.onFailure { result = it.message ?: "Compare failed." }
                            busy = false
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Analyzing…" else "COMPARE") }
                if (result.isNotBlank()) Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) { Text(result, Modifier.padding(10.dp)) }
            }
        }
    )
}
