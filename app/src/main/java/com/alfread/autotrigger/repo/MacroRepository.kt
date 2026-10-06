package com.alfread.autotrigger.repo

import android.content.Context
import com.alfread.autotrigger.model.Action
import com.alfread.autotrigger.model.Macro
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MacroRepository(context: Context) {
    private val prefs = context.getSharedPreferences("alf_macros", Context.MODE_PRIVATE)

    fun all(): MutableList<Macro> {
        val raw = prefs.getString("data", "[]") ?: "[]"
        return runCatching { decode(JSONArray(raw)) }.getOrElse { mutableListOf() }
    }

    fun save(macros: List<Macro>) {
        val arr = JSONArray()
        macros.forEach { arr.put(encode(it)) }
        prefs.edit().putString("data", arr.toString()).apply()
    }

    fun upsert(macro: Macro) {
        val list = all()
        val idx = list.indexOfFirst { it.id == macro.id }
        if (idx >= 0) list[idx] = macro else list.add(macro)
        save(list)
    }

    fun remove(id: String) = save(all().filterNot { it.id == id })

    companion object {
        fun newMacro(name: String) = Macro(UUID.randomUUID().toString(), name)

        private fun encode(m: Macro): JSONObject {
            val o = JSONObject().apply {
                put("id", m.id); put("name", m.name); put("loop", m.loop)
                put("speed", m.speed.toDouble()); put("enabled", m.enabled)
            }
            val a = JSONArray()
            m.actions.forEach { x ->
                val e = JSONObject()
                when (x) {
                    is Action.Tap -> { e.put("t", "tap"); e.put("x", x.x.toDouble()); e.put("y", x.y.toDouble()); e.put("hold", x.holdMs) }
                    is Action.Swipe -> { e.put("t", "swipe"); e.put("x1", x.x1.toDouble()); e.put("y1", x.y1.toDouble()); e.put("x2", x.x2.toDouble()); e.put("y2", x.y2.toDouble()); e.put("d", x.durationMs) }
                    is Action.Delay -> { e.put("t", "delay"); e.put("d", x.durationMs) }
                }
                a.put(e)
            }
            o.put("actions", a)
            return o
        }

        private fun decode(arr: JSONArray): MutableList<Macro> {
            val out = mutableListOf<Macro>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val m = Macro(o.getString("id"), o.optString("name", "Macro"), o.optBoolean("loop"), o.optDouble("speed", 1.0).toFloat(), o.optBoolean("enabled", true))
                val a = o.optJSONArray("actions") ?: JSONArray()
                for (j in 0 until a.length()) {
                    val e = a.getJSONObject(j)
                    when (e.getString("t")) {
                        "tap" -> m.actions.add(Action.Tap(e.getDouble("x").toFloat(), e.getDouble("y").toFloat(), e.optLong("hold", 20)))
                        "swipe" -> m.actions.add(Action.Swipe(e.getDouble("x1").toFloat(), e.getDouble("y1").toFloat(), e.getDouble("x2").toFloat(), e.getDouble("y2").toFloat(), e.optLong("d", 250)))
                        "delay" -> m.actions.add(Action.Delay(e.optLong("d", 100)))
                    }
                }
                out.add(m)
            }
            return out
        }
    }
}
