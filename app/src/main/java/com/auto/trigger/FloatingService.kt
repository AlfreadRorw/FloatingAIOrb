package com.auto.trigger
import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

class FloatingService : Service() {
    companion object { @Volatile var inst: FloatingService? = null }
    class Runner(val m: Macro) { @Volatile var paused = false; @Volatile var loop = 0; @Volatile var idx = 0; var job: Job? = null }
    class Tgt(val view: TargetView, val lp: WindowManager.LayoutParams, val slot: Int, var cps: Int) { var job: Job? = null }
    class IconBox(val box: LinearLayout, val ic: RoundIcon, val tv: TextView, val lp: WindowManager.LayoutParams)

    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val runners = HashMap<String, Runner>()
    private val icons = HashMap<String, IconBox>()
    private val targets = mutableListOf<Tgt>()
    private var macros = mutableListOf<Macro>()
    private var handle: View? = null; private var handleLine: View? = null
    private var panel: View? = null; private var dock: View? = null; private var dockLp: WindowManager.LayoutParams? = null
    private var dockRow2: View? = null; private var dockSpd: TextView? = null; private var dockPlay: ImageView? = null; private var dockTurbo: ImageView? = null
    private var rec: View? = null; private var recBar: View? = null; private var recJob: Job? = null; private var recTick: Runnable? = null
    private var barRect = Rect()
    private var pendingName = ""; private var lastRun: String? = null
    private var page = 0; private var pickName = ""; private var expanded: String? = null
    private val rowBars = HashMap<String, Bar>()
    private var lastToast: Toast? = null; private var lastNotif = ""
    private var dimmed = false; private var ticking = false
    private val fadeR = Runnable { fade(true) }
    private val tickR = object : Runnable { override fun run() { tick(); if (anyActive()) ui.postDelayed(this, 150) else ticking = false } }
    private lateinit var hp: WindowManager.LayoutParams

