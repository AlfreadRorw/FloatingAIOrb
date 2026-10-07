package com.alfread.alfvision.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val profile: String
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)],
    indices = [androidx.room.Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: Long,
    val conversationId: Long,
    val role: String,
    val content: String,
    val timestamp: Long,
    val model: String,
    val imagePath: String?,
    val regionJson: String?,
    val tokenInput: Int?,
    val tokenOutput: Int?
)

@Entity(tableName = "region_presets")
data class RegionPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotation: Int,
    val displayId: Int,
    val createdAt: Long
)

@Entity(tableName = "ai_profiles")
data class AIProfileEntity(
    @PrimaryKey val name: String,
    val systemPrompt: String,
    val temperature: Float,
    val maxTokens: Int,
    val preferredModel: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)
