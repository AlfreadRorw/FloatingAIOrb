package com.alfread.alfvoicecontrol.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "alf_settings")

data class AlfSettings(
    val voiceControlEnabled: Boolean = true,
    val wakeWord: String = "Alf",
    val wakeWordAudioPath: String? = null,
    val backgroundListening: Boolean = true,
    val screenWakeEnabled: Boolean = true,
    val screenOffEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val pinEnabled: Boolean = false,
    val matchConfidenceThreshold: Float = 0.72f,
    val listeningWindowSeconds: Int = 5
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val VOICE_CONTROL_ENABLED = booleanPreferencesKey("voice_control_enabled")
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val WAKE_WORD_AUDIO_PATH = stringPreferencesKey("wake_word_audio_path")
        val BACKGROUND_LISTENING = booleanPreferencesKey("background_listening")
        val SCREEN_WAKE_ENABLED = booleanPreferencesKey("screen_wake_enabled")
        val SCREEN_OFF_ENABLED = booleanPreferencesKey("screen_off_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val MATCH_CONFIDENCE = floatPreferencesKey("match_confidence_threshold")
        val LISTENING_WINDOW = intPreferencesKey("listening_window_seconds")
    }

    val settingsFlow: Flow<AlfSettings> = context.dataStore.data.map { prefs ->
        AlfSettings(
            voiceControlEnabled = prefs[Keys.VOICE_CONTROL_ENABLED] ?: true,
            wakeWord = prefs[Keys.WAKE_WORD] ?: "Alf",
            wakeWordAudioPath = prefs[Keys.WAKE_WORD_AUDIO_PATH],
            backgroundListening = prefs[Keys.BACKGROUND_LISTENING] ?: true,
            screenWakeEnabled = prefs[Keys.SCREEN_WAKE_ENABLED] ?: true,
            screenOffEnabled = prefs[Keys.SCREEN_OFF_ENABLED] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            pinEnabled = prefs[Keys.PIN_ENABLED] ?: false,
            matchConfidenceThreshold = prefs[Keys.MATCH_CONFIDENCE] ?: 0.72f,
            listeningWindowSeconds = prefs[Keys.LISTENING_WINDOW] ?: 5
        )
    }

    suspend fun setVoiceControlEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.VOICE_CONTROL_ENABLED] = enabled
    }

    suspend fun setWakeWord(word: String) = context.dataStore.edit { it[Keys.WAKE_WORD] = word }

    suspend fun setWakeWordAudioPath(path: String?) = context.dataStore.edit {
        if (path == null) it.remove(Keys.WAKE_WORD_AUDIO_PATH) else it[Keys.WAKE_WORD_AUDIO_PATH] = path
    }

    suspend fun setBackgroundListening(enabled: Boolean) = context.dataStore.edit {
        it[Keys.BACKGROUND_LISTENING] = enabled
    }

    suspend fun setScreenWakeEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.SCREEN_WAKE_ENABLED] = enabled
    }

    suspend fun setScreenOffEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.SCREEN_OFF_ENABLED] = enabled
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.NOTIFICATIONS_ENABLED] = enabled
    }

    suspend fun setOnboardingCompleted(completed: Boolean) = context.dataStore.edit {
        it[Keys.ONBOARDING_COMPLETED] = completed
    }

    suspend fun setPinEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.PIN_ENABLED] = enabled }

    suspend fun setMatchConfidenceThreshold(value: Float) = context.dataStore.edit {
        it[Keys.MATCH_CONFIDENCE] = value
    }

    suspend fun setListeningWindowSeconds(seconds: Int) = context.dataStore.edit {
        it[Keys.LISTENING_WINDOW] = seconds
    }
}
