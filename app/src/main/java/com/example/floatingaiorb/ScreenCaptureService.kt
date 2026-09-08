package com.example.floatingaiorb

import android.Manifest
import android.animation.ValueAnimator
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_SHOW_ORB = "com.example.floatingaiorb.SHOW_ORB"
        const val ACTION_START_CAPTURE = "com.example.floatingaiorb.START_CAPTURE"
        const val ACTION_STOP = "com.example.floatingaiorb.STOP"
        const val ACTION_REQUEST_CAPTURE = "com.example.floatingaiorb.REQUEST_CAPTURE"
        const val ACTION_VOICE = "com.example.floatingaiorb.VOICE"
        const val ACTION_OPEN_TIKTOK = "com.example.floatingaiorb.OPEN_TIKTOK"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        @Volatile var isRunning = false
        private const val CHANNEL_ID = "floating_orb_service"
        private const val NOTIFICATION_ID = 1001
        private const val HISTORY_KEY = "overlay_history_v2"
        private const val DRAFT_KEY = "overlay_draft_v2"
        private const val MAX_HISTORY = 60
    }

    private data class HistoryItem(val who: String, val text: String, val screen: Boolean = false)

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastFrame: Bitmap? = null
    private var windowManager: WindowManager? = null
    private var orbContainer: FrameLayout? = null
    private var chatPanel: View? = null
    private var chatInput: EditText? = null
    private var chatMessages: LinearLayout? = null
    private var chatScroll: ScrollView? = null
    private val network = Executors.newSingleThreadExecutor()
    private val history = mutableListOf<HistoryItem>()
    private var draftText = ""
    private var voiceEngine: VoiceEngine? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        loadPersistentChat()
        voiceEngine = VoiceEngine(
            this,
            onState = { state ->
                mainHandler.post {
                    when (state) {
                        VoiceEngine.State.LISTENING -> setVoiceStatus("Mendengarkan…")
                        VoiceEngine.State.ERROR -> setVoiceStatus("Voice perlu izin mic")
                        else -> setVoiceStatus("")
                    }
                }
            },
            onText = { text ->
                mainHandler.post {
                    chatInput?.setText(text)
                    chatInput?.setSelection(chatInput?.text?.length ?: 0)
                    setVoiceStatus("Teks dari Voice siap dikirim")
                    chatInput?.requestFocus()
                }
            }
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_ORB -> showOrb()
            ACTION_START_CAPTURE -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtraCompat<Intent>(EXTRA_DATA)
                startCapture(code, data)
            }
            ACTION_REQUEST_CAPTURE -> requestCaptureFromActivity()
            ACTION_VOICE -> toggleVoice()
            ACTION_OPEN_TIKTOK -> openTikTok()
            ACTION_STOP -> stopEverything()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent?) {
        if (data == null || resultCode != Activity.RESULT_OK) return
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        showOrb()
        if (projection != null) return
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtMost(1440)
        val height = metrics.heightPixels.coerceAtMost(2560)
        val density = metrics.densityDpi
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { releaseProjection() }
        }, mainHandler)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            runCatching { reader.acquireLatestImage()?.use { captureFrame(it) } }
        }, mainHandler)
        virtualDisplay = projection?.createVirtualDisplay(
            "FloatingAIOrbCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            null
        )
        isRunning = true
        setVoiceStatus("")
        savePersistentChat()
    }

    private fun captureFrame(image: Image) {
        val plane = image.planes.firstOrNull() ?: return
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val temp = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        buffer.rewind()
        temp.copyPixelsFromBuffer(buffer)
        val cropped = if (temp.width != image.width) Bitmap.createBitmap(temp, 0, 0, image.width, image.height) else temp
        if (cropped !== temp) temp.recycle()
        val safeCopy = cropped.copy(Bitmap.Config.ARGB_8888, false)
        synchronized(this) {
            lastFrame?.recycle()
            lastFrame = safeCopy
        }
        cropped.recycle()
    }

    private fun showOrb() {
        if (!Settings.canDrawOverlays(this)) return
        if (orbContainer != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = dp(66)
        val root = FrameLayout(this)
        val orb = OrbView(this)
        root.addView(orb, FrameLayout.LayoutParams(size, size))
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(150)
        }
        var moved = false
        var downX = 0f
        var downY = 0f
        var startX = params.x
        var startY = params.y
        orb.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    moved = false
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).roundToInt()
                    val dy = (event.rawY - downY).roundToInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params.x = startX - dx
                    params.y = startY + dy
                    runCatching { windowManager?.updateViewLayout(root, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) showChatPanel()
                    v.performClick()
                    true
                }
                else -> true
            }
        }
        runCatching {
            windowManager?.addView(root, params)
            orbContainer = root
        }
    }

    private fun showChatPanel() {
        if (chatPanel != null || windowManager == null) return
        val dm = resources.displayMetrics
        val panelW = (dm.widthPixels * 0.92f).roundToInt().coerceAtLeast(dp(300))
        val panelH = (dm.heightPixels * 0.70f).roundToInt().coerceIn(dp(390), (dm.heightPixels * 0.84f).roundToInt())
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF141A29.toInt(), 0xFF090D17.toInt())
            ).apply { cornerRadius = dp(23).toFloat(); setStroke(dp(1), 0x667D52EA) }
            elevation = dp(20).toFloat()
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_orb_logo)
            background = rounded(0xFF25153F.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)))
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(9), 0, 0, 0) }
        titleBox.addView(TextView(this).apply { text = "Floating AI"; textSize = 18f; setTextColor(Color.WHITE); setTypeface(null, android.graphics.Typeface.BOLD) })
        val voiceStatus = TextView(this).apply { text = if (isRunning) "Voice • Vision • Screen" else "Voice • Vision"; textSize = 10f; setTextColor(0xFF9FA9BA.toInt()); tag = "voiceStatus" }
        titleBox.addView(voiceStatus)
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(this).apply {
            text = "×"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener { hideChatPanel() }
        }
        header.addView(close, LinearLayout.LayoutParams(dp(42), dp(42)))
        panel.addView(header)

        val hint = TextView(this).apply {
            text = "Tarik header untuk memindahkan panel • riwayat chat tersimpan"
            textSize = 10f
            setTextColor(0xFF778399.toInt())
            setPadding(0, dp(6), 0, dp(4))
        }
        panel.addView(hint)

        val quickScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val quickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val clear = actionButton("Bersihkan")
        val copy = actionButton("Salin")
        val tiktok = actionButton("TikTok")
        val screenStatus = actionButton(if (isRunning) "Screen ON" else "Screen")
        val summarize = actionButton("Ringkas")
        listOf(clear, copy, tiktok, screenStatus, summarize).forEachIndexed { index, button ->
            quickRow.addView(button, LinearLayout.LayoutParams(dp(86), dp(38)).apply { if (index > 0) leftMargin = dp(6) })
        }
        quickScroll.addView(quickRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
        panel.addView(quickScroll)

        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS }
        val messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(5), 0, dp(5)) }
        scroll.addView(messages, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        if (history.isEmpty()) {
            addHistoryAndBubble(messages, "ai", "Siap. Chat kamu akan tetap ada saat panel ditutup. Kamu juga bisa Voice, Screen, atau buka TikTok.")
        } else {
            history.forEach { addBubble(messages, it.who, it.text, it.screen) }
        }

        val input = EditText(this).apply {
            hint = "Tanya AI…"
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF78859A.toInt())
            setPadding(dp(13), dp(8), dp(13), dp(8))
            background = roundedStroke(0xFF0F1522.toInt(), 0x667E5BF0.toInt(), dp(12))
            maxLines = 4
            setText(draftText)
            setSelection(text?.length ?: 0)
        }
        chatInput = input
        chatMessages = messages
        chatScroll = scroll
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(7) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val send = actionButton("Kirim")
        val screen = actionButton("Lihat layar")
        val voice = actionButton("Voice")
        row.addView(send, LinearLayout.LayoutParams(0, dp(46), 1f))
        row.addView(screen, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(6) })
        row.addView(voice, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(6) })
        panel.addView(row, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(7) })

        fun doSend(attachScreen: Boolean) {
            val q = input.text?.toString()?.trim().orEmpty()
            val finalQ = if (q.isBlank() && attachScreen) "Apa yang terlihat di layar ini? Jelaskan hal penting secara singkat." else q
            if (finalQ.isBlank()) return
            addHistoryAndBubble(messages, "user", finalQ, attachScreen)
            input.setText("")
            draftText = ""
            val imageForAi = synchronized(this) { if (attachScreen) lastFrame?.copy(Bitmap.Config.ARGB_8888, false) else null }
            if (attachScreen) {
                if (imageForAi == null) {
                    addHistoryAndBubble(messages, "ai", "Screen Vision belum aktif. Tekan Screen di aplikasi utama sekali untuk memberi izin, lalu coba lagi.")
                    savePersistentChat()
                    return
                }
                val preview = imageForAi.copy(Bitmap.Config.ARGB_8888, false)
                addScreenPreview(messages, preview)
            }
            callAi(messages, scroll, finalQ, imageForAi)
            savePersistentChat()
        }

        input.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) { draftText = input.text?.toString().orEmpty(); savePersistentChat() } }
        send.setOnClickListener { doSend(false) }
        screen.setOnClickListener { doSend(true) }
        clear.setOnClickListener {
            history.clear()
            input.setText("")
            draftText = ""
            messages.removeAllViews()
            addHistoryAndBubble(messages, "ai", "Chat dibersihkan. Mulai lagi dari sini.")
            savePersistentChat()
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
        copy.setOnClickListener {
            val latest = history.lastOrNull { it.who == "ai" }?.text
            if (latest.isNullOrBlank()) {
                Toast.makeText(this, "Belum ada jawaban AI untuk disalin.", Toast.LENGTH_SHORT).show()
            } else {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Floating AI", latest))
                Toast.makeText(this, "Jawaban terakhir disalin.", Toast.LENGTH_SHORT).show()
            }
        }
        tiktok.setOnClickListener { openTikTok() }
        screenStatus.setOnClickListener {
            if (isRunning) {
                addHistoryAndBubble(messages, "ai", "Screen Vision sedang aktif. Kamu bisa menekan Lihat layar untuk mengirim snapshot ke AI.")
                savePersistentChat()
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            } else {
                requestCaptureFromActivity()
            }
        }
        summarize.setOnClickListener {
            val q = "Ringkas isi layar ini menjadi poin-poin penting, singkat dan jelas."
            val image = synchronized(this) { lastFrame?.copy(Bitmap.Config.ARGB_8888, false) }
            if (image == null) {
                addHistoryAndBubble(messages, "ai", "Screen Vision belum aktif. Tekan Screen dulu untuk menyalakannya.")
            } else {
                addHistoryAndBubble(messages, "user", q, true)
                addScreenPreview(messages, image.copy(Bitmap.Config.ARGB_8888, false))
                callAi(messages, scroll, q, image)
                savePersistentChat()
            }
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
        voice.setOnClickListener { toggleVoice() }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            panelW,
            panelH,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        var dragX = 0f
        var dragY = 0f
        var baseX = 0
        var baseY = 0
        header.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragX = event.rawX; dragY = event.rawY; baseX = lp.x; baseY = lp.y; true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = baseX + (event.rawX - dragX).roundToInt()
                    lp.y = baseY + (event.rawY - dragY).roundToInt()
                    runCatching { windowManager?.updateViewLayout(panel, lp) }
                    true
                }
                else -> true
            }
        }
        runCatching {
            windowManager?.addView(panel, lp)
            chatPanel = panel
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun addScreenPreview(c: LinearLayout, bitmap: Bitmap) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.START; setPadding(dp(2), dp(3), dp(2), dp(3)) }
        val label = TextView(this).apply { text = "Kamu • snapshot layar"; textSize = 10f; setTextColor(0xFFBFA8FF.toInt()); setPadding(dp(6), dp(2), dp(6), dp(4)) }
        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(0xFF151B2B.toInt())
            adjustViewBounds = true
            maxHeight = dp(210)
        }
        row.addView(label)
        row.addView(image, LinearLayout.LayoutParams(-1, dp(190)))
        c.addView(row)
    }

    private fun callAi(messages: LinearLayout, scroll: ScrollView, prompt: String, image: Bitmap?) {
        addBubble(messages, "ai", "Sedang menjawab…", typing = true)
        val prefs = getSharedPreferences("orb", Context.MODE_PRIVATE)
        val key = prefs.getString("key", "").orEmpty()
        val model = prefs.getString("model", AIClient.DEFAULT_MODEL).orEmpty()
        val endpoint = prefs.getString("endpoint", AIClient.DEFAULT_ENDPOINT).orEmpty()
        if (key.isBlank()) {
            removeTyping(messages)
            addHistoryAndBubble(messages, "ai", "API key belum diisi. Buka SETUP di aplikasi.")
            savePersistentChat()
            return
        }
        network.execute {
            val answer = runCatching { AIClient.chat(key, endpoint, model, prompt, image).text }
                .getOrElse { it.message ?: "Permintaan gagal" }
            image?.let { runCatching { it.recycle() } }
            mainHandler.post {
                removeTyping(messages)
                addHistoryAndBubble(messages, "ai", answer)
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                savePersistentChat()
            }
        }
    }

    private fun addBubble(c: LinearLayout, who: String, text: String, screen: Boolean = false, typing: Boolean = false) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (who == "user") Gravity.END else Gravity.START
            setPadding(dp(2), dp(3), dp(2), dp(3))
        }
        val tv = TextView(this).apply {
            this.text = if (screen) "▣  $text" else text
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            maxWidth = (resources.displayMetrics.widthPixels * 0.76f).roundToInt()
            background = if (who == "user") rounded(0xFF6638C8.toInt()) else rounded(0xFF232A3A.toInt())
            tag = if (typing) "typing" else null
        }
        row.addView(tv)
        c.addView(row)
    }

    private fun addHistoryAndBubble(c: LinearLayout, who: String, text: String, screen: Boolean = false) {
        addBubble(c, who, text, screen)
        if (text.isNotBlank()) {
            history.add(HistoryItem(who, text, screen))
            while (history.size > MAX_HISTORY) history.removeAt(0)
        }
        c.post { chatScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun removeTyping(c: LinearLayout) {
        for (i in c.childCount - 1 downTo 0) {
            val row = c.getChildAt(i) as? LinearLayout ?: continue
            val tv = row.getChildAt(0) as? TextView ?: continue
            if (tv.tag == "typing") { c.removeViewAt(i); return }
        }
    }

    private fun toggleVoice() {
        val engine = voiceEngine ?: return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Toast.makeText(this, "Izin mikrofon belum diberikan. Buka Floating AI sekali untuk mengizinkannya.", Toast.LENGTH_LONG).show()
            return
        }
        if (AppMemory.voiceState == VoiceEngine.State.LISTENING) {
            engine.stopListening()
            setVoiceStatus("")
        } else {
            if (Build.VERSION.SDK_INT >= 29) runCatching { startForeground(NOTIFICATION_ID, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) }
            else runCatching { startForeground(NOTIFICATION_ID, notification()) }
            engine.startListening("id-ID")
        }
    }

    private fun setVoiceStatus(text: String) {
        val panel = chatPanel ?: return
        val tv = panel.findViewWithTag<TextView>("voiceStatus") ?: return
        tv.text = if (text.isBlank()) {
            if (isRunning) "Voice • Vision • Screen" else "Voice • Vision"
        } else text
    }

    private fun requestCaptureFromActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("REQUEST_SCREEN", true)
        }
        runCatching { startActivity(intent) }
    }

    private fun openTikTok() {
        val packageName = "com.zhiliaoapp.musically"
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch == null) {
            Toast.makeText(this, "TikTok belum terpasang.", Toast.LENGTH_SHORT).show()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(launch) }
    }

    private fun loadPersistentChat() {
        val prefs = getSharedPreferences("orb", Context.MODE_PRIVATE)
        draftText = prefs.getString(DRAFT_KEY, "").orEmpty()
        history.clear()
        runCatching {
            val array = JSONArray(prefs.getString(HISTORY_KEY, "[]"))
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                history.add(HistoryItem(item.optString("who", "ai"), item.optString("text", ""), item.optBoolean("screen", false)))
            }
        }
    }

    private fun savePersistentChat() {
        val prefs = getSharedPreferences("orb", Context.MODE_PRIVATE)
        val array = JSONArray()
        history.takeLast(MAX_HISTORY).forEach { item ->
            array.put(JSONObject().apply {
                put("who", item.who)
                put("text", item.text)
                put("screen", item.screen)
            })
        }
        prefs.edit().putString(HISTORY_KEY, array.toString()).putString(DRAFT_KEY, draftText).apply()
    }

    private fun actionButton(text: String) = Button(this).apply {
        this.text = text
        textSize = 11f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(0xFF6137C1.toInt())
        stateListAnimator = null
    }

    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(14).toFloat() }
    private fun roundedStroke(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply { setColor(fill); setStroke(dp(1), stroke); cornerRadius = radius.toFloat() }

    private fun hideChatPanel() {
        chatInput?.let { draftText = it.text?.toString().orEmpty() }
        savePersistentChat()
        chatPanel?.let { runCatching { windowManager?.removeView(it) } }
        chatPanel = null
        chatInput = null
        chatMessages = null
        chatScroll = null
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_orb_logo)
        .setContentTitle("Floating AI Orb")
        .setContentText(if (isRunning) "Screen Vision aktif" else "Orb aktif")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Floating AI Orb", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun releaseProjection() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection = null
        isRunning = false
        synchronized(this) {
            lastFrame?.recycle()
            lastFrame = null
        }
        setVoiceStatus("")
    }

    private fun stopEverything() {
        runCatching { voiceEngine?.stopListening() }
        voiceEngine?.release()
        voiceEngine = null
        savePersistentChat()
        releaseProjection()
        hideChatPanel()
        orbContainer?.let { runCatching { windowManager?.removeView(it) } }
        orbContainer = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        savePersistentChat()
        runCatching { voiceEngine?.release() }
        voiceEngine = null
        releaseProjection()
        chatPanel?.let { runCatching { windowManager?.removeView(it) } }
        orbContainer?.let { runCatching { windowManager?.removeView(it) } }
        network.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}

private class OrbView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init { contentDescription = "Floating AI Orb"; anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val x = width / 2f
        val r = width * (0.30f + phase * 0.045f)
        paint.shader = RadialGradient(x, x, width * .56f, intArrayOf(0x009B6DFF, 0x665C32C8, 0x00000000), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, x, width * .5f, paint)
        paint.shader = null
        paint.color = 0xFF8B5CF6.toInt(); c.drawCircle(x, x, r, paint)
        paint.color = 0xFFEDE9FE.toInt(); c.drawCircle(x, x, r * .62f, paint)
        paint.color = 0xFF6D35D4.toInt(); c.drawCircle(x, x, r * .22f, paint)
        paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER; paint.textSize = width * .22f
        c.drawText("✦", x, x - width * .08f, paint)
    }
}

private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else @Suppress("DEPRECATION") getParcelableExtra(key)
