package com.alfread.alfvision.vision

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceInputManager(
    context: Context,
    private val session: SessionStore
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            session.setVoiceState(VoiceState.ERROR)
            session.setError("Speech recognition tidak tersedia di perangkat ini.")
            return
        }
        stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(listener)
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        }
        session.setVoiceState(VoiceState.LISTENING)
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        if (session.voiceState.value == VoiceState.LISTENING) session.setVoiceState(VoiceState.IDLE)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() { session.setVoiceState(VoiceState.IDLE) }
        override fun onError(error: Int) {
            session.setVoiceState(VoiceState.ERROR)
            session.setError("Voice input gagal. Coba lagi.")
        }
        override fun onResults(results: Bundle?) {
            val value = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (value.isNotBlank()) session.setInput(value)
            session.setVoiceState(VoiceState.IDLE)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val value = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (value.isNotBlank()) session.setInput(value)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
