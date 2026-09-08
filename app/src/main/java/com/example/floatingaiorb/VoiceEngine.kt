package com.example.floatingaiorb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceEngine(
    private val context: Context,
    private val onState: (State) -> Unit,
    private val onText: (String) -> Unit
) {
    enum class State { IDLE, LISTENING, SPEAKING, ERROR }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var initialized = false
    private var preset = Preset.KAWAII

    enum class Preset(val label: String, val pitch: Float, val rate: Float) {
        KAWAII("Kawaii", 1.25f, 0.96f),
        CUTE("Cute", 1.18f, 0.92f),
        COOL("Cool", 0.92f, 0.95f),
        CYBER("Cyber", 1.04f, 1.00f)
    }

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() { onState(State.LISTENING) }
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onError(error: Int) {
                        onState(State.ERROR)
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        onState(State.IDLE)
                        if (text.isNotBlank()) onText(text)
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }
        tts = TextToSpeech(context) { status ->
            initialized = status == TextToSpeech.SUCCESS
            if (initialized) {
                tts?.language = Locale.getDefault()
                applyPreset()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { onState(State.SPEAKING) }
                    override fun onDone(utteranceId: String?) { onState(State.IDLE) }
                    override fun onError(utteranceId: String?) { onState(State.ERROR) }
                })
            }
        }
    }

    fun setPreset(value: Preset) {
        preset = value
        if (initialized) applyPreset()
    }

    fun startListening(languageTag: String = "id-ID") {
        val r = recognizer ?: run { onState(State.ERROR); return }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bicara ke Floating AI")
        }
        onState(State.LISTENING)
        runCatching { r.startListening(intent) }.onFailure { onState(State.ERROR) }
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    fun speak(text: String) {
        if (!initialized || text.isBlank()) return
        applyPreset()
        val cleaned = AIClient.cleanForSpeech(text)
        val id = "orb-${System.currentTimeMillis()}"
        onState(State.SPEAKING)
        tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
        onState(State.IDLE)
    }

    private fun applyPreset() {
        tts?.setPitch(preset.pitch)
        tts?.setSpeechRate(preset.rate)
    }

    fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
    }
}
