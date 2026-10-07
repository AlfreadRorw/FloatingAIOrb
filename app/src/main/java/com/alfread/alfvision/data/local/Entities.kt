package com.alfread.alfvision.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val profileId: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val pinnedImagePath: String? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val timestamp: Long,
    val imagePath: String? = null,
    val model: String? = null,
    val tokenUsage: Int? = null
)

@Entity(tableName = "region_presets")
data class RegionPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val displayId: Int,
    val rotation: Int,
    val createdAt: Long
)

@Entity(tableName = "ai_profiles")
data class AIProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val systemPrompt: String,
    val temperature: Float,
    val maxTokens: Int,
    val preferredModel: String?,
    val builtIn: Boolean
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val theme: String,
    val accent: String,
    val model: String,
    val profileId: Long,
    val temperature: Float,
    val maxTokens: Int,
    val responseStyle: String,
    val saveHistory: Boolean,
    val autoDeleteDays: Int,
    val debugLogging: Boolean,
    val networkTimeoutSeconds: Long,
    val retryCount: Int,
    val shizukuEnhanced: Boolean,
    val savedAt: Long
)
