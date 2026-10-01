package com.alfread.statusdownloader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(p.getString("theme", "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM),
        includeBusiness = p.getBoolean("business", true),
        moveMode = p.getBoolean("move", false),
        gridColumns = p.getInt("grid", 3).coerceIn(2, 4),
        prefix = p.getString("prefix", "Alfread_") ?: "Alfread_",
        newestFirst = p.getBoolean("newest", true)
    )

    fun save(s: AppSettings) {
        p.edit()
            .putString("theme", s.themeMode.name)
            .putBoolean("business", s.includeBusiness)
            .putBoolean("move", s.moveMode)
            .putInt("grid", s.gridColumns)
            .putString("prefix", s.prefix)
            .putBoolean("newest", s.newestFirst)
            .apply()
    }
}

class HistoryStore(context: Context) {
    private val p = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(): List<HistoryRecord> = runCatching {
        val arr = JSONArray(p.getString("items", "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HistoryRecord(
                id = o.getString("id"),
                originalName = o.getString("orig"),
                savedPath = o.getString("path"),
                kind = MediaKind.valueOf(o.getString("kind")),
                size = o.getLong("size"),
                time = o.getLong("time"),
                origin = o.optString("origin", "WhatsApp")
            )
        }
    }.getOrDefault(emptyList())

    fun save(list: List<HistoryRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("orig", r.originalName)
                    .put("path", r.savedPath)
                    .put("kind", r.kind.name)
                    .put("size", r.size)
                    .put("time", r.time)
                    .put("origin", r.origin)
            )
        }
        p.edit().putString("items", arr.toString()).apply()
    }
}


class DeletedMessageStore(context: Context) {
    private val p = context.getSharedPreferences("deleted_messages", Context.MODE_PRIVATE)

    fun load(): List<DeletedMessage> = runCatching {
        val arr = JSONArray(p.getString("items", "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DeletedMessage(
                id = o.getString("id"),
                packageName = o.optString("package", "com.whatsapp"),
                sender = o.optString("sender", "WhatsApp"),
                text = o.optString("text", ""),
                type = runCatching {
                    DeletedMessageType.valueOf(o.optString("type", "UNKNOWN"))
                }.getOrDefault(DeletedMessageType.UNKNOWN),
                time = o.optLong("time", 0L),
                deletedMarker = o.optBoolean("deleted", false),
                mediaPath = o.optString("mediaPath", "").ifBlank { null }
            )
        }
    }.getOrDefault(emptyList())

    fun save(list: List<DeletedMessage>) {
        val arr = JSONArray()
        list.take(500).forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("package", r.packageName)
                    .put("sender", r.sender)
                    .put("text", r.text)
                    .put("type", r.type.name)
                    .put("time", r.time)
                    .put("deleted", r.deletedMarker)
                    .put("mediaPath", r.mediaPath ?: "")
            )
        }
        p.edit().putString("items", arr.toString()).apply()
    }
}
