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
    var accent by P("accent", 0)
    var shape by P("shape", 0)      // 0 bulat, 1 kotak membulat
    var showName by P("sname", 1)
    var alpha by P("alpha", 95)
    var hLen by P("hlen", 90)
    var hW by P("hw", 6)
    var hAlpha by P("halpha", 80)
    var haptic by P("haptic", 1)
    var speed by P("speed", 100)    // persen, 10..500
    var turbo by P("turbo", 200)    // pengali saat turbo
    var dock by P("dock", 1)
    var countdown by P("cd", 0)
    var iconSize by P("isz", 48)
    var dx by P("dx", -1)
    var dy by P("dy", -1)
    var jitter by P("jit", 0)
    var jitterT by P("jitt", 0)
    var autoStop by P("astop", 0)
    var cps by P("cps", 12)
    var iconAlpha by P("ialpha", 100)
    var hx by P("hx", 0)
    var hy by P("hy", 600)
    var hxl by P("hxl", 0)
    var hyl by P("hyl", 250)
    var autoHide by P("ahide", 0)
    var lockPos by P("lock", 0)
    var panelW by P("pw", 320)
    var tMode by P("tmode", 0)      // 0 tap, 1 tahan, 2 burst
    var tSize by P("tsz", 58)
    var tAlpha by P("talpha", 100)
    var burstN by P("burstn", 5)
    var burstGap by P("burstg", 400)
    var scaleDur by P("sdur", 0)
    @Volatile var turboOn = false

    fun resetPos() { dx = -1; dy = -1; hx = 0; hy = 600; hxl = 0; hyl = 250 }
    fun resetAll() { val m = sp.getString("macros", "[]"); sp.edit().clear().putString("macros", m).apply() }
}

object T {
    val bg get() = intArrayOf(0xFF121212.toInt(), 0xFFFAFAFA.toInt(), 0xFF000000.toInt())[S.theme.coerceIn(0, 2)]
    val card get() = intArrayOf(0xFF1F1F1F.toInt(), 0xFFEBEBEB.toInt(), 0xFF0E0E0E.toInt())[S.theme.coerceIn(0, 2)]
    val fg get() = if (S.theme == 1) Color.BLACK else Color.WHITE
    val sub get() = (fg and 0xFFFFFF) or 0x99000000.toInt()
    val line get() = if (S.theme == 1) 0x33000000 else 0x33FFFFFF
    val hot = 0xFFFF3B30.toInt()
    val green = 0xFF30D158.toInt()
    val orange = 0xFFFF9F0A.toInt()
    val accents = longArrayOf(0xFF0A84FF, 0xFFBF5AF2, 0xFFFF375F, 0xFFFF9F0A, 0xFF30D158, 0xFF64D2FF, 0xFFFFD60A, 0xFF5E5CE6).map { it.toInt() }
    val accent get() = accents[S.accent.coerceIn(0, accents.size - 1)]
    fun bgA() = (bg and 0xFFFFFF) or ((S.alpha * 255 / 100) shl 24)
    fun lum(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
    fun on(c: Int) = if (lum(c) > 0.62) Color.BLACK else Color.WHITE
}

/** Warna per macro. Indeks 0 = ikuti tema */
object Pal {
    val colors = longArrayOf(0, 0xFFFF3B30, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF64D2FF, 0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF375F).map { it.toInt() }
    fun of(m: Macro) = if (m.color <= 0 || m.color >= colors.size) T.fg else colors[m.color]
}

/** Daftar ikon yang bisa dipilih. Urutan jangan diubah, hanya tambah di akhir. */
object Icons {
    val res = intArrayOf(
        R.drawable.ic_bolt, R.drawable.ic_target, R.drawable.ic_gamepad, R.drawable.ic_star, R.drawable.ic_heart, R.drawable.ic_shield,
        R.drawable.ic_fire, R.drawable.ic_diamond, R.drawable.ic_crown, R.drawable.ic_rocket, R.drawable.ic_skull, R.drawable.ic_sword,
        R.drawable.ic_flag, R.drawable.ic_eye, R.drawable.ic_pointer, R.drawable.ic_sparkle, R.drawable.ic_moon, R.drawable.ic_sun,
        R.drawable.ic_heal, R.drawable.ic_hex, R.drawable.ic_up, R.drawable.ic_potion, R.drawable.ic_castle, R.drawable.ic_loop,
        R.drawable.ic_timer, R.drawable.ic_grid)
    fun of(m: Macro) = res[m.icon.coerceIn(0, res.size - 1)]
}
