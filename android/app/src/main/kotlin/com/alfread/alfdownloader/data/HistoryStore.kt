package com.alfread.alfdownloader.data

import android.content.Context
import com.alfread.alfdownloader.model.Job
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): List<Job> = runCatching {
        json.decodeFromString(ListSerializer(Job.serializer()), prefs.getString("items", "[]") ?: "[]")
    }.getOrDefault(emptyList())

    fun add(job: Job) {
        val items = (listOf(job) + read()).distinctBy { it.id }.take(100)
        prefs.edit().putString("items", json.encodeToString(ListSerializer(Job.serializer()), items)).apply()
    }

    fun clear() { prefs.edit().remove("items").apply() }
}
