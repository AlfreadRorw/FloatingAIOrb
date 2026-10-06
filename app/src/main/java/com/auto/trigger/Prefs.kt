package com.auto.trigger
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import kotlin.reflect.KProperty

object S {
    lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("at", 0) }
    class P(val k: String, val d: Int) {
        operator fun getValue(t: Any?, p: KProperty<*>) = sp.getInt(k, d)
        operator fun setValue(t: Any?, p: KProperty<*>, v: Int) = sp.edit().putInt(k, v).apply()
    }
    var theme by P("theme", 0)      // 0 hitam, 1 putih, 2 amoled
    var alpha by P("alpha", 95)
    var hLen by P("hlen", 90)
    var hW by P("hw", 6)
    var hAlpha by P("halpha", 80)
    var haptic by P("haptic", 1)
    var speed by P("speed", 100)      // 10 = 0.10x, 100 = 1.00x, 300 = 3.00x
    var dock by P("dock", 1)
    var countdown by P("cd", 0)
    var iconSize by P("isz", 48)
    var dx by P("dx", -1)
    var dy by P("dy", -1)
    var jitter by P("jit", 0)
    var jitterT by P("jitt", 0)
    var autoStop by P("astop", 0)
    var cps by P("cps", 12)
    var gameProfile by P("game_profile", 0) // 0 normal, 1 ML/game, 2 stealth
    var iconAlpha by P("ialpha", 100)
    var hx by P("hx", 0)
    var hy by P("hy", 600)
    var hxl by P("hxl", 0)
    var hyl by P("hyl", 250)
}

object T {
    val bg get() = intArrayOf(0xFF121212.toInt(), 0xFFFAFAFA.toInt(), 0xFF000000.toInt())[S.theme]
    val card get() = intArrayOf(0xFF1F1F1F.toInt(), 0xFFEBEBEB.toInt(), 0xFF0E0E0E.toInt())[S.theme]
    val fg get() = if (S.theme == 1) Color.BLACK else Color.WHITE
    val sub get() = (fg and 0xFFFFFF) or 0x99000000.toInt()
    val line get() = if (S.theme == 1) 0x33000000 else 0x33FFFFFF
    val hot = 0xFFFF3B30.toInt()
    val green = 0xFF30D158.toInt()
    val orange = 0xFFFF9F0A.toInt()
    fun bgA() = (bg and 0xFFFFFF) or ((S.alpha * 255 / 100) shl 24)
}