    // ---------- layar ----------
    private val disp get() = (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
    private fun real() = Point().also { disp.getRealSize(it) }
    private val sw get() = real().x
    private val sh get() = real().y
    private fun rot() = disp.rotation
    private fun portrait() = real().let { minOf(it.x, it.y) to maxOf(it.x, it.y) }
    private fun isLand() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun toast(t: String) { lastToast?.cancel(); lastToast = Toast.makeText(this, t, Toast.LENGTH_SHORT).also { it.show() } }
    private fun anyActive() = runners.values.any { it.job?.isActive == true } || targets.any { it.job?.isActive == true }

    private fun lp(w: Int, h: Int, x: Int = 0, y: Int = 0, focus: Boolean = false, touch: Boolean = true) = WindowManager.LayoutParams(w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or (if (focus) WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            (if (touch) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE), PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y; if (focus) softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate(); S.init(this); inst = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("at2", "Auto Trigger", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, buildNotif("Siap"))
        Shell.bind(this); macros = Store.load(this); rebuildAll()
    }
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        when (i?.action) { "at.stop" -> stopAll(); "at.panel" -> openPanel(); "at.turbo" -> toggleTurbo() }
        return START_STICKY
    }
    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); ui.postDelayed({ rebuildAll() }, 350) }
    fun reload() { macros = Store.load(this); rebuildAll() }
    fun softReload() { macros = Store.load(this); refreshRun() }
    fun showPanel() { openPanel() }
    fun runByName(n: String) { macros.find { it.name == n }?.let { toggleRun(it) } }
    private fun startByName(n: String) { val m = macros.find { it.name == n } ?: return; if (runners[n]?.job?.isActive != true) run(m) }
    fun addTargetPublic() = addTarget()
    fun startAllTargets() { targets.toList().forEach { if (it.job?.isActive != true) toggleClicker(it) } }
    fun clearTargets() { targets.toList().forEach { removeTarget(it) } }
    fun setAllCps(v: Int) { targets.forEach { it.cps = v } }
    fun toggleTurbo() { S.turboOn = !S.turboOn; toast(if (S.turboOn) "Turbo ${spdTxt(S.turbo)} aktif" else "Turbo mati"); speedChanged() }
    fun selfTest() {
        if (!Shell.ready()) { toast("Shizuku belum terhubung"); return }
        val (w, h) = real().let { it.x to it.y }
        scope.launch { delay(600); perform(Act(0, w / 2, h * 7 / 10, w / 2, h * 3 / 10, 450), 1.0) }
        toast("Tes: geser layar ke atas")
    }

    fun rebuildAll() {
        val open = panel != null; closePanel(); buildHandle(); buildDock()
        icons.keys.toList().forEach { removeIcon(it) }; macros.filter { it.pinned }.forEach { addIcon(it) }
        targets.forEach { t ->
            t.lp.width = dp(S.tSize); t.lp.height = dp(S.tSize); try { wm.updateViewLayout(t.view, t.lp) } catch (_: Exception) {}
            t.view.alpha = S.tAlpha / 100f }
        refreshRun(); if (open) openPanel(false)
    }

    // ---------- kecepatan ----------
    private fun fac(m: Macro): Double = (S.speed / 100.0) * (m.speed / 100.0) * (if (S.turboOn) S.turbo / 100.0 else 1.0)
    private fun effTxt() = spdTxt((S.speed * (if (S.turboOn) S.turbo else 100) / 100.0).roundToInt())
    fun speedChanged() {
        dockSpd?.text = effTxt(); dockTurbo?.setColorFilter(if (S.turboOn) T.on(T.orange) else T.fg)
        (dockTurbo?.background as? android.graphics.drawable.GradientDrawable)?.setColor(if (S.turboOn) T.orange else T.card)
        buildDockRow2Refresh(); refreshRun(); if (panel != null && page == 0) openPanel(false)
    }
    private fun setSpeed(v: Int) { S.speed = v.coerceIn(10, 500); speedChanged() }
    private fun stepSpeed(dir: Int) {
        val cur = S.speed
        val n = if (dir > 0) SPEEDS.firstOrNull { it > cur } ?: 500 else SPEEDS.lastOrNull { it < cur } ?: 10
        setSpeed(n)
    }
    /** menunggu dengan kecepatan yang bisa berubah saat sedang menunggu */
    private suspend fun vdelay(ms: Long, r: Runner) {
        var left = ms.toDouble()
        while (left > 0) {
            while (r.paused) delay(40)
            val f = fac(r.m); val need = left / f
            if (need <= 12) { if (need >= 1) delay(need.toLong()); return }
            delay(12); left -= 12 * f
        }
    }
    private fun jt(d: Long): Long = if (S.jitterT == 0) d else (d * (1 + Random.nextInt(-S.jitterT, S.jitterT + 1) / 100.0)).toLong().coerceAtLeast(0)

    // ================= drag helper =================
    private fun drag(touch: View, win: View, p: WindowManager.LayoutParams, snap: Boolean = false, onTap: () -> Unit = {}, onLong: (() -> Unit)? = null, onEnd: () -> Unit = {}) {
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false; var long = false
        val lr = Runnable { if (!moved) { long = true; hap(touch); onLong?.invoke() } }
        touch.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { wake(); dx = e.rawX; dy = e.rawY; sx = p.x; sy = p.y; moved = false; long = false; if (onLong != null) ui.postDelayed(lr, 550) }
                MotionEvent.ACTION_MOVE -> {
                    if (S.lockPos == 0 && !moved && (abs(e.rawX - dx) > dp(8) || abs(e.rawY - dy) > dp(8))) { moved = true; ui.removeCallbacks(lr); hap(touch) }
                    if (moved) {
                        p.x = (sx + e.rawX - dx).toInt().coerceIn(0, maxOf(0, sw - win.width)); p.y = (sy + e.rawY - dy).toInt().coerceIn(0, maxOf(0, sh - win.height))
                        wm.updateViewLayout(win, p)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(lr)
                    if (moved) { if (snap) { p.x = if (p.x + win.width / 2 < sw / 2) 0 else sw - win.width; wm.updateViewLayout(win, p) }; onEnd() }
                    else if (!long && e.action == MotionEvent.ACTION_UP) { hap(touch); onTap() }
                }
            }
            true
        }
    }

    // ================= auto hide =================
    private fun fade(dim: Boolean) {
        if (dim && (rec != null || panel != null)) return
        dimmed = dim
        val f = if (dim) 0.25f else 1f
        handle?.animate()?.alpha(f)?.setDuration(250)?.start(); dock?.animate()?.alpha(f)?.setDuration(250)?.start()
        icons.values.forEach { it.box.animate().alpha(S.iconAlpha / 100f * (if (dim) 0.35f else 1f)).setDuration(250).start() }
        targets.forEach { it.view.animate().alpha(S.tAlpha / 100f * (if (dim) 0.35f else 1f)).setDuration(250).start() }
    }
    private fun wake() {
        ui.removeCallbacks(fadeR); if (dimmed) fade(false)
        if (S.autoHide > 0) ui.postDelayed(fadeR, S.autoHide * 1000L)
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
            side(); if (panel != null) openPanel(false) })
        wm.addView(box, hp); handle = box
    }

    // ================= Panel =================
    private fun togglePanel() { if (panel != null) closePanel() else { page = 0; openPanel() } }
    private fun closePanel() { panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }; panel = null; rowBars.clear() }
    private fun openApp() { closePanel(); startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    private fun openPanel(anim: Boolean = true) {
        val oldY = (panel as? ScrollView)?.scrollY ?: 0
        closePanel(); wake()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        if (page == 1 && macros.any { it.name == pickName }) buildPicker(col) else { page = 0; buildList(col) }
        val pw = dp(S.panelW).coerceAtMost(sw - dp(24))
        col.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val ph = minOf(col.measuredHeight, sh * 8 / 10)
        val px = if (hp.x < sw / 2) hp.width + dp(4) else sw - pw - hp.width - dp(4)
        val py = minOf(hp.y, sh - ph - dp(30)).coerceAtLeast(dp(20))
        val sv = ScrollView(this).apply { addView(col); background = rb(T.bgA(), 22, true); isVerticalScrollBarEnabled = false; if (anim) { alpha = 0f; scaleX = .94f; scaleY = .94f } }
        wm.addView(sv, lp(pw, ph, px, py, focus = true)); panel = sv
        if (anim) sv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
        sv.post { sv.scrollTo(0, oldY) }
    }

    private fun buildPicker(col: LinearLayout) {
        val m = macros.first { it.name == pickName }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(icon(R.drawable.ic_back, T.fg, 32) { page = 0; openPanel(false) })
        head.addView(label(m.name, 14f, true).apply { setPadding(dp(8), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        head.addView(icon(R.drawable.ic_close, T.fg, 32) { closePanel() })
        col.addView(head)
        col.addView(pickerView(m) { Store.save(this, macros); refreshRun() }.apply { setPadding(0, dp(8), 0, 0) })
    }

    private fun buildList(col: LinearLayout) {
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(View(this).apply { background = oval(T.accent); layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(8) } })
        head.addView(label("AUTO TRIGGER", 12f, true).apply { letterSpacing = 0.15f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        head.addView(icon(R.drawable.ic_lock, if (S.lockPos == 1) T.on(T.accent) else T.fg, 32, if (S.lockPos == 1) T.accent else T.card) { S.lockPos = 1 - S.lockPos; toast(if (S.lockPos == 1) "Posisi dikunci" else "Posisi dibuka"); openPanel(false) })
        head.addView(icon(R.drawable.ic_settings, T.fg, 32) { openApp() })
        head.addView(icon(R.drawable.ic_close, T.fg, 32) { closePanel() })
        col.addView(head)

        // kecepatan
        val sc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rb(T.card, 14); setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) } }
        val sr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        sr.addView(label("KECEPATAN", 10f, true, T.sub).apply { letterSpacing = 0.12f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        sr.addView(icon(R.drawable.ic_minus, T.fg, 30, T.bg) { stepSpeed(-1) })
        sr.addView(label(effTxt(), 14f, true, if (S.turboOn) T.orange else T.accent).apply { gravity = Gravity.CENTER; minWidth = dp(54) })
        sr.addView(icon(R.drawable.ic_add, T.fg, 30, T.bg) { stepSpeed(1) })
        sr.addView(icon(R.drawable.ic_bolt, if (S.turboOn) T.on(T.orange) else T.fg, 30, if (S.turboOn) T.orange else T.bg) { toggleTurbo() })
        sc.addView(sr)
        val pr = LinearLayout(this); SPEEDS.forEach { v -> pr.addView(miniChip(spdTxt(v), S.speed == v) { setSpeed(v) }) }
        sc.addView(hscroll(pr)); col.addView(sc)

        // rekam
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

        // aksi cepat
        val qr = LinearLayout(this)
        qr.addView(chip("+ Target", false) { addTarget(); openPanel(false) })
        qr.addView(chip("Stop semua", false, T.hot) { stopAll(); openPanel(false) })
        qr.addView(chip("Tes", false) { selfTest() })
        col.addView(hscroll(qr))

        // target
        targets.forEachIndexed { i, t ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = rb(T.card, 14); setPadding(dp(8), dp(4), dp(8), dp(4))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) } }
            row.addView(label("Target ${i + 1}", 12f, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            row.addView(icon(R.drawable.ic_minus, T.fg, 28, T.bg) { t.cps = (t.cps - 1).coerceAtLeast(1); t.view.cps = t.cps; openPanel(false) })
            row.addView(label("${t.cps}/dtk", 11f, true).apply { gravity = Gravity.CENTER; minWidth = dp(46) })
            row.addView(icon(R.drawable.ic_add, T.fg, 28, T.bg) { t.cps = (t.cps + 1).coerceAtMost(60); t.view.cps = t.cps; openPanel(false) })
            val on = t.job?.isActive == true
            row.addView(icon(if (on) R.drawable.ic_stop else R.drawable.ic_play, Color.WHITE, 30, if (on) T.hot else T.green) { toggleClicker(t); openPanel(false) })
            row.addView(icon(R.drawable.ic_delete, T.fg, 28, T.bg) { removeTarget(t); openPanel(false) })
            col.addView(row)
        }

        if (macros.isEmpty()) col.addView(label("Isi nama, tekan rekam, lakukan aksi di layar, lalu simpan.", 11f, false, T.sub).apply { setPadding(0, dp(8), 0, 0) })
        macros.toList().forEach { m ->
            val r = runners[m.name]
            val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rb(T.card, 14); setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) } }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val ic = RoundIcon(this, 42).also { it.set(m, stateOf(r), progOf(m, r), badgeOf(m, r)) }
            ic.setOnClickListener { hap(ic); toggleRun(m); openPanel(false) }
            ic.setOnLongClickListener { stopRun(m.name); toast("Berhenti"); openPanel(false); true }
            row.addView(ic)
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(4), 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setOnClickListener { hap(this); expanded = if (expanded == m.name) null else m.name; openPanel(false) } }
            info.addView(label(m.name, 14f, true).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END })
            info.addView(label("${m.acts.size} aksi | ${if (m.loops == 0) "∞" else "${m.loops}x"} | ${spdTxt(m.speed)}", 11f, false, T.sub))
            row.addView(info)
            row.addView(icon(R.drawable.ic_pin, if (m.pinned) T.hot else T.fg, 32, T.bg) { m.pinned = !m.pinned; if (m.pinned) addIcon(m) else removeIcon(m.name); Store.save(this, macros); openPanel(false) })
            row.addView(icon(R.drawable.ic_delete, T.fg, 32, T.bg) { stopRun(m.name); removeIcon(m.name); macros.remove(m); Store.save(this, macros); openPanel(false) })
            wrap.addView(row)
            val bar = Bar(this).apply { color = if (r?.paused == true) T.orange else T.green; frac = progOf(m, r); layoutParams = LinearLayout.LayoutParams(-1, dp(3)).apply { topMargin = dp(6) } }
            wrap.addView(bar); rowBars[m.name] = bar
            if (expanded == m.name) {
                wrap.addView(label("KECEPATAN MACRO", 10f, true, T.sub).apply { letterSpacing = 0.12f; setPadding(0, dp(8), 0, dp(2)) })
                val mp = LinearLayout(this); intArrayOf(25, 50, 75, 100, 150, 200, 300).forEach { v -> mp.addView(miniChip(spdTxt(v), m.speed == v) { m.speed = v; Store.save(this, macros); openPanel(false) }) }
                wrap.addView(hscroll(mp))
                wrap.addView(stepper("Ulangi (0 = ∞)", m.loops, 0, 999, 1, "x", { m.loops = it }) { Store.save(this, macros); openPanel(false) })
                val br = LinearLayout(this)
                br.addView(chip("Ikon & warna", false) { pickName = m.name; page = 1; openPanel(false) })
                wrap.addView(br)
            }
            col.addView(wrap)
        }
    }
    private fun stateOf(r: Runner?) = if (r == null || r.job?.isActive != true) 0 else if (r.paused) 2 else 1
    private fun progOf(m: Macro, r: Runner?) = if (r == null) 0f else r.idx.toFloat() / maxOf(1, m.acts.size)
    private fun badgeOf(m: Macro, r: Runner?): String { if (r == null) return ""; val f = fac(m); return if (abs(f - 1.0) > 0.01) spdTxt((f * 100).roundToInt()) else "" }

    // ================= Dock bar =================
    private fun buildDockRow2Refresh() {
        val row = dockRow2 as? LinearLayout ?: return
        row.removeAllViews()
        row.addView(icon(R.drawable.ic_minus, T.fg, 30, T.bg) { stepSpeed(-1) })
        row.addView(label(effTxt(), 12f, true, T.accent).apply { gravity = Gravity.CENTER; minWidth = dp(46) })
        row.addView(icon(R.drawable.ic_add, T.fg, 30, T.bg) { stepSpeed(1) })
        intArrayOf(25, 50, 100, 200, 300).forEach { v -> row.addView(miniChip(spdTxt(v), S.speed == v) { setSpeed(v) }) }
    }
    private fun buildDock() {
        dock?.let { wm.removeView(it) }; dock = null
        if (S.dock != 1) return
        val bar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rb(T.bgA(), 28, true); setPadding(dp(8), dp(6), dp(10), dp(6)) }
        val r1 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val grip = ImageView(this).apply { setImageResource(R.drawable.ic_drag); setColorFilter(T.sub); layoutParams = LinearLayout.LayoutParams(dp(26), dp(36)) }
        r1.addView(grip)
        r1.addView(icon(R.drawable.ic_rec, T.hot, 34) { page = 0; openPanel() })
        dockPlay = icon(R.drawable.ic_play, T.fg, 34) { (macros.find { it.name == lastRun } ?: macros.firstOrNull())?.let { toggleRun(it) } ?: toast("Belum ada macro") }
        r1.addView(dockPlay)
        r1.addView(icon(R.drawable.ic_stop, T.fg, 34) { stopAll() })
        r1.addView(icon(R.drawable.ic_target, T.fg, 34) { addTarget() }.also { it.setOnLongClickListener { clearTargets(); toast("Target dihapus"); true } })
        r1.addView(icon(R.drawable.ic_speed, T.fg, 34) { dockRow2?.let { it.visibility = if (it.visibility == View.GONE) View.VISIBLE else View.GONE; dockLp?.let { p -> wm.updateViewLayout(bar, p) } } })
        dockSpd = label(effTxt(), 11f, true, T.accent).apply { gravity = Gravity.CENTER; minWidth = dp(34) }
        r1.addView(dockSpd)
        dockTurbo = icon(R.drawable.ic_bolt, if (S.turboOn) T.on(T.orange) else T.fg, 34, if (S.turboOn) T.orange else T.card) { toggleTurbo() }
        r1.addView(dockTurbo)
        r1.addView(icon(R.drawable.ic_settings, T.fg, 34) { openApp() })
        bar.addView(r1)
        val r2 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE; setPadding(dp(26), dp(6), 0, 0) }
        dockRow2 = r2; bar.addView(r2); buildDockRow2Refresh()
        val p = lp(-2, -2, if (S.dx < 0) dp(40) else S.dx, if (S.dy < 0) sh - dp(170) else S.dy); dockLp = p
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
    fun stopAll() {
        runners.values.forEach { it.job?.cancel() }; runners.clear(); targets.toList().forEach { stopClicker(it) }
        scope.launch { delay(150); Shell.release() }; refreshRun()
    }

    private fun run(m: Macro) {
        if (!Shell.ready()) { Shell.bind(this); toast("Shizuku belum terhubung"); return }
        if (m.land != isLand()) { toast("Macro direkam ${if (m.land) "landscape" else "portrait"}. Putar layar dulu"); return }
        if (m.acts.isEmpty()) return
        val r = Runner(m); runners[m.name] = r; lastRun = m.name
        r.job = scope.launch {
            try {
                for (i in S.countdown downTo 1) { withContext(Dispatchers.Main) { toast("$i") }; delay(1000) }
                val t0 = System.currentTimeMillis()
                fun over() = S.autoStop > 0 && System.currentTimeMillis() - t0 > S.autoStop * 60000L
                while (isActive && !over() && (m.loops == 0 || r.loop < m.loops)) {
                    while (r.idx < m.acts.size && isActive && !over()) {
                        val a = m.acts[r.idx]
                        vdelay(jt(a.delay), r)
                        while (r.paused) delay(40)
                        perform(a, fac(m)); r.idx++
                    }
                    if (r.idx >= m.acts.size) {
                        r.idx = 0; r.loop++; withContext(Dispatchers.Main) { refreshRun() }
                        if (m.loops == 0 || r.loop < m.loops) vdelay(jt(m.gap), r)
                    }
                }
                if (isActive && !over() && m.chain.isNotEmpty() && m.chain != m.name) withContext(Dispatchers.Main) { startByName(m.chain) }
            } finally { withContext(NonCancellable + Dispatchers.Main) { if (runners[m.name] === r) runners.remove(m.name); refreshRun() } }
        }
        refreshRun()
    }

    private suspend fun perform(a: Act, f: Double) {
        val j = S.jitter
        fun r() = if (j == 0) 0 else Random.nextInt(-j, j + 1)
        val x1 = a.x1 + r(); val y1 = a.y1 + r(); val x2 = a.x2 + r(); val y2 = a.y2 + r()
        val moved = abs(a.x1 - a.x2) > 8 || abs(a.y1 - a.y2) > 8
        val dur = if (moved && S.scaleDur == 1) (a.dur / f).toLong().coerceIn(30, 4000) else a.dur
        if (!Touch.ok) { Shell.run(if (!moved && a.dur < 200) "input tap $x1 $y1" else "input swipe $x1 $y1 $x2 $y2 $dur"); delay(dur); return }
        val rt = rot(); val (w, h) = portrait()
        val (ax, ay) = Touch.toRaw(x1, y1, rt, w, h); val (bx, by) = Touch.toRaw(x2, y2, rt, w, h)
        try {
            Shell.injM(0, 0, ax, ay)
            if (!moved) delay(a.dur.coerceAtLeast(30)) else {
                val steps = (dur / 16).toInt().coerceAtLeast(2)
                for (i in 1..steps) { delay(dur / steps); Shell.injM(0, 1, ax + (bx - ax) * i / steps, ay + (by - ay) * i / steps) }
            }
        } finally { Shell.injM(0, 2, 0, 0) }
    }

    private fun refreshRun() {
        val any = anyActive()
        handleLine?.background = rb(if (S.turboOn) T.orange else if (any) T.green else T.accent, 3); handle?.keepScreenOn = any
        icons.forEach { (n, ib) ->
            val m = macros.find { it.name == n } ?: return@forEach
            val r = runners[n]
            ib.ic.set(m, stateOf(r), progOf(m, r), badgeOf(m, r))
            ib.tv.text = if (r == null) n else "${r.loop + 1}/${if (m.loops == 0) "∞" else "${m.loops}"}"
        }
        val lm = macros.find { it.name == lastRun } ?: macros.firstOrNull()
        dockPlay?.setImageResource(if (lm != null && stateOf(runners[lm.name]) == 1) R.drawable.ic_pause else R.drawable.ic_play)
        updateNotif(); if (any && !ticking) { ticking = true; ui.post(tickR) }
        wake()
    }
    private fun tick() {
        icons.forEach { (n, ib) -> val m = macros.find { it.name == n } ?: return@forEach; val r = runners[n]; ib.ic.set(m, stateOf(r), progOf(m, r), badgeOf(m, r)) }
        rowBars.forEach { (n, b) -> val m = macros.find { it.name == n } ?: return@forEach; b.frac = progOf(m, runners[n]); b.invalidate() }
    }

    private fun buildNotif(text: String): Notification {
        fun pi(a: String, c: Int) = PendingIntent.getService(this, c, Intent(this, FloatingService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun act(res: Int, t: String, a: String, c: Int) = Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, res), t, pi(a, c)).build()
        return Notification.Builder(this, "at2").setContentTitle("Auto Trigger aktif").setContentText(text).setSmallIcon(R.drawable.ic_bolt).setOngoing(true)
            .addAction(act(R.drawable.ic_list, "Panel", "at.panel", 1)).addAction(act(R.drawable.ic_stop, "Stop semua", "at.stop", 2)).addAction(act(R.drawable.ic_bolt, "Turbo", "at.turbo", 3)).build()
    }
    private fun updateNotif() {
        val n = runners.values.count { it.job?.isActive == true }
        val t = (if (n > 0) "$n macro jalan" else "Siap") + " | kecepatan ${effTxt()}" + (if (S.turboOn) " (turbo)" else "")
        if (t != lastNotif) { lastNotif = t; getSystemService(NotificationManager::class.java).notify(1, buildNotif(t)) }
    }

    // ================= Ikon macro =================
    private fun addIcon(m: Macro) {
        if (icons.containsKey(m.name)) return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; alpha = S.iconAlpha / 100f }
        val ic = RoundIcon(this, S.iconSize).also { it.set(m, 0, 0f, "") }
        val tv = label(m.name, 9f, true).apply { setShadowLayer(6f, 0f, 0f, T.bg); visibility = if (S.showName == 1) View.VISIBLE else View.GONE }
        box.addView(ic); box.addView(tv)
        val slot = icons.size
        val dx0 = if (m.ix >= 0) m.ix else sw - dp(S.iconSize) - dp(20)
        val dy0 = if (m.iy >= 0) m.iy else (dp(120) + slot * dp(S.iconSize + 26)) % maxOf(dp(200), sh - dp(160))
        val p = lp(-2, -2, dx0.coerceIn(0, maxOf(0, sw - dp(S.iconSize))), dy0.coerceIn(0, maxOf(0, sh - dp(S.iconSize))))
        drag(box, box, p, onTap = { macros.find { it.name == m.name }?.let { toggleRun(it) } }, onLong = { stopRun(m.name); toast("Berhenti") },
            onEnd = { m.ix = p.x; m.iy = p.y; Store.save(this, macros) })
        wm.addView(box, p); icons[m.name] = IconBox(box, ic, tv, p); refreshRun()
    }
    private fun removeIcon(n: String) { icons.remove(n)?.let { try { wm.removeView(it.box) } catch (_: Exception) {} } }

    // ================= Target auto-click (multi, multi-touch) =================
    private fun addTarget() {
        if (targets.size >= 5) { toast("Maksimal 5 target"); return }
        val slot = (1..5).first { s -> targets.none { it.slot == s } }
        val s = dp(S.tSize); val n = targets.size
        val v = TargetView(this).apply { num = n + 1; cps = S.cps; alpha = S.tAlpha / 100f }
        val p = lp(s, s, sw / 2 - s / 2 + n * dp(16), sh / 2 - s / 2 + n * dp(16))
        val t = Tgt(v, p, slot, S.cps)
        drag(v, v, p, onTap = { toggleClicker(t) }, onLong = { removeTarget(t) })
        wm.addView(v, p); targets.add(t); renumber(); toast("Geser ke titik, tap untuk mulai. Tahan untuk hapus")
    }
    private fun renumber() { targets.forEachIndexed { i, t -> t.view.num = i + 1; t.view.invalidate() } }
    private fun removeTarget(t: Tgt) { stopClicker(t); try { wm.removeView(t.view) } catch (_: Exception) {}; targets.remove(t); renumber() }
    private fun toggleClicker(t: Tgt) {
        if (t.job?.isActive == true) { stopClicker(t); return }
        if (!Shell.ready()) { toast("Shizuku belum terhubung"); return }
        val loc = IntArray(2); t.view.getLocationOnScreen(loc); val cx = loc[0] + t.view.width / 2; val cy = loc[1] + t.view.height / 2
        t.lp.flags = t.lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; wm.updateViewLayout(t.view, t.lp)
        t.view.running = true; t.view.cps = t.cps; t.view.invalidate()
        t.job = scope.launch {
            try { clickLoop(t, cx, cy) }
            finally { Shell.injM(t.slot, 2, 0, 0); withContext(NonCancellable + Dispatchers.Main) { stopClicker(t) } }
        }
        refreshRun()
    }
    private suspend fun CoroutineScope.clickLoop(t: Tgt, cx: Int, cy: Int) {
        val (w, h) = portrait(); val rt = rot(); val (rx, ry) = Touch.toRaw(cx, cy, rt, w, h)
        val t0 = System.currentTimeMillis()
        fun over() = S.autoStop > 0 && System.currentTimeMillis() - t0 > S.autoStop * 60000L
        val j = S.jitter; fun jr() = if (j == 0) 0 else Random.nextInt(-j, j + 1)
        val mode = S.tMode
        if (mode == 1 && Touch.ok) { Shell.injM(t.slot, 0, rx, ry); while (isActive && !over()) delay(100); return }
        var n = 0
        while (isActive && !over()) {
            val interval = 1000L / t.cps.coerceAtLeast(1); val hold = minOf(18L, interval / 2).coerceAtLeast(3)
            if (Touch.ok) {
                val (a, b) = if (j == 0) rx to ry else Touch.toRaw(cx + jr(), cy + jr(), rt, w, h)
                Shell.injM(t.slot, 0, a, b); delay(hold); Shell.injM(t.slot, 2, 0, 0)
            } else Shell.run("input tap ${cx + jr()} ${cy + jr()}")
            delay((interval - hold).coerceAtLeast(2))
            if (mode == 2 && ++n >= S.burstN) { n = 0; delay(S.burstGap.toLong()) }
        }
    }
    private fun stopClicker(t: Tgt) {
        t.job?.cancel(); t.job = null
        if (t.view.isAttachedToWindow) { t.lp.flags = t.lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv(); try { wm.updateViewLayout(t.view, t.lp) } catch (_: Exception) {} }
        t.view.running = false; t.view.invalidate(); refreshRun()
    }

    // ================= Rekam =================
    private fun startRecord() {
        if (!Shell.ready()) { Shell.bind(this); toast("Hubungkan Shizuku dulu"); return }
        if (!Touch.canRead) Touch.init()
        val relay = !Touch.canRead
        var name = pendingName.trim().ifEmpty { "Macro ${macros.size + 1}" }
        while (macros.any { it.name == name }) name += "'"
        val land = isLand(); closePanel()
        handle?.visibility = View.GONE; dock?.visibility = View.GONE; targets.forEach { it.view.visibility = View.GONE }
        val list = mutableListOf<Act>(); val cnt = label("0 aksi", 13f, true)
        val t0r = System.currentTimeMillis()
        fun upd() { val s = (System.currentTimeMillis() - t0r) / 1000; cnt.text = "${synchronized(list) { list.size }} aksi  ${s / 60}:${"%02d".format(s % 60)}" }
        val frame = View(this).apply { background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(3), T.hot) } }
        val fp = lp(-1, -1, touch = relay)
        if (relay) {
            var t0 = 0L; var x0 = 0f; var y0 = 0f; var lastUp = 0L
            frame.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { t0 = e.eventTime; x0 = e.rawX; y0 = e.rawY }
                    MotionEvent.ACTION_UP -> {
                        val a = Act(if (lastUp == 0L) 300 else t0 - lastUp, x0.toInt(), y0.toInt(), e.rawX.toInt(), e.rawY.toInt(), (e.eventTime - t0).coerceAtLeast(40))
                        synchronized(list) { list.add(a) }; lastUp = e.eventTime; upd()
                        fp.flags = fp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; wm.updateViewLayout(frame, fp)
                        scope.launch { perform(a, 1.0); withContext(Dispatchers.Main) { if (rec === frame) { fp.flags = fp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv(); wm.updateViewLayout(frame, fp) } } }
                    }
                }
                true
            }
        }
        wm.addView(frame, fp); rec = frame
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = rb(T.bg, 26, true); setPadding(dp(16), dp(6), dp(8), dp(6)) }
        val dot = View(this).apply { background = oval(T.hot); layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(8) } }
        bar.addView(dot); bar.addView(cnt.apply { minWidth = dp(92) })
        bar.addView(icon(R.drawable.ic_undo) { synchronized(list) { if (list.isNotEmpty()) list.removeAt(list.size - 1) }; upd() })
        bar.addView(icon(R.drawable.ic_close) { finishRecord(name, list, land, false) })
        bar.addView(icon(R.drawable.ic_check, Color.WHITE, 40, T.hot) { finishRecord(name, list, land, true) })
        bar.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val l = IntArray(2); v.getLocationOnScreen(l); barRect = Rect(l[0] - dp(24), l[1] - dp(24), l[0] + v.width + dp(24), l[1] + v.height + dp(24)) }
        wm.addView(bar, lp(-2, -2, 0, dp(36)).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }); recBar = bar
        val tk = object : Runnable { override fun run() { upd(); dot.alpha = if (dot.alpha > 0.5f) 0.25f else 1f; ui.postDelayed(this, 500) } }
        recTick = tk; ui.post(tk)
        if (!relay) {
            val parser = Recorder(list) { rx, ry -> val (w, h) = portrait(); Touch.toDisp(rx, ry, rot(), w, h) }
            parser.ignore = { x, y -> barRect.contains(x, y) }
            try { Shell.svc?.startEvents() } catch (_: Exception) {}
            recJob = scope.launch {
                while (isActive) {
                    delay(40)
                    val s = try { Shell.svc?.drainEvents() } catch (e: Exception) { null } ?: continue
                    if (s.isNotEmpty()) { s.lineSequence().forEach { parser.feed(it) }; withContext(Dispatchers.Main) { upd() } }
                }
            }
            toast("Merekam. Main seperti biasa, lalu tekan centang")
        } else toast("Mode cadangan: aksi dijalankan setelah jari diangkat. Lihat alasan di Beranda")
    }
    private fun finishRecord(name: String, list: MutableList<Act>, land: Boolean, save: Boolean) {
        recJob?.cancel(); try { Shell.svc?.stopEvents() } catch (_: Exception) {}
        recTick?.let { ui.removeCallbacks(it) }; recTick = null
        rec?.let { wm.removeView(it) }; recBar?.let { wm.removeView(it) }; rec = null; recBar = null
        handle?.visibility = View.VISIBLE; dock?.visibility = View.VISIBLE; targets.forEach { it.view.visibility = View.VISIBLE }
        if (!save) return
        val acts = synchronized(list) { list.toMutableList() }
        if (acts.isEmpty()) { toast("Belum ada aksi terekam"); return }
        val m = Macro(name, acts, 1, 500, true, land, icon = macros.size % Icons.res.size); macros.add(m); Store.save(this, macros); pendingName = ""
        addIcon(m); toast("Tersimpan: $name. Tap ikon bulat untuk jalan")
    }

    override fun onDestroy() {
        inst = null; scope.cancel(); ui.removeCallbacksAndMessages(null); recJob?.cancel(); Shell.release()
        listOf(rec, recBar, panel, dock, handle).forEach { v -> v?.let { try { wm.removeView(it) } catch (_: Exception) {} } }
        targets.toList().forEach { try { wm.removeView(it.view) } catch (_: Exception) {} }; targets.clear()
        icons.keys.toList().forEach { removeIcon(it) }; super.onDestroy()
    }
}
