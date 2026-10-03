package com.alfread.alfdownloader.data

import android.content.Context
import com.alfread.alfdownloader.model.Job
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val serializer = ListSerializer(Job.serializer())

    fun read(): List<Job> = runCatching {
        json.decodeFromString(serializer, prefs.getString("items", "[]") ?: "[]")
    }.getOrDefault(emptyList())

    private fun write(items: List<Job>) {
        prefs.edit().putString("items", json.encodeToString(serializer, items)).apply()
    }

    fun add(job: Job) {
        write((listOf(job) + read()).distinctBy { it.id }.take(300))
    }

    fun remove(id: String) {
        write(read().filterNot { it.id == id })
    }

    fun clear() {
        prefs.edit().remove("items").apply()
    }
}
