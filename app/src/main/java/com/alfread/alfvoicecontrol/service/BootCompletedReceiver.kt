package com.alfread.alfvoicecontrol.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alfread.alfvoicecontrol.R
import com.alfread.alfvoicecontrol.data.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = LocalStore(context).settingsFlow.first()
                if (settings.listeningEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            VoiceMonitoringService.CHANNEL_ID,
                            "Voice monitoring",
                            NotificationManager.IMPORTANCE_LOW
                        )
                        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
                    }
                    val notification = NotificationCompat.Builder(context, VoiceMonitoringService.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_alf)
                        .setContentTitle("ALF Voice Control")
                        .setContentText("Listening was enabled before reboot. Open ALF to resume microphone monitoring.")
                        .setAutoCancel(true)
                        .build()
                    runCatching { NotificationManagerCompat.from(context).notify(2002, notification) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
