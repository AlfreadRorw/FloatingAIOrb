package com.auto.trigger
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

object Shell {
    @Volatile var svc: IShellService? = null
    private var binding = false
    fun available() = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
    fun granted() = try { available() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }
    fun ready() = svc != null
    fun bind(ctx: Context) {
        if (svc != null || binding || !granted()) return
        binding = true
        val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, ShellService::class.java.name)).daemon(false).processNameSuffix("shell").version(3)
        Shizuku.bindUserService(args, object : ServiceConnection {
            override fun onServiceConnected(n: ComponentName?, b: IBinder?) { svc = IShellService.Stub.asInterface(b); binding = false; Thread { Touch.init() }.start() }
            override fun onServiceDisconnected(n: ComponentName?) { svc = null; binding = false; Touch.ok = false; Touch.canRead = false }
        })
    }
    fun run(cmd: String) { try { svc?.exec(cmd) } catch (_: Exception) {} }
    fun inj(a: Int, x: Int, y: Int): Boolean = injM(0, a, x, y)
    fun injM(slot: Int, a: Int, x: Int, y: Int): Boolean = try { svc?.injM(slot, a, x, y) == true } catch (e: Exception) { false }
    fun release() { for (s in 0..9) injM(s, 2, 0, 0) }
}

object Touch {
    @Volatile var ok = false          // bisa menyuntik sentuhan langsung
    @Volatile var canRead = false     // bisa membaca sentuhan mentah (rekam tanpa blokir layar)
    @Volatile var diag = ""
    var maxX = 1; var maxY = 1
    fun init() {
        try {
            val p = (Shell.svc?.info() ?: "").split(',', limit = 6)
            if (p.size >= 5) {
                maxX = p[1].toIntOrNull() ?: 1; maxY = p[2].toIntOrNull() ?: 1
                ok = p[3] == "1" && maxX > 1; canRead = p[4] == "1" && maxX > 1; diag = p.getOrElse(5) { "" }
            } else { ok = false; canRead = false; diag = "service Shizuku lama / info kosong. Force stop app lalu buka lagi" }
        } catch (e: Exception) { ok = false; canRead = false; diag = "info() gagal: ${e.javaClass.simpleName} ${e.message}" }
    }
    // raw panel (portrait) <-> koordinat layar sesuai rotasi. w,h = ukuran portrait
    fun toDisp(rx: Int, ry: Int, rot: Int, w: Int, h: Int): Pair<Int, Int> {
        val nx = rx.toFloat() * w / (maxX + 1); val ny = ry.toFloat() * h / (maxY + 1)
        val (x, y) = when (rot) { 1 -> ny to (w - nx); 2 -> (w - nx) to (h - ny); 3 -> (h - ny) to nx; else -> nx to ny }
        return x.toInt() to y.toInt()
    }
    fun toRaw(x: Int, y: Int, rot: Int, w: Int, h: Int): Pair<Int, Int> {
        val (nx, ny) = when (rot) { 1 -> (w - y) to x; 2 -> (w - x) to (h - y); 3 -> y to (h - x); else -> x to y }
        return (nx * (maxX + 1) / w).coerceIn(0, maxX) to (ny * (maxY + 1) / h).coerceIn(0, maxY)
    }
}

/** Mengubah event mentah touchscreen jadi daftar aksi (tap / swipe / tahan) */
class Recorder(private val list: MutableList<Act>, private val conv: (Int, Int) -> Pair<Int, Int>) {
    var ignore: (Int, Int) -> Boolean = { _, _ -> false }
    private var slot = 0; private var x = 0; private var y = 0
    private var down = false; private var pd = false; private var pu = false
    private var t0 = 0L; private var sx = 0; private var sy = 0; private var lastUp = 0L
    fun feed(line: String) {
        val p = line.split('|'); if (p.size < 4) return
        val ts = p[0].toLong(); val t = p[1].toInt(); val c = p[2].toInt(); val v = p[3].toInt()
        if (t == 3) {
            when (c) {
                47 -> slot = v
                53 -> if (slot == 0) x = v
                54 -> if (slot == 0) y = v
                57 -> if (slot == 0) { if (v >= 0) pd = true else pu = true }
            }
        } else if (t == 0) {
            if (pd) { pd = false; down = true; t0 = ts; sx = x; sy = y }
            if (pu) {
                pu = false
                if (down) {
                    down = false
                    val (a, b) = conv(sx, sy); val (c2, d) = conv(x, y)
                    if (!ignore(a, b)) synchronized(list) {
                        list.add(Act(if (lastUp == 0L) 300 else (t0 - lastUp).coerceAtLeast(0), a, b, c2, d, (ts - t0).coerceAtLeast(40))); lastUp = ts
                    }
                }
            }
        }
    }
}
