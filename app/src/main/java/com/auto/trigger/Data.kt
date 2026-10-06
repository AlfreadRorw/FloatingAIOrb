package com.auto.trigger
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Act(val delay: Long, val x1: Int, val y1: Int, val x2: Int, val y2: Int, val dur: Long)
data class Macro(var name: String, val acts: MutableList<Act>, var loops: Int = 1, var gap: Long = 500, var pinned: Boolean = false, var land: Boolean = false,
                 var icon: Int = 0, var color: Int = 0, var speed: Int = 100, var chain: String = "", var ix: Int = -1, var iy: Int = -1) {
    fun loopMs(): Long = acts.sumOf { it.delay + it.dur }
}

fun spdTxt(v: Int): String { val s = String.format(Locale.US, "%.2f", v / 100.0).trimEnd('0').trimEnd('.'); return s + "x" }
fun secTxt(ms: Long): String = if (ms >= 60000) String.format(Locale.US, "%d:%02d mnt", ms / 60000, ms / 1000 % 60) else String.format(Locale.US, "%.1f dtk", ms / 1000.0)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("at", 0)
    private fun fromJ(o: JSONObject): Macro {
        val a = o.getJSONArray("a"); val l = mutableListOf<Act>()
        for (j in 0 until a.length()) { val r = a.getJSONArray(j); l.add(Act(r.getLong(0), r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4), r.getLong(5))) }
        return Macro(o.getString("n"), l, o.getInt("l"), o.getLong("g"), o.optBoolean("p"), o.optBoolean("r"),
            o.optInt("i", 0), o.optInt("c", 0), o.optInt("s", 100).coerceIn(10, 500), o.optString("h", ""), o.optInt("x", -1), o.optInt("y", -1))
    }
    private fun toJ(m: Macro) = JSONObject().put("n", m.name).put("l", m.loops).put("g", m.gap).put("p", m.pinned).put("r", m.land)
        .put("i", m.icon).put("c", m.color).put("s", m.speed).put("h", m.chain).put("x", m.ix).put("y", m.iy)
        .put("a", JSONArray().also { a -> m.acts.forEach { a.put(JSONArray(listOf(it.delay, it.x1, it.y1, it.x2, it.y2, it.dur))) } })
    fun load(c: Context): MutableList<Macro> {
        val out = mutableListOf<Macro>(); val arr = JSONArray(sp(c).getString("macros", "[]"))
        for (i in 0 until arr.length()) out.add(fromJ(arr.getJSONObject(i)))
        return out
    }
    fun save(c: Context, ms: List<Macro>) {
        val arr = JSONArray(); ms.forEach { arr.put(toJ(it)) }
        sp(c).edit().putString("macros", arr.toString()).apply()
    }
    fun raw(c: Context): String = sp(c).getString("macros", "[]")!!
    fun setRaw(c: Context, s: String) = sp(c).edit().putString("macros", s).apply()
    private fun clip(c: Context, s: String) { (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("macros", s)) }
    fun exportTo(c: Context) = clip(c, raw(c))
    fun exportOne(c: Context, m: Macro) = clip(c, JSONArray().put(toJ(m)).toString())
    fun importFrom(c: Context): Int {
        val old = raw(c)
        return try {
            val t = (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text.toString()
            setRaw(c, t); val imp = load(c); setRaw(c, old); val cur = load(c)
            imp.forEach { var n = it.name; while (cur.any { m -> m.name == n }) n += "'"; it.name = n; it.pinned = false; it.chain = ""; it.ix = -1; it.iy = -1; cur.add(it) }
            save(c, cur); imp.size
        } catch (e: Exception) { setRaw(c, old); -1 }
    }
}
