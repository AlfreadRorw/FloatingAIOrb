package com.alfread.alfvoicecontrol.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alfread.alfvoicecontrol.model.ActionType
import com.alfread.alfvoicecontrol.model.AppSettings
import com.alfread.alfvoicecontrol.model.VoiceCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.alfStore by preferencesDataStore(name = "alf_voice_control")

class LocalStore(private val context: Context) {
    private object Keys {
        val commands = stringPreferencesKey("commands_json")
        val wakeWord = stringPreferencesKey("wake_word")
        val wakeWordAudioFile = stringPreferencesKey("wake_word_audio_file")
        val confidence = floatPreferencesKey("confidence_threshold")
        val listeningEnabled = booleanPreferencesKey("listening_enabled")
        val screenWake = booleanPreferencesKey("screen_wake_enabled")
        val screenOff = booleanPreferencesKey("screen_off_enabled")
        val notifications = booleanPreferencesKey("notifications_enabled")
        val firstRun = booleanPreferencesKey("first_run_completed")
        val pinSalt = stringPreferencesKey("pin_salt")
        val pinHash = stringPreferencesKey("pin_hash")
    }

    val settingsFlow: Flow<AppSettings> = context.alfStore.data.map { p ->
        AppSettings(
            listeningEnabled = p[Keys.listeningEnabled] ?: false,
            wakeWord = p[Keys.wakeWord] ?: "Alf",
            wakeWordAudioFile = p[Keys.wakeWordAudioFile],
            confidenceThreshold = p[Keys.confidence] ?: 0.68f,
            screenWakeEnabled = p[Keys.screenWake] ?: true,
            screenOffEnabled = p[Keys.screenOff] ?: true,
            notificationsEnabled = p[Keys.notifications] ?: true,
            firstRunCompleted = p[Keys.firstRun] ?: false
        )
    }

    val commandsFlow: Flow<List<VoiceCommand>> = context.alfStore.data.map { p ->
        decodeCommands(p[Keys.commands])
    }

    suspend fun seedDefaults() {
        val existing = commandsFlow.first()
        if (existing.isNotEmpty()) return
        saveCommands(
            listOf(
                VoiceCommand(
                    name = "Screen On",
                    triggerPhrase = "Alf bangun",
                    actionType = ActionType.SCREEN_ON
                ),
                VoiceCommand(
                    name = "Screen Off",
                    triggerPhrase = "Alf tidur",
                    actionType = ActionType.SCREEN_OFF
                )
            )
        )
    }

    suspend fun saveCommands(commands: List<VoiceCommand>) {
        context.alfStore.edit { it[Keys.commands] = encodeCommands(commands) }
    }

    suspend fun upsertCommand(command: VoiceCommand) {
        val current = commandsFlow.first().toMutableList()
        val index = current.indexOfFirst { it.id == command.id }
        if (index >= 0) current[index] = command else current.add(command)
        saveCommands(current)
    }

    suspend fun deleteCommand(id: String) {
        saveCommands(commandsFlow.first().filterNot { it.id == id })
    }

    suspend fun setListening(enabled: Boolean) {
        context.alfStore.edit { it[Keys.listeningEnabled] = enabled }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = settingsFlow.first()
        val updated = transform(current)
        context.alfStore.edit {
            it[Keys.wakeWord] = updated.wakeWord
            if (updated.wakeWordAudioFile == null) it.remove(Keys.wakeWordAudioFile) else it[Keys.wakeWordAudioFile] = updated.wakeWordAudioFile
            it[Keys.confidence] = updated.confidenceThreshold
            it[Keys.listeningEnabled] = updated.listeningEnabled
            it[Keys.screenWake] = updated.screenWakeEnabled
            it[Keys.screenOff] = updated.screenOffEnabled
            it[Keys.notifications] = updated.notificationsEnabled
            it[Keys.firstRun] = updated.firstRunCompleted
        }
    }

    suspend fun setPinRecord(salt: String?, hash: String?) {
        context.alfStore.edit {
            if (salt == null) it.remove(Keys.pinSalt) else it[Keys.pinSalt] = salt
            if (hash == null) it.remove(Keys.pinHash) else it[Keys.pinHash] = hash
        }
    }

    suspend fun getPinRecord(): Pair<String, String>? {
        val p = context.alfStore.data.first()
        val salt = p[Keys.pinSalt] ?: return null
        val hash = p[Keys.pinHash] ?: return null
        return salt to hash
    }

    private fun encodeCommands(commands: List<VoiceCommand>): String {
        val array = JSONArray()
        commands.forEach { command ->
            array.put(JSONObject().apply {
                put("id", command.id)
                put("name", command.name)
                put("triggerPhrase", command.triggerPhrase)
                put("actionType", command.actionType.name)
                put("targetPackage", command.targetPackage ?: JSONObject.NULL)
                put("targetAppName", command.targetAppName ?: JSONObject.NULL)
                put("audioFile", command.audioFile ?: JSONObject.NULL)
                put("enabled", command.enabled)
                put("createdAt", command.createdAt)
            })
        }
        return array.toString()
    }

    private fun decodeCommands(raw: String?): List<VoiceCommand> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val o = array.getJSONObject(index)
                    add(
                        VoiceCommand(
                            id = o.optString("id"),
                            name = o.optString("name", "Command"),
                            triggerPhrase = o.optString("triggerPhrase", ""),
                            actionType = ActionType.from(o.optString("actionType")),
                            targetPackage = o.optString("targetPackage").takeIf { it.isNotBlank() && it != "null" },
                            targetAppName = o.optString("targetAppName").takeIf { it.isNotBlank() && it != "null" },
                            audioFile = o.optString("audioFile").takeIf { it.isNotBlank() && it != "null" },
                            enabled = o.optBoolean("enabled", true),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
