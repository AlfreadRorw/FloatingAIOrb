package com.alfread.alfvoicecontrol.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.alfread.alfvoicecontrol.MainActivity
import com.alfread.alfvoicecontrol.R
import com.alfread.alfvoicecontrol.commands.CommandExecutor
import com.alfread.alfvoicecontrol.commands.CommandMatcher
import com.alfread.alfvoicecontrol.data.LocalStore
import com.alfread.alfvoicecontrol.model.VoiceCommand
import com.alfread.alfvoicecontrol.voice.VoiceRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VoiceMonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: LocalStore
    private lateinit var executor: CommandExecutor
    private var recognizer: VoiceRecognizer? = null
    private var commandListening = false
    private var running = false

    override fun onCreate() {
        super.onCreate()
        store = LocalStore(this)
        executor = CommandExecutor(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopMonitoring()
            else -> startMonitoring()
        }
        return START_NOT_STICKY
    }

    private fun startMonitoring() {
        if (running) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        try {
            val notification = buildNotification("Waiting for wake word")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
            }
        } catch (_: Exception) {
            stopSelf()
            return
        }

        running = true
        serviceScope.launch {
            store.setListening(true)
            startWakeListening()
        }
    }

    private fun startWakeListening() {
        if (!running) return
        commandListening = false
        recognizer?.stop()
        recognizer = VoiceRecognizer(this)
        recognizer?.recognizeOnce(
            preferOnDevice = true,
            onResult = { transcript, _ ->
                handleTranscript(transcript)
            },
            onError = { error, message ->
                if (!running) return@recognizeOnce
                updateNotification(message)
                serviceScope.launch {
                    delay(retryDelayFor(error))
                    if (running && !commandListening) startWakeListening()
                }
            }
        )
    }

    private fun startCommandListening() {
        if (!running) return
        commandListening = true
        recognizer?.stop()
        recognizer = VoiceRecognizer(this)
        updateNotification("Wake word detected. Listening for a command...")
        recognizer?.recognizeOnce(
            preferOnDevice = true,
            onResult = { transcript, _ ->
                handleCommandTranscript(transcript)
            },
            onError = { error, message ->
                commandListening = false
                updateNotification(message)
                serviceScope.launch {
                    delay(retryDelayFor(error))
                    if (running) startWakeListening()
                }
            }
        )
    }

    private fun handleTranscript(transcript: String) {
        if (transcript.isBlank()) {
            serviceScope.launch {
                delay(450L)
                if (running && !commandListening) startWakeListening()
            }
            return
        }

        serviceScope.launch {
            val settings = store.settingsFlow.first()
            val commands = store.commandsFlow.first()
            val threshold = settings.confidenceThreshold
            val fullMatch = CommandMatcher.match(transcript, commands, threshold)
            if (fullMatch != null) {
                executeMatch(fullMatch.command, fullMatch.score, settings)
                return@launch
            }

            val wake = CommandMatcher.normalize(settings.wakeWord)
            val normalized = CommandMatcher.normalize(transcript)
            if (normalized == wake || normalized.startsWith("$wake ")) {
                val remainder = normalized.removePrefix(wake).trim()
                if (remainder.isNotBlank()) {
                    val remainderMatch = CommandMatcher.match(remainder, commands, threshold)
                    if (remainderMatch != null) {
                        executeMatch(remainderMatch.command, remainderMatch.score, settings)
                    } else {
                        updateNotification("Command not recognized: \"$remainder\"")
                    }
                } else {
                    startCommandListening()
                }
            } else {
                delay(300L)
                if (running && !commandListening) startWakeListening()
            }
        }
    }

    private fun handleCommandTranscript(transcript: String) {
        serviceScope.launch {
            val settings = store.settingsFlow.first()
            executeFromTranscript(transcript, settings.confidenceThreshold)
            commandListening = false
            if (running) {
                delay(350L)
                startWakeListening()
            }
        }
    }

    private suspend fun executeFromTranscript(transcript: String, threshold: Float) {
        val commands = store.commandsFlow.first()
        val settings = store.settingsFlow.first()
        val match = CommandMatcher.match(transcript, commands, threshold)
        if (match == null) {
            updateNotification("Command not recognized: \"$transcript\"")
            return
        }
        executeMatch(match.command, match.score, settings)
    }

    private fun executeMatch(command: VoiceCommand, score: Float, settings: com.alfread.alfvoicecontrol.model.AppSettings) {
        val result = executor.execute(command, settings.screenWakeEnabled, settings.screenOffEnabled)
        val message = when (result) {
            CommandExecutor.Result.SUCCESS -> "Executed ${command.name} (${(score * 100).toInt()}% match)"
            CommandExecutor.Result.DISABLED -> "${command.name} is disabled in Settings."
            CommandExecutor.Result.TARGET_NOT_FOUND -> "Target app is not installed or no launch activity was found."
            CommandExecutor.Result.BLOCKED -> "Android blocked background app launch. Open ALF and try again."
            CommandExecutor.Result.ADMIN_REQUIRED -> "Screen Off requires ALF Device Administrator permission."
            CommandExecutor.Result.FAILED -> "Could not execute ${command.name}. Check its permissions/settings."
        }
        updateNotification(message)
    }

    private fun retryDelayFor(error: Int): Long = when (error) {
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1_500L
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 5_000L
        else -> 750L
    }

    private fun stopMonitoring() {
        running = false
        commandListening = false
        recognizer?.stop()
        recognizer = null
        CoroutineScope(Dispatchers.IO).launch { store.setListening(false) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, VoiceMonitoringService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alf)
            .setContentTitle("ALF Voice Control")
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_alf, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voice monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when ALF Voice Control is listening for voice commands."
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        running = false
        recognizer?.stop()
        recognizer = null
        CoroutineScope(Dispatchers.IO).launch { store.setListening(false) }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "alf_voice_monitoring"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.alfread.alfvoicecontrol.action.START"
        const val ACTION_STOP = "com.alfread.alfvoicecontrol.action.STOP"
    }
}
