package com.alfread.alfvision.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.alfread.alfvision.core.AppContainer
import com.alfread.alfvision.ui.MainViewModel
import kotlinx.coroutines.launch

private data class ChatItem(val role: String, val text: String)

@Composable
fun ChatScreen(viewModel: MainViewModel, onRequestMicrophone: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val list = remember { mutableStateListOf<ChatItem>() }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<android.speech.SpeechRecognizer?>(null) }

    fun stopListening() {
        listening = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    fun startListening() {
        if (!com.alfread.alfvision.util.PermissionUtils.microphoneGranted(context)) {
            onRequestMicrophone()
            return
        }
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
            list += ChatItem("assistant", "Speech recognition is not available on this device.")
            return
        }
        stopListening()
        val sr = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
            override fun onBeginningOfSpeech() { listening = true }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                sr.destroy()
                if (recognizer === sr) recognizer = null
            }
            override fun onResults(results: android.os.Bundle?) {
                listening = false
                val spoken = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) input = if (input.isBlank()) spoken else "$input $spoken"
                sr.destroy()
                if (recognizer === sr) recognizer = null
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        sr.startListening(intent)
    }

    DisposableEffect(Unit) {
        onDispose { stopListening() }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Chat", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(4.dp))
        LazyColumn(state = state, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.role == "user") Arrangement.End else Arrangement.Start) {
                    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.large) { Text(item.text, Modifier.padding(12.dp)) }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("Ask anything") },
                maxLines = 5
            )
            IconButton(onClick = { if (listening) stopListening() else startListening() }) {
                Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, if (listening) "Stop voice input" else "Voice input")
            }
            FilledIconButton(
                onClick = {
                    val text = input.trim(); if (text.isBlank()) return@FilledIconButton
                    input = ""; list += ChatItem("user", text); sending = true
                    scope.launch {
                        AppContainer.groq.chat(text, model = settings.selectedModel)
                            .onSuccess { list += ChatItem("assistant", it.result.text) }
                            .onFailure { list += ChatItem("assistant", it.message ?: "Request failed") }
                        sending = false
                        if (list.isNotEmpty()) state.animateScrollToItem(list.lastIndex)
                    }
                }, enabled = !sending && input.isNotBlank()
            ) { Icon(Icons.Default.Send, "Send") }
        }
    }
}
