package com.alfread.alfvoicecontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alfread.alfvoicecontrol.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * Restarts the voice-listening foreground service after a reboot, but only
 * if the user previously had voice control + background listening enabled.
 * Respects Android's background-start restrictions on newer OS versions -
 * if the OS refuses to start the service, that limitation is surfaced in
 * the Home screen UI rather than silently ignored.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            val settings = SettingsRepository(appContext).settingsFlow.first()
            if (settings.voiceControlEnabled && settings.backgroundListening) {
                VoiceListeningService.start(appContext)
            }
        }
    }
}
