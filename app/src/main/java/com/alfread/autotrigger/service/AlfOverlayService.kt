package com.alfread.autotrigger.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.*
import com.alfread.autotrigger.MainActivity
import com.alfread.autotrigger.model.Macro
import com.alfread.autotrigger.RecorderEngine
import com.alfread.autotrigger.MacroRunner
import com.alfread.autotrigger.repo.MacroRepository
import kotlin.math.roundToInt

class AlfOverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var handle: View
    private var panel: LinearLayout? = null
    private val repo by lazy { MacroRepository(this) }
    private var handleY = 420
    private var handleXSide = 1

    private val dp get() = resources.displayMetrics.density
    private fun d(v: Int) = (v * dp).roundToInt()

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        createChannel(); startForeground(17, notification()); createHandle()
    }

    private fun notification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "alf_overlay")
            .setContentTitle("ALF Auto Trigger aktif")
            .setContentText("Panel tepi siap digunakan")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true).build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("alf_overlay", "ALF Auto Trigger", NotificationManager.IMPORTANCE_LOW))
    }

    private fun lp(width: Int, height: Int) = WindowManager.LayoutParams(
        d(width), d(height), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        android.graphics.PixelFormat.TRANSLUCENT
    )

    private fun createHandle() {
        handle = TextView(this).apply {
            text = ""
            background = GradientDrawable().apply { cornerRadius = d(4).toFloat(); setColor(Color.WHITE) }
            alpha = .92f
            setOnClickListener { togglePanel() }
        }
        val p = lp(5, 105); p.gravity = Gravity.TOP or Gravity.RIGHT; p.y = handleY
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm.addView(handle, p)
        handle.setOnTouchListener(object : View.OnTouchListener {
            var downY = 0f; var startY = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downY = e.rawY; startY = (v.layoutParams as WindowManager.LayoutParams).y; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val q = v.layoutParams as WindowManager.LayoutParams
                        val dy = (e.rawY - downY)
                        val dx = e.rawX - (if (handleXSide > 0) resources.displayMetrics.widthPixels - d(5) else d(5))
                        q.y = (startY + dy).toInt().coerceIn(0, resources.displayMetrics.heightPixels - d(105))
                        if (kotlin.math.abs(dx) > d(40)) {
                            handleXSide = if (e.rawX < resources.displayMetrics.widthPixels / 2f) -1 else 1
                            q.gravity = Gravity.TOP or if (handleXSide > 0) Gravity.RIGHT else Gravity.LEFT
                        }
                        wm.updateViewLayout(v, q); handleY = q.y; return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val moved = kotlin.math.abs(e.rawY - downY) > d(12)
                        if (moved) return true
                    }
                }
                return false
            }
        })
    }

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(12), d(12), d(12), d(12))
            background = GradientDrawable().apply { cornerRadius = d(22).toFloat(); setColor(Color.argb(246, 18, 20, 26)) }
        }
        val title = TextView(this).apply { text = "ALF AUTO TRIGGER"; setTextColor(Color.WHITE); textSize = 15f; setPadding(d(4),0,d(4),d(8)) }
        panel!!.addView(title)
        repo.all().filter { it.enabled }.forEach { macro -> panel!!.addView(macroRow(macro)) }
        panel!!.addView(button("RECORD MACRO") { startRecording() })
        panel!!.addView(button("STOP RECORDING") { stopRecording() })
        panel!!.addView(button("EDIT / SETTINGS") { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        val p = lp(275, 0); p.height = WindowManager.LayoutParams.WRAP_CONTENT; p.gravity = Gravity.TOP or if (handleXSide > 0) Gravity.RIGHT else Gravity.LEFT; p.y = (handleY - d(65)).coerceAtLeast(d(10))
        wm.addView(panel, p)
    }

    private fun macroRow(m: Macro): View {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(d(8),d(8),d(4),d(8)) }
        val name = TextView(this).apply { text = m.name; setTextColor(Color.WHITE); textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val play = Button(this).apply { text = "PLAY"; setOnClickListener { MacroRunner.play(m); Toast.makeText(this@AlfOverlayService, "${m.name} berjalan", Toast.LENGTH_SHORT).show() } }
        row.addView(name); row.addView(play)
        return row
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }

    private fun startRecording() {
        if (RecorderEngine.recording) { Toast.makeText(this, "REC sudah aktif", Toast.LENGTH_SHORT).show(); closePanel(); return }
        val dm = resources.displayMetrics
        RecorderEngine.start(dm.widthPixels, dm.heightPixels) { ok, info ->
            Handler(mainLooper).post {
                Toast.makeText(this, if (ok) "REC aktif • sentuh layar seperti biasa" else "REC gagal: $info", Toast.LENGTH_LONG).show()
                if (ok) closePanel()
            }
        }
    }

    private fun stopRecording() {
        val recorded = RecorderEngine.stop()
        if (recorded.isEmpty()) { Toast.makeText(this, "Tidak ada aksi terekam", Toast.LENGTH_SHORT).show(); return }
        val input = EditText(this).apply { hint = "Nama macro"; setText("Macro ${repo.all().size + 1}") }
        val dialog = AlertDialog.Builder(this).setTitle("Simpan macro").setView(input)
            .setPositiveButton("SIMPAN") { _, _ ->
                val m = MacroRepository.newMacro(input.text.toString().ifBlank { "Macro" }); m.actions.addAll(recorded); repo.upsert(m); Toast.makeText(this, "Macro disimpan: ${m.name}", Toast.LENGTH_SHORT).show(); closePanel(); togglePanel()
            }.setNegativeButton("BUANG", null).create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun closePanel() { panel?.let { runCatching { wm.removeView(it) } }; panel = null }
    override fun onDestroy() { closePanel(); runCatching { wm.removeView(handle) }; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
