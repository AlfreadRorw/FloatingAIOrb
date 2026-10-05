package com.alfread.alfvoicecontrol.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

sealed class SpeechResult {
    data class Success(val text: String) : SpeechResult()
    data class Error(val code: Int, val message: String) : SpeechResult()
    object NoSpeech : SpeechResult()
    object Listening : SpeechResult()
}

/**
 * Thin wrapper around [SpeechRecognizer] (speech-to-text only - this is NOT
 * biometric speaker verification; see the README for that distinction).
 * Must be created and used on the main thread.
 */
class SpeechRecognitionManager(
    private val context: Context,
    private val onResult: (SpeechResult) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(timeoutMillis: Long = 6000L) {
        if (!isAvailable) {
            onResult(SpeechResult.Error(-1, "Speech recognition unavailable on this device"))
            return
        }

        stopListening()

        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speechRecognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onResult(SpeechResult.Listening)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()
                if (best.isNullOrBlank()) {
                    onResult(SpeechResult.NoSpeech)
                } else {
                    onResult(SpeechResult.Success(best))
                }
                stopListening()
            }

            override fun onError(error: Int) {
                onResult(SpeechResult.Error(error, errorMessage(error)))
                stopListening()
            }

            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
        })

        speechRecognizer.startListening(intent)

        mainHandler.postDelayed({
            // Safety timeout in case the recognizer never calls back.
            if (recognizer === speechRecognizer) {
                onResult(SpeechResult.NoSpeech)
                stopListening()
            }
        }, timeoutMillis)
    }

    fun stopListening() {
        runCatching {
            recognizer?.stopListening()
            recognizer?.destroy()
        }
        recognizer = null
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
        SpeechRecognizer.ERROR_CLIENT -> "Recognition client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "Could not recognize your voice"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microphone busy"
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Unknown recognition error"
    }
}
