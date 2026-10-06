package com.auto.trigger
import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.*
import android.widget.*
import kotlinx.coroutines.*
import kotlin.math.abs

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val jobs = HashMap<String, Job>()
    private val icons = HashMap<String, View>()
    private var macros = mutableListOf<Macro>()
    private var handle: View? = null; private var handleLine: View? = null
    private var panel: View? = null; private var dock: View? = null
    private var rec: View? = null; private var recBar: View? = null
    private var page = 0; private var lastRun: String? = null
    private lateinit var hp: WindowManager.LayoutParams
    private val sw get() = resources.displayMetrics.widthPixels
    private val sh get() = resources.displayMetrics.heightPixels
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, x: Int = 0, y: Int = 0) = WindowManager.LayoutParams(w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate(); S.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("at", "Auto Trigger", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, "at").setContentTitle("Auto Trigger aktif").setSmallIcon(R.drawable.ic_bolt).build())
        Shell.bind(this); macros = Store.load(this)
        rebuildAll()
    }

    // ================= UI helpers =================
    private fun rb(c: Int, r: Int, stroke: Boolean = false) = GradientDrawable().apply {
        setColor(c); cornerRadius = dp(r).toFloat(); if (stroke) setStroke(dp(1), T.line) }
    private fun hap(v: View) { if (S.haptic == 1) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    private fun label(t: String, sz: Float = 13f, bold: Boolean = false, c: Int = T.fg) = TextView(this).apply {
        text = t; textSize = sz; setTextColor(c); if (bold) setTypeface(typeface, Typeface.BOLD) }
    private fun icon(res: Int, tint: Int = T.fg, sz: Int = 36, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(res); setColorFilter(tint); val p = dp(sz / 4); setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(dp(sz), dp(sz)).apply { setMargins(dp(2), 0, dp(2), 0) }
        background = rb(T.card, 10); setOnClickListener { hap(this); onClick() } }
    private fun chip(t: String, sel: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = 12f; gravity = Gravity.CENTER; setTextColor(if (sel) T.bg else T.fg)
        background = rb(if (sel) T.fg else T.card, 10); setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        setOnClickListener { hap(this); onClick() } }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun drag(touch: View, win: View, p: WindowManager.LayoutParams, snap: Boolean = false, onTap: () -> Unit = {}, onEnd: () -> Unit = {}) {
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
        touch.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = p.x; sy = p.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && (abs(e.rawX - dx) > dp(8) || abs(e.rawY - dy) > dp(8))) { moved = true; hap(touch) }
                    if (moved) {
                        p.x = (sx + e.rawX - dx).toInt().coerceIn(0, maxOf(0, sw - win.width))
                        p.y = (sy + e.rawY - dy).toInt().coerceIn(0, maxOf(0, sh - win.height))
                        wm.updateViewLayout(win, p)
                    }
                }
                MotionEvent.ACTION_UP -> if (moved) {
                    if (snap) { p.x = if (p.x + win.width / 2 < sw / 2) 0 else sw - win.width; wm.updateViewLayout(win, p) }
                    onEnd()
                } else { hap(touch); onTap() }
            }
            true
        }
    }

    private fun rebuildAll() {
        val open = panel != null; closePanel(); buildHandle(); buildDock()
        icons.keys.toList().forEach { removeIcon(it) }; macros.filter { it.pinned }.forEach { addIcon(it) }
        refreshRun(); if (open) openPanel()
    }

    // ================= Smart handle =================
    private fun buildHandle() {
        handle?.let { wm.removeView(it) }
        val (sx, sy) = Store.handlePos(this)
        val box = FrameLayout(this); val line = View(this); handleLine = line
        box.addView(line, FrameLayout.LayoutParams(dp(S.hW), dp(S.hLen), Gravity.CENTER_VERTICAL or Gravity.START))
        hp = lp(dp(S.hW) + dp(18), dp(S.hLen) + dp(12), 0, sy)
        hp.x = if (sx < sw / 2) 0 else sw - hp.width
        fun side() { (line.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER_VERTICAL or (if (hp.x < sw / 2) Gravity.START else Gravity.END); line.requestLayout() }
        side(); line.alpha = S.hAlpha / 100f
        drag(box, box, hp, true, { togglePanel() }, { Store.setHandlePos(this, hp.x, hp.y); side(); if (panel != null) openPanel() })
        wm.addView(box, hp); handle = box
    }

    // ================= Panel =================
    private fun togglePanel() { if (panel != null) closePanel() else openPanel() }
    private fun closePanel() { panel?.let { wm.removeView(it) }; panel = null }
    private fun stopAll() { jobs.values.forEach { it.cancel() }; jobs.clear(); refreshRun() }

    private fun openPanel() {
        closePanel()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)) }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(label(if (page == 0) "AUTO TRIGGER" else "PENGATURAN", 12f, true).apply { letterSpacing = 0.15f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        head.addView(icon(R.drawable.ic_rec, T.hot, 32) { closePanel(); startRecord() })
        head.addView(icon(R.drawable.ic_stop, T.fg, 32) { stopAll() })
        head.addView(icon(R.drawable.ic_grid, if (S.dock == 1) T.hot else T.fg, 32) { S.dock = 1 - S.dock; buildDock(); openPanel() })
        head.addView(icon(R.drawable.ic_settings, if (page == 1) T.hot else T.fg, 32) { page = 1 - page; openPanel() })
        head.addView(icon(R.drawable.ic_close, T.fg, 32) { closePanel() })
        col.addView(head)
        if (page == 0) macroList(col) else settingsPage(col)
        val pw = dp(320)
        col.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val ph = minOf(col.measuredHeight, sh * 7 / 10)
        val px = if (hp.x < sw / 2) hp.width + dp(4) else sw - pw - hp.width - dp(4)
        val py = minOf(hp.y, sh - ph - dp(40)).coerceAtLeast(dp(24))
        val sv = ScrollView(this).apply { addView(col); background = rb(T.bgA(), 20, true); alpha = 0f; scaleX = .94f; scaleY = .94f }
        wm.addView(sv, lp(pw, ph, px, py)); panel = sv
        sv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
    }

    private fun macroList(col: LinearLayout) {
        if (!Shell.ready()) col.addView(label("Shizuku belum terhubung", 11f, false, T.hot).apply { setPadding(0, dp(8), 0, 0) })
        if (macros.isEmpty()) col.addView(label("Belum ada macro. Tekan tombol rekam.", 12f, false, T.sub).apply { setPadding(0, dp(12), 0, dp(4)) })
        val loopsCycle = listOf(1, 5, 10, 50, 0); val gapCycle = listOf(0L, 100L, 200L, 500L, 1000L, 2000L)
        macros.toList().forEach { m ->
            val run = jobs[m.name]?.isActive == true
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rb(T.card, 14); setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) } }
            card.addView(label(m.name, 14f, true))
            card.addView(label("${m.acts.size} aksi  |  ${if (m.loops == 0) "∞" else "${m.loops}x"}  |  jeda ${m.gap}ms", 11f, false, T.sub))
            val r = LinearLayout(this).apply { setPadding(0, dp(6), 0, 0) }
            r.addView(icon(if (run) R.drawable.ic_stop else R.drawable.ic_play, if (run) T.hot else T.fg) { closePanel(); toggleRun(m) })
            r.addView(icon(R.drawable.ic_loop) { m.loops = loopsCycle[(loopsCycle.indexOf(m.loops) + 1) % loopsCycle.size]; Store.save(this, macros); openPanel() })
            r.addView(icon(R.drawable.ic_timer) { m.gap = gapCycle[(gapCycle.indexOf(m.gap) + 1) % gapCycle.size]; Store.save(this, macros); openPanel() })
            r.addView(icon(R.drawable.ic_pin, if (m.pinned) T.hot else T.fg) { m.pinned = !m.pinned; if (m.pinned) addIcon(m) else removeIcon(m.name); Store.save(this, macros); openPanel() })
            r.addView(icon(R.drawable.ic_copy) { var n = m.name + "2"; while (macros.any { it.name == n }) n += "'"; macros.add(Macro(n, m.acts.toMutableList(), m.loops, m.gap)); Store.save(this, macros); openPanel() })
            r.addView(icon(R.drawable.ic_delete) { stopRun(m.name); removeIcon(m.name); macros.remove(m); Store.save(this, macros); openPanel() })
            card.addView(r); col.addView(card)
        }
    }

    private fun section(t: String) = label(t, 11f, true, T.sub).apply { setPadding(0, dp(12), 0, dp(4)); letterSpacing = 0.12f }
    private fun stepper(name: String, v: Int, mn: Int, mx: Int, st: Int, unit: String, set: (Int) -> Unit): View {
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, dp(3)) }
        r.addView(label(name).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        r.addView(icon(R.drawable.ic_minus, T.fg, 30) { set((v - st).coerceAtLeast(mn)); rebuildAll() })
        r.addView(label("$v$unit", 12f, true).apply { gravity = Gravity.CENTER; minWidth = dp(54) })
        r.addView(icon(R.drawable.ic_add, T.fg, 30) { set((v + st).coerceAtMost(mx)); rebuildAll() })
        return r
    }
    private fun toggle(name: String, on: Boolean, set: (Int) -> Unit): View {
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, dp(3)) }
        r.addView(label(name).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        r.addView(chip(if (on) "ON" else "OFF", on) { set(if (on) 0 else 1); rebuildAll() }); return r
    }
    private fun settingsPage(col: LinearLayout) {
        col.addView(section("TEMA"))
        val tr = LinearLayout(this)
        listOf("Hitam", "Putih", "AMOLED").forEachIndexed { i, n -> tr.addView(chip(n, S.theme == i) { S.theme = i; rebuildAll() }) }
        col.addView(tr)
        col.addView(section("TAMPILAN"))
        col.addView(stepper("Opacity panel", S.alpha, 40, 100, 5, "%") { S.alpha = it })
        col.addView(stepper("Panjang garis", S.hLen, 40, 240, 10, "dp") { S.hLen = it })
        col.addView(stepper("Tebal garis", S.hW, 3, 14, 1, "dp") { S.hW = it })
        col.addView(stepper("Transparansi garis", S.hAlpha, 20, 100, 10, "%") { S.hAlpha = it })
        col.addView(stepper("Ukuran ikon", S.iconSize, 36, 72, 6, "dp") { S.iconSize = it })
        col.addView(toggle("Dock bar", S.dock == 1) { S.dock = it })
        col.addView(toggle("Getar haptic", S.haptic == 1) { S.haptic = it })
        col.addView(section("EKSEKUSI"))
        col.addView(stepper("Kecepatan", S.speed, 50, 300, 25, "%") { S.speed = it })
        col.addView(stepper("Hitung mundur", S.countdown, 0, 5, 1, "s") { S.countdown = it })
        col.addView(section("DATA"))
        val dr = LinearLayout(this)
        dr.addView(chip("Ekspor", false) { exportM() }); dr.addView(chip("Impor", false) { importM() })
        dr.addView(chip("Hapus semua", false) { stopAll(); macros.clear(); Store.save(this, macros); rebuildAll() })
        col.addView(dr)
    }
    private fun exportM() {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("macros", Store.raw(this))); toast("Macro disalin ke clipboard")
    }
    private fun importM() {
        val old = Store.raw(this)
        try {
            val t = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text.toString()
            Store.setRaw(this, t); val imp = Store.load(this); Store.setRaw(this, old)
            imp.forEach { var n = it.name; while (macros.any { m -> m.name == n }) n += "'"; it.name = n; it.pinned = false; macros.add(it) }
            Store.save(this, macros); toast("${imp.size} macro diimpor"); openPanel()
        } catch (e: Exception) { Store.setRaw(this, old); toast("Clipboard bukan data macro") }
    }

    // ================= Dock bar =================
    private fun buildDock() {
        dock?.let { wm.removeView(it) }; dock = null
        if (S.dock != 1) return
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = rb(T.bgA(), 28, true); setPadding(dp(8), dp(6), dp(10), dp(6)) }
        val grip = ImageView(this).apply { setImageResource(R.drawable.ic_drag); setColorFilter(T.sub); layoutParams = LinearLayout.LayoutParams(dp(26), dp(36)) }
        bar.addView(grip)
        bar.addView(icon(R.drawable.ic_rec, T.hot) { startRecord() })
        bar.addView(icon(R.drawable.ic_play) { (macros.find { it.name == lastRun } ?: macros.firstOrNull())?.let { toggleRun(it) } ?: toast("Belum ada macro") })
        bar.addView(icon(R.drawable.ic_stop) { stopAll() })
        bar.addView(icon(R.drawable.ic_bolt) { togglePanel() })
        val p = lp(-2, -2, if (S.dx < 0) dp(40) else S.dx, if (S.dy < 0) sh - dp(170) else S.dy)
        drag(grip, bar, p, false, {}, { S.dx = p.x; S.dy = p.y })
        wm.addView(bar, p); dock = bar
    }

    // ================= Playback =================
    private fun toggleRun(m: Macro) { if (jobs[m.name]?.isActive == true) stopRun(m.name) else run(m) }
    private fun stopRun(n: String) { jobs.remove(n)?.cancel(); refreshRun() }
    private fun run(m: Macro) {
        if (!Shell.ready()) { Shell.bind(this); toast("Shizuku belum terhubung"); return }
        lastRun = m.name
        jobs[m.name] = scope.launch {
            for (i in S.countdown downTo 1) { withContext(Dispatchers.Main) { toast("$i") }; delay(1000) }
            var n = 0
            while (isActive && (m.loops == 0 || n < m.loops)) {
                for (a in m.acts) {
                    if (!isActive) break
                    delay(a.delay * 100 / S.speed)
                    val moved = abs(a.x1 - a.x2) > 5 || abs(a.y1 - a.y2) > 5
                    Shell.run(if (!moved && a.dur < 200) "input tap ${a.x1} ${a.y1}" else "input swipe ${a.x1} ${a.y1} ${a.x2} ${a.y2} ${a.dur}")
                    if (moved || a.dur >= 200) delay(a.dur)
                }
                n++; delay(m.gap * 100 / S.speed)
            }
            withContext(Dispatchers.Main) { jobs.remove(m.name); refreshRun() }
        }
        refreshRun()
    }
    private fun refreshRun() {
        val on = jobs.values.any { it.isActive }
        handleLine?.background = rb(if (on) T.hot else T.fg, 3)
        icons.forEach { (n, v) ->
            val r = jobs[n]?.isActive == true
            ((v as LinearLayout).getChildAt(0) as ImageView).apply {
                setImageResource(if (r) R.drawable.ic_stop else R.drawable.ic_bolt); (background as GradientDrawable).setColor(if (r) T.hot else T.fg) }
        }
    }

    // ================= Floating icons =================
    private fun addIcon(m: Macro) {
        if (icons.containsKey(m.name)) return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val img = ImageView(this).apply {
            setImageResource(R.drawable.ic_bolt); setColorFilter(T.bg); val pd = dp(S.iconSize / 4); setPadding(pd, pd, pd, pd)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(T.fg); setStroke(dp(2), T.bg) }
            layoutParams = LinearLayout.LayoutParams(dp(S.iconSize), dp(S.iconSize)) }
        box.addView(img); box.addView(label(m.name, 9f, true).apply { setShadowLayer(6f, 0f, 0f, T.bg) })
        val p = lp(-2, -2, sw - dp(S.iconSize) - dp(16), dp(160) + icons.size * dp(S.iconSize + 24))
        drag(box, box, p, false, { macros.find { it.name == m.name }?.let { toggleRun(it) } })
        wm.addView(box, p); icons[m.name] = box
    }
    private fun removeIcon(n: String) { icons.remove(n)?.let { wm.removeView(it) } }

    // ================= Rekam =================
    private fun startRecord() {
        closePanel(); handle?.visibility = View.GONE; dock?.visibility = View.GONE
        val list = mutableListOf<Act>(); val cnt = label("0", 14f, true)
        var lastUp = 0L; var t0 = 0L; var x0 = 0f; var y0 = 0f
        val ov = FrameLayout(this).apply { setBackgroundColor(0x22FF3B30) }
        ov.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { t0 = e.eventTime; x0 = e.rawX; y0 = e.rawY }
                MotionEvent.ACTION_UP -> {
                    list.add(Act(if (lastUp == 0L) 300 else t0 - lastUp, x0.toInt(), y0.toInt(), e.rawX.toInt(), e.rawY.toInt(), (e.eventTime - t0).coerceAtLeast(40)))
                    lastUp = e.eventTime; cnt.text = "${list.size}"
                }
            }
            true
        }
        wm.addView(ov, lp(-1, -1)); rec = ov
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = rb(T.bg, 24, true); setPadding(dp(14), dp(6), dp(8), dp(6)) }
        bar.addView(View(this).apply { background = rb(T.hot, 6); layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(8) } })
        bar.addView(cnt.apply { minWidth = dp(28) })
        bar.addView(icon(R.drawable.ic_undo) { if (list.isNotEmpty()) { list.removeAt(list.size - 1); cnt.text = "${list.size}" } })
        bar.addView(icon(R.drawable.ic_close) { stopRecord() })
        bar.addView(icon(R.drawable.ic_check, T.hot) {
            stopRecord()
            if (list.isNotEmpty()) { macros.add(Macro("M${macros.size + 1}", list)); Store.save(this, macros); toast("Macro tersimpan") }
        })
        wm.addView(bar, lp(-2, -2, 0, dp(40)).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }); recBar = bar
    }
    private fun stopRecord() {
        rec?.let { wm.removeView(it) }; recBar?.let { wm.removeView(it) }; rec = null; recBar = null
        handle?.visibility = View.VISIBLE; dock?.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        scope.cancel(); stopRecord(); closePanel(); dock?.let { wm.removeView(it) }
        icons.keys.toList().forEach { removeIcon(it) }; handle?.let { wm.removeView(it) }; super.onDestroy()
    }
}
