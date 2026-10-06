package com.alfread.alfvision.data.repository

import com.alfread.alfvision.core.model.AiProfile
import com.alfread.alfvision.data.local.AIProfileDao
import com.alfread.alfvision.data.local.AIProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepository(private val dao: AIProfileDao) {
    fun observe(): Flow<List<AiProfile>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun get(id: Long): AiProfile? = dao.get(id)?.toModel()

    suspend fun create(profile: AiProfile): Long = dao.insert(
        AIProfileEntity(name = profile.name, systemPrompt = profile.systemPrompt, temperature = profile.temperature, maxTokens = profile.maxTokens, preferredModel = profile.preferredModel, builtIn = false)
    )

    suspend fun delete(profile: AiProfile) {
        if (!profile.builtIn) dao.delete(AIProfileEntity(profile.id, profile.name, profile.systemPrompt, profile.temperature, profile.maxTokens, profile.preferredModel, false))
    }

    suspend fun ensureDefaults() {
        if (dao.count() > 0) return
        val profiles = listOf(
            "General" to "You are ALF Vision, a helpful screen assistant. Be accurate and practical.",
            "Coding" to "Act as a senior software engineer. Analyze code, errors, APIs, and explain exact fixes.",
            "Gaming" to "Act as a game analysis assistant. Explain what is visible, mechanics, HUD information, and practical next steps without controlling the game.",
            "Translator" to "Translate visible text faithfully. Preserve names, numbers, formatting, and context.",
            "Android" to "Act as an Android engineer. Diagnose Android UI, permissions, Gradle, Kotlin, and system behavior.",
            "Minecraft" to "Act as a Minecraft assistant. Analyze screenshots for UI, items, errors, builds, and gameplay context.",
            "MLBB" to "Act as a Mobile Legends assistant. Analyze the visible HUD, map, items, heroes, and provide useful tactical explanation without automating actions.",
            "Homework" to "Act as a patient tutor. Explain the visible problem, show steps, and avoid making unsupported assumptions."
        )
        profiles.forEach { (name, prompt) ->
            dao.insert(AIProfileEntity(name = name, systemPrompt = prompt, temperature = 0.2f, maxTokens = 1024, preferredModel = null, builtIn = true))
        }
    }
}

private fun AIProfileEntity.toModel() = AiProfile(id, name, systemPrompt, temperature, maxTokens, preferredModel, builtIn)
