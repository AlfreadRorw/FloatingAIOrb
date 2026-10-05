package com.alfread.alfvoicecontrol.model

import java.util.UUID

enum class ActionType {
    SCREEN_ON,
    SCREEN_OFF,
    OPEN_APP;

    companion object {
        fun from(value: String?): ActionType = entries.firstOrNull { it.name == value } ?: SCREEN_ON
    }
}

data class VoiceCommand(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val triggerPhrase: String,
    val actionType: ActionType,
    val targetPackage: String? = null,
    val targetAppName: String? = null,
    val audioFile: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class AppTarget(
    val packageName: String,
    val label: String
)

data class AppSettings(
    val listeningEnabled: Boolean = false,
    val wakeWord: String = "Alf",
    val wakeWordAudioFile: String? = null,
    val confidenceThreshold: Float = 0.68f,
    val screenWakeEnabled: Boolean = true,
    val screenOffEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val firstRunCompleted: Boolean = false
)
