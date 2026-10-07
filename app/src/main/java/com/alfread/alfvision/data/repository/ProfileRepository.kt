package com.alfread.alfvision.data.repository

import com.alfread.alfvision.core.Constants
import com.alfread.alfvision.data.local.AIProfileEntity
import com.alfread.alfvision.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val database: AppDatabase) {
    fun observe(): Flow<List<AIProfileEntity>> = database.aiProfileDao().observeAll()

    suspend fun ensureDefaults() {
        val dao = database.aiProfileDao()
        val now = System.currentTimeMillis()
        val defaults = listOf(
            "General" to "You are a practical visual AI assistant. Answer clearly and do not invent details.",
            "Coding" to "Focus on code, errors, logs, architecture, and concrete fixes. Explain assumptions.",
            "Gaming" to "Analyze the game screen tactically. Give concise, practical advice without controlling the game.",
            "Translator" to "Translate visible text accurately. Preserve names, numbers, formatting, and context.",
            "Android" to "Diagnose Android UI, settings, permissions, Logcat, build errors, and Gradle issues.",
            "Minecraft" to "Analyze Minecraft UI/gameplay and explain mechanics, errors, settings, or next actions.",
            "MLBB" to "Analyze Mobile Legends: Bang Bang screenshots and give strategy or UI explanations without automation.",
            "Homework" to "Explain the visible homework step by step and prefer teaching over simply giving an answer."
        )
        defaults.forEach { (name, prompt) ->
            if (dao.get(name) == null) {
                dao.upsert(AIProfileEntity(name, prompt, 0.7f, 2048, Constants.DEFAULT_MODEL, now, now))
            }
        }
    }

    suspend fun get(name: String) = database.aiProfileDao().get(name)
    suspend fun save(profile: AIProfileEntity) = database.aiProfileDao().upsert(profile)
    suspend fun delete(name: String) = database.aiProfileDao().delete(name)
}
