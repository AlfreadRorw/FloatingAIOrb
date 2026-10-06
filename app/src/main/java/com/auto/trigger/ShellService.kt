package com.auto.trigger
import androidx.annotation.Keep
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Keep
class ShellService : IShellService.Stub() {
    private var path = ""; private var maxX = 0; private var maxY = 0
    private var out: FileOutputStream? = null
    private var inp: FileInputStream? = null
    private var tid = 100
    private val buf = ByteBuffer.allocate(24 * 16).order(ByteOrder.nativeOrder())
    private val evBuf = StringBuilder()

    init { probe() }

    private fun probe() {
        try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", "getevent -pl 2>/dev/null"))
            var cur = ""; var mx = 0
            p.inputStream.bufferedReader().forEachLine { l ->
                if (l.startsWith("add device")) { cur = l.substringAfter(": ").trim(); mx = 0 }
                else if (l.contains("ABS_MT_POSITION_X")) mx = Regex("max (\\d+)").find(l)?.groupValues?.get(1)?.toInt() ?: 0
                else if (l.contains("ABS_MT_POSITION_Y") && path.isEmpty() && mx > 0) {
                    val my = Regex("max (\\d+)").find(l)?.groupValues?.get(1)?.toInt() ?: 0
                    if (my > 0) { path = cur; maxX = mx; maxY = my }
                }
            }
            if (path.isNotEmpty()) out = FileOutputStream(path)
        } catch (e: Exception) { out = null }
    }

    override fun destroy() { System.exit(0) }
    override fun exec(cmd: String): Int = try { Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor() } catch (e: Exception) { -1 }
    override fun info(): String = if (out == null) "" else "$path,$maxX,$maxY"

    override fun inj(action: Int, x: Int, y: Int): Boolean {
        val o = out ?: return false
        return try {
            buf.clear()
            fun e(t: Int, c: Int, v: Int) { buf.putLong(0); buf.putLong(0); buf.putShort(t.toShort()); buf.putShort(c.toShort()); buf.putInt(v) }
            when (action) {
                0 -> { if (tid > 60000) tid = 100; e(3, 47, 0); e(3, 57, ++tid); e(1, 330, 1); e(3, 53, x); e(3, 54, y); e(3, 48, 6); e(3, 58, 60) }
                1 -> { e(3, 53, x); e(3, 54, y) }
                else -> { e(3, 57, -1); e(1, 330, 0) }
            }
            e(0, 0, 0)
            o.write(buf.array(), 0, buf.position()); o.flush(); true
        } catch (ex: Exception) { false }
    }

    override fun startEvents() {
        stopEvents(); synchronized(evBuf) { evBuf.setLength(0) }
        if (path.isEmpty()) return
        val s = FileInputStream(path); inp = s
        Thread {
            try {
                val b = ByteArray(24)
                while (true) {
                    var n = 0
                    while (n < 24) { val r = s.read(b, n, 24 - n); if (r < 0) return@Thread; n += r }
                    val bb = ByteBuffer.wrap(b).order(ByteOrder.nativeOrder())
                    val sec = bb.getLong(); val us = bb.getLong(); val t = bb.getShort().toInt(); val c = bb.getShort().toInt(); val v = bb.getInt()
                    if (t == 3 || t == 0) synchronized(evBuf) { evBuf.append(sec * 1000 + us / 1000).append('|').append(t).append('|').append(c).append('|').append(v).append('\n') }
                }
            } catch (_: Exception) {}
        }.apply { isDaemon = true; start() }
    }
    override fun drainEvents(): String = synchronized(evBuf) { val s = evBuf.toString(); evBuf.setLength(0); s }
    override fun stopEvents() { try { inp?.close() } catch (_: Exception) {}; inp = null }
}
