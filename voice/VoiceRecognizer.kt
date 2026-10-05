package com.alfread.alfvoicecontrol.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceRecognizer(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun recognizeOnce(
        preferOnDevice: Boolean = true,
        onPartial: (String) -> Unit = {},
        onResult: (String, Float) -> Unit,
        onError: (Int, String) -> Unit
    ) {
        stop()
        val speechRecognizer = try {
            if (
                preferOnDevice &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                onError(SpeechRecognizer.ERROR_CLIENT, "Speech recognition is unavailable on this device.")
                return
            }
        } catch (e: Exception) {
            onError(SpeechRecognizer.ERROR_CLIENT, e.message ?: "Could not create speech recognizer.")
            return
        }

        recognizer = speechRecognizer
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                onResult(text, scores?.firstOrNull() ?: 0f)
                stop()
            }

            override fun onError(error: Int) {
                onError(error, errorMessage(error))
                stop()
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOnDevice)
        }

        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            onError(SpeechRecognizer.ERROR_CLIENT, e.message ?: "Could not start listening.")
            stop()
        }
    }

    fun stop() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    companion object {
        private fun errorMessage(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone could not be accessed."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is not granted."
            SpeechRecognizer.ERROR_NETWORK -> "Speech service network error."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service timed out."
            SpeechRecognizer.ERROR_NO_MATCH -> "Could not recognize your voice."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
            SpeechRecognizer.ERROR_SERVER -> "Speech service failed."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected."
            else -> "Speech recognition failed (code $error)."
        }
    }
}
