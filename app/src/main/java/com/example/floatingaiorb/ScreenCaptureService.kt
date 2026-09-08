package com.example.floatingaiorb

import android.animation.ValueAnimator
import android.app.*
import android.content.Context
import android.content.Intent
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_SHOW_ORB = "com.example.floatingaiorb.SHOW_ORB"
        const val ACTION_START_CAPTURE = "com.example.floatingaiorb.START_CAPTURE"
        const val ACTION_STOP = "com.example.floatingaiorb.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        @Volatile var isRunning = false
        private const val CHANNEL_ID = "floating_orb_service"
        private const val NOTIFICATION_ID = 1001
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastFrame: Bitmap? = null
    private var windowManager: WindowManager? = null
    private var orbContainer: FrameLayout? = null
    private var chatPanel: View? = null
    private val network = Executors.newSingleThreadExecutor()

    override fun onCreate() { super.onCreate(); createChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_ORB -> showOrb()
            ACTION_START_CAPTURE -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtraCompat<Intent>(EXTRA_DATA)
                startCapture(code, data)
            }
            ACTION_STOP -> stopEverything()
        }
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent?) {
        if (data == null || resultCode != Activity.RESULT_OK) return
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification())

        showOrb()
        if (projection != null) return
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { releaseProjection() }
        }, Handler(Looper.getMainLooper()))

        imageReader = ImageReader.newInstance(width.coerceAtMost(1440), height.coerceAtMost(2560), PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            runCatching { reader.acquireLatestImage()?.use { img -> captureFrame(img) } }
        }, Handler(Looper.getMainLooper()))
        virtualDisplay = projection?.createVirtualDisplay(
            "FloatingAIOrbCapture", imageReader!!.width, imageReader!!.height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader!!.surface, null, null
        )
        isRunning = true
    }

    private fun captureFrame(image: Image) {
        val plane = image.planes.firstOrNull() ?: return
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val temp = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind(); temp.copyPixelsFromBuffer(buffer)
        val cropped = if (temp.width != image.width) Bitmap.createBitmap(temp, 0, 0, image.width, image.height) else temp
        if (cropped !== temp) temp.recycle()
        synchronized(this) {
            lastFrame?.recycle()
            lastFrame = cropped.copy(Bitmap.Config.ARGB_8888, false)
        }
        cropped.recycle()
    }

    private fun showOrb() {
        if (!Settings.canDrawOverlays(this) || orbContainer != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = FrameLayout(this)
        val orb = OrbView(this)
        root.addView(orb, FrameLayout.LayoutParams(78, 78))
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(78, 78, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END; x = 18; y = 190
        }
        orb.setOnTouchListener(DragTouchListener(root, params))
        orb.setOnClickListener { showChatPanel() }
        try { windowManager?.addView(root, params); orbContainer = root } catch (_: Exception) { orbContainer = null }
    }

    private fun showChatPanel() {
        if (chatPanel != null) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 14)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF141A29.toInt(), 0xFF0B0F1A.toInt())).apply { cornerRadius = 36f; setStroke(2, 0x557C4DFF) }
        }
        val title = TextView(this).apply { text = "✦  Floating AI"; textSize = 20f; setTextColor(Color.WHITE) }
        val sub = TextView(this).apply { text = "Chat • Vision • Screen"; textSize = 12f; setTextColor(0xFF9AA6BC.toInt()) }
        val transcript = TextView(this).apply { text = "Siap. Masukkan pertanyaan."; textSize = 15f; setTextColor(0xFFE6EAF2.toInt()); setPadding(4, 14, 4, 10) }
        val input = EditText(this).apply { hint = "Tanya AI..."; setSingleLine(false); maxLines = 3; setTextColor(Color.WHITE); setHintTextColor(0xFF7F8AA0.toInt()) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val send = Button(this).apply { text = "Kirim" }
        val vision = Button(this).apply { text = "Lihat layar" }
        row.addView(send, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); row.addView(vision, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(title); panel.addView(sub); panel.addView(transcript, LinearLayout.LayoutParams(-1, 0, 1f)); panel.addView(input); panel.addView(row)

        send.setOnClickListener {
            val q = input.text?.toString()?.trim().orEmpty()
            if (q.isBlank()) return@setOnClickListener
            appendText(transcript, "\n\nKamu: $q")
            input.setText("")
            callAi(transcript, q, null)
        }
        vision.setOnClickListener {
            val q = input.text?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: "Apa yang terlihat di layar ini? Jelaskan bagian yang penting."
            synchronized(this) { lastFrame?.let { bmp -> appendText(transcript, "\n\nKamu: $q [SCREEN]"); callAi(transcript, q, bmp) } ?: appendText(transcript, "\n\nBelum ada frame layar. Aktifkan Screen Capture dari aplikasi." ) }
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val dw = (resources.displayMetrics.widthPixels * 0.90f).toInt().coerceAtLeast(420)
        val dh = (resources.displayMetrics.heightPixels * 0.62f).toInt().coerceAtLeast(620)
        val lp = WindowManager.LayoutParams(dw, dh, overlayType, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        try { windowManager?.addView(panel, lp); chatPanel = panel } catch (_: Exception) { chatPanel = null }
    }

    private fun appendText(tv: TextView, more: String) { tv.text = tv.text.toString() + more; (tv.parent as? ScrollView)?.post { } }

    private fun callAi(transcript: TextView, prompt: String, image: Bitmap?) {
        val prefs = getSharedPreferences("orb", Context.MODE_PRIVATE)
        val key = prefs.getString("key", "").orEmpty()
        val model = prefs.getString("model", AIClient.DEFAULT_MODEL).orEmpty()
        val endpoint = prefs.getString("endpoint", AIClient.DEFAULT_ENDPOINT).orEmpty()
        if (key.isBlank()) { appendText(transcript, "\nAI: API key belum diisi di aplikasi."); return }
        appendText(transcript, "\nAI: sedang berpikir…")
        network.execute {
            val answer = runCatching { AIClient.chat(key, endpoint, model, prompt, image).text }.getOrElse { "Error: ${it.message}" }
            Handler(Looper.getMainLooper()).post { appendText(transcript, "\nAI: $answer") }
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_orb_logo).setContentTitle("Floating AI Orb").setContentText(if (isRunning) "Vision aktif" else "Orb aktif")
        .setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Floating AI Orb", NotificationManager.IMPORTANCE_LOW))
    }

    private fun releaseProjection() {
        virtualDisplay?.release(); virtualDisplay = null; imageReader?.close(); imageReader = null; projection = null; isRunning = false
        synchronized(this) { lastFrame?.recycle(); lastFrame = null }
    }

    private fun stopEverything() {
        releaseProjection()
        chatPanel?.let { runCatching { windowManager?.removeView(it) } }; chatPanel = null
        orbContainer?.let { runCatching { windowManager?.removeView(it) } }; orbContainer = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() { stopEverything(); network.shutdownNow(); super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}

private class OrbView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply { duration = 1500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator(); addUpdateListener { phase = it.animatedValue as Float; invalidate() } }
    init { anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); val c = width / 2f; val r = width * (0.30f + phase * 0.035f)
        paint.shader = RadialGradient(c, c, width * 0.55f, intArrayOf(0x009E7BFF, 0x558B5CF6, 0x00000000), null, Shader.TileMode.CLAMP); canvas.drawCircle(c, c, width * 0.5f, paint)
        paint.shader = null; paint.color = 0xFF8B5CF6.toInt(); canvas.drawCircle(c, c, r, paint)
        paint.color = 0xFFEDE9FE.toInt(); canvas.drawCircle(c, c, r * 0.62f, paint)
        paint.color = 0xFF7C3AED.toInt(); canvas.drawCircle(c, c, r * 0.20f, paint)
        paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER; paint.textSize = width * 0.20f; canvas.drawText("✦", c, c - width * 0.08f, paint)
    }
}

private class DragTouchListener(private val root: View, private val lp: WindowManager.LayoutParams) : View.OnTouchListener {
    private var downX = 0f; private var downY = 0f; private var startX = 0; private var startY = 0; private var moved = false
    override fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false; return false }
            MotionEvent.ACTION_MOVE -> { val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt(); if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true; lp.x = startX - dx; lp.y = startY + dy; (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(root, lp); return true }
        }
        return moved
    }
}

private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(key: String): T? = if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else { @Suppress("DEPRECATION") getParcelableExtra(key) }
