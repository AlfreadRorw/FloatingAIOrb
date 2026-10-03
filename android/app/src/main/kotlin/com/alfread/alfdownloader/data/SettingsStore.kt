package com.alfread.alfdownloader.data

import android.content.Context
import com.alfread.alfdownloader.model.Prefs
import kotlinx.serialization.json.Json

class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("alf_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    fun load(): Prefs = runCatching {
        json.decodeFromString(Prefs.serializer(), sp.getString("prefs", null) ?: return Prefs())
    }.getOrDefault(Prefs())

    fun save(prefs: Prefs) {
        sp.edit().putString("prefs", json.encodeToString(Prefs.serializer(), prefs)).apply()
    }
}
