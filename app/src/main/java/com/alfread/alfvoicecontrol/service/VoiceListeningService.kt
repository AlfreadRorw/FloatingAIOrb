package com.alfread.alfvoicecontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.alfread.alfvoicecontrol.MainActivity
import com.alfread.alfvoicecontrol.R
import com.alfread.alfvoicecontrol.commands.CommandExecutor
import com.alfread.alfvoicecontrol.data.AppDatabase
import com.alfread.alfvoicecontrol.data.SettingsRepository
import com.alfread.alfvoicecontrol.voice.CommandMatcher
import com.alfread.alfvoicecontrol.voice.ListenerState
import com.alfread.alfvoicecontrol.voice.WakeWordListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps listening for the user's wake word, then for
 * one command phrase, matches it against the stored [com.alfread.alfvoicecontrol.data.VoiceCommand]
 * list and executes it via [CommandExecutor]. Always shows a visible
 * notification while the microphone may be active in the background, per
 * Android's foreground-service and privacy requirements.
 */
class VoiceListeningService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settingsRepository: SettingsRepository
    private var wakeWordListener: WakeWordListener? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        createNotificationChannel()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(ListenerState.WAITING_FOR_WAKE_WORD))
        startListeningLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        wakeWordListener?.stop()
        wakeWordListener = null
        _isRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListeningLoop() {
        serviceScope.launch {
            val settings = settingsRepository.settingsFlow
            // Read the current settings once per (re)start; the service is
            // restarted whenever the user changes voice-control settings.
            var currentWakeWord = "Alf"
            var windowSeconds = 5
            settings.collect { s ->
                currentWakeWord = s.wakeWord
                windowSeconds = s.listeningWindowSeconds
                if (wakeWordListener == null) {
                    setupListener { currentWakeWord } .also { it.start() }
                }
            }
        }
    }

    private fun setupListener(wakeWordProvider: () -> String): WakeWordListener {
        val listener = WakeWordListener(
            context = applicationContext,
            getWakeWord = wakeWordProvider,
            commandListeningWindowMillis = 6000L,
            onWakeWordDetected = {
                updateNotification(ListenerState.LISTENING_FOR_COMMAND)
            },
            onCommandHeard = { text -> handleRecognizedCommand(text) },
            onStateChanged = { state -> updateNotification(state) },
            onError = { stopSelf() }
        )
        wakeWordListener = listener
        return listener
    }

    private fun handleRecognizedCommand(recognizedText: String) {
        serviceScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).commandDao()
            val settings = settingsRepository.settingsFlow
            var threshold = 0.72f
            // settingsFlow is hot; take the latest cached value quickly.
            kotlin.runCatching {
                val enabledCommands = dao.getAllEnabled()
                val match = CommandMatcher.findBestMatch(recognizedText, enabledCommands, threshold)
                val bestCommand = match.command
                if (bestCommand != null) {
                    val result = CommandExecutor.execute(applicationContext, bestCommand)
                    CommandExecutor.showResultToast(applicationContext, result)
                }
            }
        }
    }

    private fun updateNotification(state: ListenerState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: ListenerState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stateText = when (state) {
            ListenerState.LISTENING_FOR_COMMAND -> "Listening for your command..."
            else -> getString(R.string.notification_listening_text)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_listening_title))
            .setContentText(stateText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "alf_voice_listening"
        private const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        fun start(context: android.content.Context) {
            val intent = Intent(context, VoiceListeningService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, VoiceListeningService::class.java))
        }
    }
}
