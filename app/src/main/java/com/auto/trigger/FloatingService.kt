package com.auto.trigger
import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.random.Random

class FloatingService : Service() {
    companion object { @Volatile var inst: FloatingService? = null }
    class Runner(val m: Macro) { @Volatile var paused = false; @Volatile var loop = 0; @Volatile var idx = 0; var job: Job? = null }

    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val runners = HashMap<String, Runner>()
    private val icons = HashMap<String, View>()
    private var macros = mutableListOf<Macro>()
    private var handle: View? = null; private var handleLine: View? = null
    private var panel: View? = null; private var dock: View? = null
    private var rec: View? = null; private var recBar: View? = null; private var recJob: Job? = null
    private var barRect = Rect()
    private var target: View? = null; private var tp: WindowManager.LayoutParams? = null; private var clicker: Job? = null
    private var pendingName = ""; private var lastRun: String? = null
    private lateinit var hp: WindowManager.LayoutParams

    // ---------- layar ----------
    private val disp get() = (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
    private fun real() = Point().also { disp.getRealSize(it) }
    private val sw get() = real().x
    private val sh get() = real().y
    private fun rot() = disp.rotation
    private fun portrait() = real().let { minOf(it.x, it.y) to maxOf(it.x, it.y) }
    private fun isLand() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun lp(w: Int, h: Int, x: Int = 0, y: Int = 0, focus: Boolean = false, touch: Boolean = true) = WindowManager.LayoutParams(w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or (if (focus) WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            (if (touch) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE), PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y; if (focus) softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate(); S.init(this); inst = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("at", "Auto Trigger", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, "at").setContentTitle("Auto Trigger aktif").setSmallIcon(R.drawable.ic_bolt).build())
        Shell.bind(this); macros = Store.load(this); rebuildAll()
    }
    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); ui.postDelayed({ rebuildAll() }, 350) }
    fun reload() { macros = Store.load(this); rebuildAll() }
    fun showPanel() { openPanel() }
    fun runByName(n: String) { macros.find { it.name == n }?.let { toggleRun(it) } }
    fun toggleTargetPublic() = toggleTarget()
    fun selfTest() {
        if (!Shell.ready()) { toast("Shizuku belum terhubung"); return }
        val (w, h) = real().let { it.x to it.y }
        scope.launch { delay(600); perform(Act(0, w / 2, h * 7 / 10, w / 2, h * 3 / 10, 450)) }
        toast("Tes: geser layar ke atas")
    }

    fun rebuildAll() {
        val open = panel != null; closePanel(); buildHandle(); buildDock()
        icons.keys.toList().forEach { removeIcon(it) }; macros.filter { it.pinned }.forEach { addIcon(it) }
        refreshRun(); if (open) openPanel()
    }

    // ================= drag helper =================
    private fun drag(touch: View, win: View, p: WindowManager.LayoutParams, snap: Boolean = false, onTap: () -> Unit = {}, onLong: (() -> Unit)? = null, onEnd: () -> Unit = {}) {
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false; var long = false
        val lr = Runnable { if (!moved) { long = true; hap(touch); onLong?.invoke() } }
        touch.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = p.x; sy = p.y; moved = false; long = false; if (onLong != null) ui.postDelayed(lr, 550) }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && (abs(e.rawX - dx) > dp(8) || abs(e.rawY - dy) > dp(8))) { moved = true; ui.removeCallbacks(lr); hap(touch) }
                    if (moved) {
                        p.x = (sx + e.rawX - dx).toInt().coerceIn(0, maxOf(0, sw - win.width)); p.y = (sy + e.rawY - dy).toInt().coerceIn(0, maxOf(0, sh - win.height))
                        wm.updateViewLayout(win, p)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(lr)
                    if (moved) { if (snap) { p.x = if (p.x + win.width / 2 < sw / 2) 0 else sw - win.width; wm.updateViewLayout(win, p) }; onEnd() }
                    else if (!long) { hap(touch); onTap() }
                }
            }
            true
        }
    }

    // ================= Garis pintar =================
    private fun buildHandle() {
        handle?.let { wm.removeView(it) }
        val box = FrameLayout(this); val line = View(this); handleLine = line
        box.addView(line, FrameLayout.LayoutParams(dp(S.hW), dp(S.hLen), Gravity.CENTER_VERTICAL or Gravity.START))
        val land = isLand(); val sx = if (land) S.hxl else S.hx; val sy = if (land) S.hyl else S.hy
        hp = lp(dp(S.hW) + dp(18), dp(S.hLen) + dp(12), 0, sy.coerceIn(0, maxOf(0, sh - dp(S.hLen) - dp(12))))
        hp.x = if (sx < sw / 2) 0 else sw - hp.width
        fun side() { (line.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER_VERTICAL or (if (hp.x < sw / 2) Gravity.START else Gravity.END); line.requestLayout() }
        side(); line.alpha = S.hAlpha / 100f
        drag(box, box, hp, true, onTap = { togglePanel() }, onEnd = {
            if (isLand()) { S.hxl = hp.x; S.hyl = hp.y } else { S.hx = hp.x; S.hy = hp.y }
            side(); if (panel != null) openPanel() })
        wm.addView(box, hp); handle = box
    }

    // ================= Panel (nama -> rekam -> daftar) =================
    private fun togglePanel() { if (panel != null) closePanel() else openPanel() }
    private fun closePanel() { panel?.let { wm.removeView(it) }; panel = null }
    private fun openApp() { closePanel(); startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    private fun openPanel() {
        closePanel()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(label("AUTO TRIGGER", 12f, true).apply { letterSpacing = 0.15f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        head.addView(icon(R.drawable.ic_settings, T.fg, 32) { openApp() })
        head.addView(icon(R.drawable.ic_close, T.fg, 32) { closePanel() })
        col.addView(head)
        val nameRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(4)) }
        val et = EditText(this).apply {
            hint = "Nama trigger baru"; setText(pendingName); setSingleLine(); textSize = 14f; setTextColor(T.fg); setHintTextColor(T.sub)
            background = rb(T.card, 14); setPadding(dp(14), dp(10), dp(14), dp(10)); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            doAfterTextChanged { pendingName = it.toString() } }
        nameRow.addView(et)
        nameRow.addView(icon(R.drawable.ic_rec, Color.WHITE, 46, T.hot) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(et.windowToken, 0); startRecord() })
        col.addView(nameRow)
        if (!Shell.ready()) col.addView(label("Shizuku belum terhubung", 11f, false, T.hot))
        if (macros.isEmpty()) col.addView(label("Isi nama, tekan rekam, lakukan aksi di layar, lalu simpan.", 11f, false, T.sub).apply { setPadding(0, dp(8), 0, 0) })
        macros.toList().forEach { m ->
            val r = runners[m.name]
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = rb(T.card, 14); setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) } }
            row.addView(icon(if (r != null && !r.paused) R.drawable.ic_pause else R.drawable.ic_play, if (r == null) T.bg else Color.WHITE, 40, if (r == null) T.fg else if (r.paused) T.orange else T.green) { toggleRun(m); openPanel() })
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(4), 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            info.addView(label(m.name, 14f, true)); info.addView(label("${m.acts.size} aksi  |  ${if (m.loops == 0) "∞" else "${m.loops}x"}", 11f, false, T.sub))
            row.addView(info)
            row.addView(icon(R.drawable.ic_pin, if (m.pinned) T.hot else T.fg, 34, T.bg) { m.pinned = !m.pinned; if (m.pinned) addIcon(m) else removeIcon(m.name); Store.save(this, macros); openPanel() })
            row.addView(icon(R.drawable.ic_delete, T.fg, 34, T.bg) { stopRun(m.name); removeIcon(m.name); macros.remove(m); Store.save(this, macros); openPanel() })
            col.addView(row)
        }
        val pw = dp(320)
        col.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val ph = minOf(col.measuredHeight, sh * 7 / 10)
        val px = if (hp.x < sw / 2) hp.width + dp(4) else sw - pw - hp.width - dp(4)
        val py = minOf(hp.y, sh - ph - dp(30)).coerceAtLeast(dp(20))
        val sv = ScrollView(this).apply { addView(col); background = rb(T.bgA(), 22, true); alpha = 0f; scaleX = .94f; scaleY = .94f }
        wm.addView(sv, lp(pw, ph, px, py, focus = true)); panel = sv
        sv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
    }

    // ================= Dock bar =================
    private fun buildDock() {
        dock?.let { wm.removeView(it) }; dock = null
        if (S.dock != 1) return
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = rb(T.bgA(), 28, true); setPadding(dp(8), dp(6), dp(10), dp(6)) }
        val grip = ImageView(this).apply { setImageResource(R.drawable.ic_drag); setColorFilter(T.sub); layoutParams = LinearLayout.LayoutParams(dp(26), dp(36)) }
        bar.addView(grip)
        bar.addView(icon(R.drawable.ic_rec, T.hot) { openPanel() })
        bar.addView(icon(R.drawable.ic_play) { (macros.find { it.name == lastRun } ?: macros.firstOrNull())?.let { toggleRun(it) } ?: toast("Belum ada macro") })
        bar.addView(icon(R.drawable.ic_stop) { stopAll() })
        bar.addView(icon(R.drawable.ic_target) { toggleTarget() })
        bar.addView(icon(R.drawable.ic_settings) { openApp() })
        val p = lp(-2, -2, if (S.dx < 0) dp(40) else S.dx, if (S.dy < 0) sh - dp(170) else S.dy)
        drag(grip, bar, p, onEnd = { S.dx = p.x; S.dy = p.y })
        wm.addView(bar, p); dock = bar
    }

    // ================= Eksekusi =================
    private fun toggleRun(m: Macro) {
        val r = runners[m.name]
        if (r?.job?.isActive == true) { r.paused = !r.paused; refreshRun(); toast(if (r.paused) "Dijeda" else "Dilanjutkan"); return }
        run(m)
    }
    private fun stopRun(n: String) { runners.remove(n)?.job?.cancel(); refreshRun() }
    fun stopAll() { runners.values.forEach { it.job?.cancel() }; runners.clear(); stopClicker(); refreshRun() }
    private fun jt(d: Long): Long = if (S.jitterT == 0) d else (d * (1 + Random.nextInt(-S.jitterT, S.jitterT + 1) / 100.0)).toLong().coerceAtLeast(0)

    private fun run(m: Macro) {
        if (!Shell.ready()) { Shell.bind(this); toast("Shizuku belum terhubung"); return }
        if (m.land != isLand()) { toast("Macro direkam ${if (m.land) "landscape" else "portrait"}. Putar layar dulu"); return }
        val r = Runner(m); runners[m.name] = r; lastRun = m.name
        r.job = scope.launch {
            try {
                for (i in S.countdown downTo 1) { withContext(Dispatchers.Main) { toast("$i") }; delay(1000) }
                val t0 = System.currentTimeMillis()
                fun over() = S.autoStop > 0 && System.currentTimeMillis() - t0 > S.autoStop * 60000L
                while (isActive && !over() && (m.loops == 0 || r.loop < m.loops)) {
                    while (r.idx < m.acts.size && isActive && !over()) {
                        while (r.paused && isActive) delay(60)
                        val a = m.acts[r.idx]
                        delay(jt(a.delay * 100 / S.speed))
                        while (r.paused && isActive) delay(60)
                        perform(a); r.idx++
                    }
                    if (r.idx >= m.acts.size) { r.idx = 0; r.loop++; withContext(Dispatchers.Main) { refreshRun() }; delay(jt(m.gap * 100 / S.speed)) }
                }
            } finally { withContext(NonCancellable + Dispatchers.Main) { if (runners[m.name] === r) runners.remove(m.name); refreshRun() } }
        }
        refreshRun()
    }

    private suspend fun perform(a: Act) {
        val j = S.jitter
        fun r() = if (j == 0) 0 else Random.nextInt(-j, j + 1)
        val x1 = a.x1 + r(); val y1 = a.y1 + r(); val x2 = a.x2 + r(); val y2 = a.y2 + r()
        val moved = abs(a.x1 - a.x2) > 8 || abs(a.y1 - a.y2) > 8
        if (!Touch.ok) { Shell.run(if (!moved && a.dur < 200) "input tap $x1 $y1" else "input swipe $x1 $y1 $x2 $y2 ${a.dur}"); delay(a.dur); return }
        val rt = rot(); val (w, h) = portrait()
        val (ax, ay) = Touch.toRaw(x1, y1, rt, w, h); val (bx, by) = Touch.toRaw(x2, y2, rt, w, h)
        try {
            Shell.inj(0, ax, ay)
            if (!moved) delay(a.dur.coerceAtLeast(30)) else {
                val steps = (a.dur / 16).toInt().coerceAtLeast(2)
                for (i in 1..steps) { delay(a.dur / steps); Shell.inj(1, ax + (bx - ax) * i / steps, ay + (by - ay) * i / steps) }
            }
        } finally { Shell.inj(2, 0, 0) }
    }

    private fun refreshRun() {
        val any = runners.values.any { it.job?.isActive == true } || clicker?.isActive == true
        handleLine?.background = rb(if (any) T.green else T.fg, 3); handle?.keepScreenOn = any
        icons.forEach { (n, box) ->
            val r = runners[n]; val m = macros.find { it.name == n }
            val img = (box as LinearLayout).getChildAt(0) as ImageView; val tv = box.getChildAt(1) as TextView
            img.setImageResource(if (r == null) R.drawable.ic_bolt else if (r.paused) R.drawable.ic_play else R.drawable.ic_pause)
            img.setColorFilter(if (r == null) T.bg else Color.WHITE)
            (img.background as GradientDrawable).setColor(if (r == null) T.fg else if (r.paused) T.orange else T.green)
            tv.text = if (r == null) n else "${r.loop + 1}/${if (m?.loops == 0) "∞" else "${m?.loops}"}"
        }
    }

    // ================= Ikon bulat per macro =================
    private fun addIcon(m: Macro) {
        if (icons.containsKey(m.name)) return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; alpha = S.iconAlpha / 100f }
        val img = ImageView(this).apply {
            setImageResource(R.drawable.ic_bolt); setColorFilter(T.bg); val pd = dp(S.iconSize / 4); setPadding(pd, pd, pd, pd)
            background = oval(T.fg).apply { setStroke(dp(2), T.bg) }; layoutParams = LinearLayout.LayoutParams(dp(S.iconSize), dp(S.iconSize)) }
        box.addView(img); box.addView(label(m.name, 9f, true).apply { setShadowLayer(6f, 0f, 0f, T.bg) })
        val slot = icons.size
        val p = lp(-2, -2, sw - dp(S.iconSize) - dp(20), (dp(120) + slot * dp(S.iconSize + 26)) % maxOf(dp(200), sh - dp(160)))
        drag(box, box, p, onTap = { macros.find { it.name == m.name }?.let { toggleRun(it) } }, onLong = { stopRun(m.name); toast("Berhenti") })
        wm.addView(box, p); icons[m.name] = box; refreshRun()
    }
    private fun removeIcon(n: String) { icons.remove(n)?.let { wm.removeView(it) } }

    // ================= Target auto-click (turbo) =================
    private fun toggleTarget() { if (target != null) { stopClicker(); target?.let { wm.removeView(it) }; target = null } else addTarget() }
    private fun addTarget() {
        val s = dp(58)
        val v = ImageView(this).apply { setImageResource(R.drawable.ic_target); setColorFilter(T.bg); val pd = dp(13); setPadding(pd, pd, pd, pd); background = oval(T.fg).apply { setStroke(dp(2), T.bg) } }
        val p = lp(s, s, sw / 2 - s / 2, sh / 2 - s / 2); tp = p
        drag(v, v, p, onTap = { toggleClicker() })
        wm.addView(v, p); target = v; toast("Geser ke titik, tap untuk mulai. Stop: tombol stop")
    }
    private fun toggleClicker() {
        val v = target as? ImageView ?: return; val p = tp ?: return
        if (clicker?.isActive == true) { stopClicker(); return }
        if (!Shell.ready()) { toast("Shizuku belum terhubung"); return }
        val loc = IntArray(2); v.getLocationOnScreen(loc); val cx = loc[0] + v.width / 2; val cy = loc[1] + v.height / 2
        p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; wm.updateViewLayout(v, p)
        (v.background as GradientDrawable).setColor(T.green); v.setColorFilter(Color.WHITE)
        val (w, h) = portrait(); val (rx, ry) = Touch.toRaw(cx, cy, rot(), w, h)
        clicker = scope.launch {
            try {
                val t0 = System.currentTimeMillis()
                while (isActive && !(S.autoStop > 0 && System.currentTimeMillis() - t0 > S.autoStop * 60000L)) {
                    if (Touch.ok) { Shell.inj(0, rx, ry); delay(18); Shell.inj(2, 0, 0) } else Shell.run("input tap $cx $cy")
                    delay((1000L / S.cps - 18).coerceAtLeast(4))
                }
            } finally { Shell.inj(2, 0, 0); withContext(NonCancellable + Dispatchers.Main) { stopClicker() } }
        }
        refreshRun()
    }
    private fun stopClicker() {
        clicker?.cancel(); clicker = null
        val v = target as? ImageView; val p = tp
        if (v != null && p != null && v.isAttachedToWindow) {
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv(); wm.updateViewLayout(v, p)
            (v.background as GradientDrawable).setColor(T.fg); v.setColorFilter(T.bg)
        }
        refreshRun()
    }

    // ================= Rekam (layar tetap bisa disentuh) =================
    private fun startRecord() {
        if (!Shell.ready()) { Shell.bind(this); toast("Hubungkan Shizuku dulu"); return }
        if (!Touch.ok) Touch.init()
        if (!Touch.ok) { toast("Layar sentuh tidak terbaca oleh Shizuku"); return }
        var name = pendingName.trim().ifEmpty { "Macro ${macros.size + 1}" }
        while (macros.any { it.name == name }) name += "'"
        val land = isLand(); closePanel()
        handle?.visibility = View.GONE; dock?.visibility = View.GONE; target?.visibility = View.GONE
        val frame = View(this).apply { background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(3), T.hot) } }
        wm.addView(frame, lp(-1, -1, touch = false)); rec = frame
        val list = mutableListOf<Act>(); val cnt = label("0 aksi", 13f, true)
        val parser = Recorder(list) { rx, ry -> val (w, h) = portrait(); Touch.toDisp(rx, ry, rot(), w, h) }
        parser.ignore = { x, y -> barRect.contains(x, y) }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = rb(T.bg, 26, true); setPadding(dp(16), dp(6), dp(8), dp(6)) }
        bar.addView(View(this).apply { background = oval(T.hot); layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(8) } })
        bar.addView(cnt.apply { minWidth = dp(64) })
        bar.addView(icon(R.drawable.ic_undo) { synchronized(list) { if (list.isNotEmpty()) list.removeAt(list.size - 1) }; cnt.text = "${list.size} aksi" })
        bar.addView(icon(R.drawable.ic_close) { finishRecord(name, list, land, false) })
        bar.addView(icon(R.drawable.ic_check, Color.WHITE, 40, T.hot) { finishRecord(name, list, land, true) })
        bar.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val l = IntArray(2); v.getLocationOnScreen(l); barRect = Rect(l[0] - dp(24), l[1] - dp(24), l[0] + v.width + dp(24), l[1] + v.height + dp(24)) }
        wm.addView(bar, lp(-2, -2, 0, dp(36)).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }); recBar = bar
        try { Shell.svc?.startEvents() } catch (_: Exception) {}
        recJob = scope.launch {
            while (isActive) {
                delay(40)
                val s = try { Shell.svc?.drainEvents() } catch (e: Exception) { null } ?: continue
                if (s.isNotEmpty()) { s.lineSequence().forEach { parser.feed(it) }; val n = synchronized(list) { list.size }; withContext(Dispatchers.Main) { cnt.text = "$n aksi" } }
            }
        }
        toast("Merekam. Main seperti biasa, lalu tekan centang")
    }
    private fun finishRecord(name: String, list: MutableList<Act>, land: Boolean, save: Boolean) {
        recJob?.cancel(); try { Shell.svc?.stopEvents() } catch (_: Exception) {}
        rec?.let { wm.removeView(it) }; recBar?.let { wm.removeView(it) }; rec = null; recBar = null
        handle?.visibility = View.VISIBLE; dock?.visibility = View.VISIBLE; target?.visibility = View.VISIBLE
        if (!save) return
        val acts = synchronized(list) { list.toMutableList() }
        if (acts.isEmpty()) { toast("Belum ada aksi terekam"); return }
        val m = Macro(name, acts, 1, 500, true, land); macros.add(m); Store.save(this, macros); pendingName = ""
        addIcon(m); toast("Tersimpan: $name. Tap ikon bulat untuk jalan")
    }

    override fun onDestroy() {
        inst = null; scope.cancel(); stopClicker(); recJob?.cancel()
        listOf(rec, recBar, panel, dock, target, handle).forEach { v -> v?.let { try { wm.removeView(it) } catch (_: Exception) {} } }
        icons.keys.toList().forEach { removeIcon(it) }; super.onDestroy()
    }
}
