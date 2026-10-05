package com.alfread.alfvoicecontrol.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single user-defined voice command, persisted locally with Room.
 * Nothing in this table is ever uploaded anywhere - see [AppDatabase].
 */
@Entity(tableName = "voice_commands")
data class VoiceCommand(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val triggerPhrase: String,
    val actionType: ActionType,
    val targetPackage: String? = null,
    val targetAppLabel: String? = null,
    val audioFilePath: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
