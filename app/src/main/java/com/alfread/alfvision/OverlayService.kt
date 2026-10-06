package com.alfread.alfvision

import android.app.Service
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.io.File
import kotlin.math.max
import kotlin.math.min

class OverlayService : Service() {
    companion object { const val ACTION_SHOW = "show" }
    private lateinit var wm: WindowManager
    private lateinit var bubble: View
    private lateinit var panel: View
    private lateinit var selector: SelectionView
    private var selectorLp: WindowManager.LayoutParams? = null
    private var panelOpen = false
    private var answerBox: LinearLayout? = null
    private var selectionEditing = false
    private val main = Handler(Looper.getMainLooper())
    private var captureRequest: String? = null
    private var pendingQuestion: String = "What is happening in this area of the screen?"
    private var autoRunnable: Runnable? = null

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ProjectionService.ACTION_CAPTURE_RESULT) return
            val path = intent.getStringExtra(ProjectionService.EXTRA_PATH) ?: return
            selector.visibility = View.VISIBLE
            val q = pendingQuestion
            answerFromFile(path, q)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        registerReceiver(captureReceiver, IntentFilter(ProjectionService.ACTION_CAPTURE_RESULT), Context.RECEIVER_NOT_EXPORTED)
        createBubble()
        createSelector()
        createPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW) openPanel()
        return START_STICKY
    }

    private fun baseType(): Int = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun createBubble() {
        val b = TextView(this).apply {
            text = "AI"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(115, 85, 220)); setStroke(2, Color.WHITE) }
            setOnClickListener { openPanel() }
        }
        bubble = b
        val lp = WindowManager.LayoutParams(54, 54, baseType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = 16; y = 220
        }
        wm.addView(b, lp)
        dragView(b, lp)
    }

    private fun createSelector() {
        selector = SelectionView(this)
        val lp = WindowManager.LayoutParams(-1, -1, baseType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        selectorLp = lp
        wm.addView(selector, lp)
        selector.setRegion(AppPrefs.getRegion(this))
    }

    private fun createPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 14)
            background = GradientDrawable().apply { cornerRadius = 34f; setColor(Color.argb(246, 16, 18, 24)); setStroke(1, Color.argb(70, 180, 180, 190)) }
        }
        val title = TextView(this).apply { text = "ALF Vision"; textSize = 19f; setTextColor(Color.WHITE); setTypeface(null, android.graphics.Typeface.BOLD) }
        val subtitle = TextView(this).apply { text = "Screen-aware AI panel"; textSize = 12f; setTextColor(Color.rgb(160,160,170)) }
        root.addView(title)
        root.addView(subtitle)

        val status = TextView(this).apply { text = "REGION READY"; textSize = 10f; setTextColor(Color.rgb(164,140,255)); setPadding(0, 10, 0, 8) }
        root.addView(status)

        val answerScroll = ScrollView(this)
        val answerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(4, 4, 4, 4) }
        this.answerBox = answerBox
        answerScroll.addView(answerBox)
        root.addView(answerScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val input = EditText(this).apply {
            hint = "Tanya tentang area layar..."
            setTextColor(Color.WHITE); setHintTextColor(Color.rgb(120,122,132)); textSize = 14f
            setSingleLine(false); minLines = 2; maxLines = 4
            background = GradientDrawable().apply { cornerRadius = 22f; setColor(Color.rgb(27,30,38)); setStroke(1, Color.rgb(55,58,70)) }
            setPadding(14, 12, 14, 12)
        }
        root.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val select = button("Select") { selectionEditing = !selectionEditing; setSelectorTouch(selectionEditing); status.text = if (selectionEditing) "MOVE / RESIZE REGION" else "REGION READY" }
        val ask = button("Ask") {
            pendingQuestion = input.text.toString().ifBlank { "Explain clearly what is visible in this screen area and what I should do next." }
            appendBubble(answerBox, "YOU", pendingQuestion, false)
            requestCapture()
        }
        val close = button("Close") { closePanel() }
        row1.addView(select, weightParams()); row1.addView(ask, weightParams()); row1.addView(close, weightParams())
        root.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("Analyze") {
            pendingQuestion = "Analyze this screen area in detail. Identify important text, UI state, errors, buttons, and the most useful next action."
            appendBubble(answerBox, "VISION", pendingQuestion, false)
            requestCapture()
        }, weightParams())
        row2.addView(button("History") {
            val h = AppPrefs.getHistory(this)
            if (h.isEmpty()) Toast.makeText(this, "No history", Toast.LENGTH_SHORT).show() else appendBubble(answerBox, "HISTORY", h.take(6).joinToString("\n\n") { "Q: ${it.first}\nA: ${it.second.take(220)}" }, false)
        }, weightParams())
        row2.addView(button("Settings") { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, weightParams())
        root.addView(row2)

        panel = root
    }

    private fun button(text: String, click: () -> Unit): TextView = TextView(this).apply {
        this.text = text; textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { cornerRadius = 18f; setColor(Color.rgb(30,33,42)) }
        setPadding(6, 12, 6, 12); setOnClickListener { click() }
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 4; rightMargin = 4; topMargin = 6 }

    private fun appendBubble(box: LinearLayout, label: String, text: String, accent: Boolean) {
        val v = TextView(this).apply { this.text = "$label\n$text"; textSize = 13f; setTextColor(Color.WHITE); setPadding(12, 10, 12, 10); background = GradientDrawable().apply { cornerRadius = 18f; setColor(if (accent) Color.rgb(42,35,70) else Color.rgb(24,27,34)) } }
        box.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6 })
    }

    private fun openPanel() {
        if (panelOpen) return
        val lp = WindowManager.LayoutParams(345, 520, baseType(), WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 120
        }
        wm.addView(panel, lp); panelOpen = true
        bubble.visibility = View.GONE
    }

    private fun closePanel() { if (panelOpen) { wm.removeView(panel); panelOpen = false; bubble.visibility = View.VISIBLE } }

    private fun setSelectorTouch(enabled: Boolean) {
        val lp = selectorLp ?: return
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or if (!enabled) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        wm.updateViewLayout(selector, lp)
        if (!enabled) AppPrefs.saveRegion(this, selector.leftX, selector.topY, selector.rightX, selector.bottomY)
    }

    private fun requestCapture() {
        setSelectorTouch(false)
        selector.visibility = View.GONE
        main.postDelayed({
            val id = System.currentTimeMillis().toString(); captureRequest = id
            startService(Intent(this, ProjectionService::class.java).setAction(ProjectionService.ACTION_CAPTURE).putExtra(ProjectionService.EXTRA_REQUEST_ID, id))
        }, 80)
    }

    private fun answerFromFile(path: String, question: String) {
        val key = AppPrefs.getApiKey(this)
        if (key.isNullOrBlank()) { answerBox?.let { appendBubble(it, "SYSTEM", "Masukkan Groq API key di Settings terlebih dahulu.", false) }; return }
        val bmp = BitmapFactory.decodeFile(path) ?: return
        GroqClient.ask(key, AppPrefs.getModel(this), AppPrefs.getSystemPrompt(this), question, bmp) { result ->
            bmp.recycle()
            main.post {
                val text = result.getOrElse { "Request failed: ${it.message}" }
                AppPrefs.addHistory(this, question, text)
                answerBox?.let { box -> appendBubble(box, if (text.startsWith("Request failed")) "ERROR" else "ALF", text, text.startsWith("Request failed").not()) }
            }
        }
    }

    private fun dragView(v: View, lp: WindowManager.LayoutParams) {
        v.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=lp.x; oy=lp.y; return true }
                    MotionEvent.ACTION_MOVE -> { lp.x = ox + (e.rawX-sx).toInt(); lp.y = oy + (e.rawY-sy).toInt(); wm.updateViewLayout(v, lp); return true }
                }
                return false
            }
        })
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(captureReceiver) }
        autoRunnable?.let(main::removeCallbacks)
        runCatching { wm.removeView(bubble) }; runCatching { wm.removeView(selector) }; runCatching { if (panelOpen) wm.removeView(panel) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    inner class SelectionView(context: Context) : View(context) {
        var leftX=80; var topY=360; var rightX=1000; var bottomY=1200
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val dim = Paint(Paint.ANTI_ALIAS_FLAG)
        private var mode = 0
        private var lastX = 0f; private var lastY = 0f
        init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }
        fun setRegion(r:IntArray){ if(r.size==4){leftX=r[0];topY=r[1];rightX=r[2];bottomY=r[3]}; invalidate() }
        override fun onDraw(c:Canvas){
            dim.color=Color.argb(45,0,0,0); c.drawRect(0f,0f,width.toFloat(),height.toFloat(),dim)
            paint.style=Paint.Style.FILL; paint.color=Color.argb(28,155,123,255); c.drawRect(leftX.toFloat(),topY.toFloat(),rightX.toFloat(),bottomY.toFloat(),paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=5f; paint.color=Color.rgb(155,123,255); c.drawRect(leftX.toFloat(),topY.toFloat(),rightX.toFloat(),bottomY.toFloat(),paint)
            paint.style=Paint.Style.FILL; paint.color=Color.WHITE
            val s=18f; c.drawCircle(leftX.toFloat(),topY.toFloat(),s,paint); c.drawCircle(rightX.toFloat(),topY.toFloat(),s,paint); c.drawCircle(leftX.toFloat(),bottomY.toFloat(),s,paint); c.drawCircle(rightX.toFloat(),bottomY.toFloat(),s,paint)
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (!selectionEditing) return false
            val x=e.x; val y=e.y
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN -> { lastX=x; lastY=y; mode=hit(x,y); return true }
                MotionEvent.ACTION_MOVE -> {
                    val dx=(x-lastX).toInt(); val dy=(y-lastY).toInt();
                    when(mode){1->{leftX+=dx;topY+=dy;rightX+=dx;bottomY+=dy};2->{leftX+=dx;topY+=dy};3->{rightX+=dx;topY+=dy};4->{leftX+=dx;bottomY+=dy};5->{rightX+=dx;bottomY+=dy}}
                    normalize(); lastX=x;lastY=y;invalidate(); return true
                }
                MotionEvent.ACTION_UP -> { AppPrefs.saveRegion(this@OverlayService,leftX,topY,rightX,bottomY); return true }
            }
            return true
        }
        private fun hit(x:Float,y:Float):Int{
            fun near(a:Float,b:Float)=kotlin.math.abs(a-b)<55
            if(near(x, leftX.toFloat()) && near(y, topY.toFloat())) return 2
            if(near(x, rightX.toFloat()) && near(y, topY.toFloat())) return 3
            if(near(x, leftX.toFloat()) && near(y, bottomY.toFloat())) return 4
            if(near(x, rightX.toFloat()) && near(y, bottomY.toFloat())) return 5
            if (x > leftX.toFloat() && x < rightX.toFloat() && y > topY.toFloat() && y < bottomY.toFloat()) return 1
            return 0
        }
        private fun normalize(){
            leftX=leftX.coerceIn(0,width-120); topY=topY.coerceIn(0,height-160); rightX=max(leftX+160,min(rightX,width)); bottomY=max(topY+160,min(bottomY,height));
        }
    }
}
