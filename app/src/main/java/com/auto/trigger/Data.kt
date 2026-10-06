package com.auto.trigger
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Act(val delay: Long, val x1: Int, val y1: Int, val x2: Int, val y2: Int, val dur: Long)
data class Macro(var name: String, val acts: MutableList<Act>, var loops: Int = 1, var gap: Long = 500, var pinned: Boolean = false, var land: Boolean = false)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("at", 0)
    fun load(c: Context): MutableList<Macro> {
        val out = mutableListOf<Macro>(); val arr = JSONArray(sp(c).getString("macros", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); val a = o.getJSONArray("a"); val l = mutableListOf<Act>()
            for (j in 0 until a.length()) { val r = a.getJSONArray(j); l.add(Act(r.getLong(0), r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4), r.getLong(5))) }
            out.add(Macro(o.getString("n"), l, o.getInt("l"), o.getLong("g"), o.optBoolean("p"), o.optBoolean("r")))
        }
        return out
    }
    fun save(c: Context, ms: List<Macro>) {
        val arr = JSONArray()
        ms.forEach { m -> arr.put(JSONObject().put("n", m.name).put("l", m.loops).put("g", m.gap).put("p", m.pinned).put("r", m.land)
            .put("a", JSONArray().also { a -> m.acts.forEach { a.put(JSONArray(listOf(it.delay, it.x1, it.y1, it.x2, it.y2, it.dur))) } })) }
        sp(c).edit().putString("macros", arr.toString()).apply()
    }
    fun raw(c: Context): String = sp(c).getString("macros", "[]")!!
    fun setRaw(c: Context, s: String) = sp(c).edit().putString("macros", s).apply()
    fun exportTo(c: Context) { (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("macros", raw(c))) }
    fun importFrom(c: Context): Int {
        val old = raw(c)
        return try {
            val t = (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text.toString()
            setRaw(c, t); val imp = load(c); setRaw(c, old); val cur = load(c)
            imp.forEach { var n = it.name; while (cur.any { m -> m.name == n }) n += "'"; it.name = n; it.pinned = false; cur.add(it) }
            save(c, cur); imp.size
        } catch (e: Exception) { setRaw(c, old); -1 }
    }
}
