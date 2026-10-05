package com.alfread.alfvoicecontrol

import android.content.Context
import com.alfread.alfvoicecontrol.data.AppDatabase
import com.alfread.alfvoicecontrol.data.CommandDao
import com.alfread.alfvoicecontrol.data.SettingsRepository
import com.alfread.alfvoicecontrol.security.PinManager
import com.alfread.alfvoicecontrol.voice.AudioRecorder

/**
 * Lightweight manual dependency container. The app is small enough that a
 * full DI framework (Hilt/Koin) would add build complexity without real
 * benefit; everything here is a cheap, stateless-ish singleton.
 */
class AppContainer(context: Context) {
    val commandDao: CommandDao = AppDatabase.getInstance(context).commandDao()
    val settingsRepository: SettingsRepository = SettingsRepository(context)
    val pinManager: PinManager = PinManager(context)
    val audioRecorder: AudioRecorder = AudioRecorder(context)

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
