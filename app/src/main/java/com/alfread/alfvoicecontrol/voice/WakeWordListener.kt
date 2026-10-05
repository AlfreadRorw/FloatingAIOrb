package com.alfread.alfvoicecontrol.voice

import android.content.Context
import android.os.Handler
import android.os.Looper

enum class ListenerState {
    IDLE,
    WAITING_FOR_WAKE_WORD,
    LISTENING_FOR_COMMAND,
    STOPPED
}

/**
 * Drives the two-stage "wake word -> command" loop used by the foreground
 * listening service:
 *
 *   1. Keep restarting short recognition passes, checking each result
 *      against the configured wake word.
 *   2. Once the wake word is heard, listen once for the actual command
 *      inside [commandListeningWindowMillis] and hand the recognized text
 *      back to the caller for matching/execution.
 *
 * Must be driven from the main thread (SpeechRecognizer requirement).
 */
class WakeWordListener(
    private val context: Context,
    private val getWakeWord: () -> String,
    private val commandListeningWindowMillis: Long,
    private val onWakeWordDetected: () -> Unit,
    private val onCommandHeard: (String) -> Unit,
    private val onStateChanged: (ListenerState) -> Unit,
    private val onError: (String) -> Unit
) {
    private var state: ListenerState = ListenerState.IDLE
    private var speechManager: SpeechRecognitionManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var restartRunnable: Runnable? = null

    fun start() {
        if (state == ListenerState.WAITING_FOR_WAKE_WORD || state == ListenerState.LISTENING_FOR_COMMAND) return
        setState(ListenerState.WAITING_FOR_WAKE_WORD)
        listenForWakeWord()
    }

    fun stop() {
        setState(ListenerState.STOPPED)
        restartRunnable?.let { handler.removeCallbacks(it) }
        speechManager?.stopListening()
        speechManager = null
    }

    private fun setState(newState: ListenerState) {
        state = newState
        onStateChanged(newState)
    }

    private fun listenForWakeWord() {
        if (state != ListenerState.WAITING_FOR_WAKE_WORD) return

        speechManager = SpeechRecognitionManager(context) { result ->
            when (result) {
                is SpeechResult.Success -> {
                    if (CommandMatcher.containsPhrase(result.text, getWakeWord())) {
                        onWakeWordDetected()
                        listenForCommand()
                    } else {
                        scheduleWakeWordRestart()
                    }
                }
                is SpeechResult.NoSpeech -> scheduleWakeWordRestart()
                is SpeechResult.Error -> {
                    // Busy/no-match errors are expected constantly in a restart loop; only
                    // surface genuinely actionable errors (e.g. missing permission).
                    if (result.message.contains("permission", ignoreCase = true)) {
                        onError(result.message)
                        stop()
                    } else {
                        scheduleWakeWordRestart()
                    }
                }
                is SpeechResult.Listening -> Unit
            }
        }
        speechManager?.startListening(timeoutMillis = 4000L)
    }

    private fun scheduleWakeWordRestart() {
        if (state != ListenerState.WAITING_FOR_WAKE_WORD) return
        val runnable = Runnable { listenForWakeWord() }
        restartRunnable = runnable
        handler.postDelayed(runnable, 400L)
    }

    private fun listenForCommand() {
        setState(ListenerState.LISTENING_FOR_COMMAND)

        speechManager = SpeechRecognitionManager(context) { result ->
            when (result) {
                is SpeechResult.Success -> {
                    onCommandHeard(result.text)
                    setState(ListenerState.WAITING_FOR_WAKE_WORD)
                    scheduleWakeWordRestart()
                }
                is SpeechResult.NoSpeech, is SpeechResult.Error -> {
                    setState(ListenerState.WAITING_FOR_WAKE_WORD)
                    scheduleWakeWordRestart()
                }
                is SpeechResult.Listening -> Unit
            }
        }
        speechManager?.startListening(timeoutMillis = commandListeningWindowMillis)
    }
}
