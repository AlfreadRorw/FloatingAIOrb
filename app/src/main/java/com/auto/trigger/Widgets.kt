package com.auto.trigger
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Ikon bulat/kotak macro: glyph, cincin progres, status jalan/jeda, badge kecepatan */
class RoundIcon(c: Context, var sizeDp: Int = 48) : View(c) {
    private var res = R.drawable.ic_bolt; private var base = Color.WHITE
    private var state = 0; private var prog = 0f; private var badge = ""
    private val p = Paint(Paint.ANTI_ALIAS_FLAG); private val rect = RectF(); private var dr: Drawable? = null; private var drRes = -1
    fun set(m: Macro, state: Int, prog: Float, badge: String) {
        this.state = state; this.prog = prog; this.badge = badge; base = Pal.of(m)
        res = when (state) { 1 -> R.drawable.ic_pause; 2 -> R.drawable.ic_play; else -> Icons.of(m) }; invalidate()
    }
    override fun onMeasure(w: Int, h: Int) { val s = context.dp(sizeDp); setMeasuredDimension(s, s) }
    override fun onDraw(cv: Canvas) {
        val s = width.toFloat(); val st = context.dp(3).toFloat()
        val fill = when (state) { 1 -> T.green; 2 -> T.orange; else -> base }
        rect.set(st, st, s - st, s - st)
        p.style = Paint.Style.FILL; p.color = fill
        if (S.shape == 0) cv.drawOval(rect, p) else cv.drawRoundRect(rect, s * 0.3f, s * 0.3f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = st * 0.8f
        rect.set(st / 2, st / 2, s - st / 2, s - st / 2)
        p.color = T.bg; p.alpha = 255
        if (S.shape == 0) cv.drawOval(rect, p) else cv.drawRoundRect(rect, s * 0.32f, s * 0.32f, p)
        if (state != 0 && prog > 0f) { p.color = Color.WHITE; p.strokeCap = Paint.Cap.ROUND; cv.drawArc(rect, -90f, 360f * prog.coerceIn(0f, 1f), false, p); p.strokeCap = Paint.Cap.BUTT }
        if (dr == null || drRes != res) { dr = context.getDrawable(res)?.mutate(); drRes = res }
        dr?.let { d -> d.setTint(T.on(fill)); val pad = (s * 0.27f).toInt(); d.setBounds(pad, pad, (s - pad).toInt(), (s - pad).toInt()); d.draw(cv) }
        if (badge.isNotEmpty()) {
            p.style = Paint.Style.FILL; p.textSize = context.dp(9).toFloat(); p.typeface = Typeface.DEFAULT_BOLD
            val tw = p.measureText(badge); val pw = tw + context.dp(8); val ph = context.dp(14).toFloat()
            rect.set(s - pw, s - ph, s, s); p.color = T.bg; cv.drawRoundRect(rect, ph / 2, ph / 2, p)
            p.color = T.fg; cv.drawText(badge, s - pw / 2 - tw / 2, s - ph / 2 + p.textSize * 0.35f, p)
        }
    }
}

/** Bar progres tipis */
class Bar(c: Context) : View(c) {
    var frac = 0f; var color = T.green
    private val p = Paint(Paint.ANTI_ALIAS_FLAG); private val r = RectF()
    override fun onDraw(cv: Canvas) {
        val h = height.toFloat(); r.set(0f, 0f, width.toFloat(), h); p.color = T.line; cv.drawRoundRect(r, h / 2, h / 2, p)
        if (frac > 0f) { r.set(0f, 0f, width * frac.coerceIn(0f, 1f), h); p.color = color; cv.drawRoundRect(r, h / 2, h / 2, p) }
    }
}

/** Target auto-click */
class TargetView(c: Context) : View(c) {
    var running = false; var num = 1; var cps = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val s = width.toFloat(); val c = s / 2; val st = context.dp(3).toFloat()
        val col = if (running) T.green else T.accent
        p.style = Paint.Style.FILL; p.color = (col and 0xFFFFFF) or 0x55000000; cv.drawCircle(c, c, c - st, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = st; p.color = col; cv.drawCircle(c, c, c - st, p)
        p.strokeWidth = st / 2; val k = s * 0.14f
        cv.drawLine(c, st * 2, c, st * 2 + k, p); cv.drawLine(c, s - st * 2, c, s - st * 2 - k, p)
        cv.drawLine(st * 2, c, st * 2 + k, c, p); cv.drawLine(s - st * 2, c, s - st * 2 - k, c, p)
        p.style = Paint.Style.FILL; p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = s * 0.3f; cv.drawText(if (running) "$cps/s" else "$num", c, c + p.textSize * 0.35f, p)
    }
}

/** Pemilih ikon + warna. Memperbarui dirinya sendiri; onChange dipanggil setelah macro diubah. */
fun Context.pickerView(m: Macro, onChange: () -> Unit): View {
    val ctx = this
    val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    fun fill() {
        box.removeAllViews()
        box.addView(LinearLayout(ctx).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(8))
            addView(RoundIcon(ctx, 56).also { it.set(m, 0, 0f, "") }) })
        box.addView(label("IKON", 10f, true, T.sub).apply { letterSpacing = 0.15f; setPadding(dp(4), 0, 0, dp(4)) })
        var row: LinearLayout? = null
        Icons.res.forEachIndexed { i, r ->
            if (i % 6 == 0) { row = LinearLayout(ctx).apply { gravity = Gravity.CENTER }; box.addView(row) }
            val sel = m.icon == i
            row!!.addView(icon(r, if (sel) T.on(T.accent) else T.fg, 44, if (sel) T.accent else T.bg) { m.icon = i; onChange(); fill() })
        }
        box.addView(label("WARNA", 10f, true, T.sub).apply { letterSpacing = 0.15f; setPadding(dp(4), dp(10), 0, dp(4)) })
        val cr = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
        Pal.colors.forEachIndexed { i, col ->
            val c = if (i == 0) T.fg else col
            cr.addView(View(ctx).apply {
                background = oval(c).apply { if (m.color == i) setStroke(dp(3), T.accent) else setStroke(dp(1), T.line) }
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { setMargins(dp(3), 0, dp(3), 0) }
                setOnClickListener { hap(this); m.color = i; onChange(); fill() } })
        }
        box.addView(cr)
    }
    fill(); return box
}
