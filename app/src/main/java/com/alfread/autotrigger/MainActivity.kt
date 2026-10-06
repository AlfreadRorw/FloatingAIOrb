package com.alfread.autotrigger

import android.app.*
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import com.alfread.autotrigger.model.Action
import com.alfread.autotrigger.model.Macro
import com.alfread.autotrigger.repo.MacroRepository
import com.alfread.autotrigger.service.AlfAccessibilityService
import com.alfread.autotrigger.service.AlfOverlayService
import com.alfread.autotrigger.shizuku.ShizukuBridge

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var repo: MacroRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = MacroRepository(this)
        render()
    }

    override fun onResume() { super.onResume(); if (::status.isInitialized) status.text = statusText() }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 36, 40, 30); setBackgroundColor(Color.rgb(7,8,11)) }
        fun tv(text: String, size: Float) = TextView(this).apply { this.text=text; setTextColor(Color.WHITE); textSize=size; setPadding(0, 8, 0, 8) }
        root.addView(tv("ALF AUTO TRIGGER", 24f))
        root.addView(tv("Smart edge panel + macro engine", 14f).apply { setTextColor(Color.LTGRAY) })
        status = tv(statusText(), 13f)
        root.addView(status)

        root.addView(button("1. IZINKAN OVERLAY / PANEL") { openOverlaySettings() })
        root.addView(button("2. AKTIFKAN ACCESSIBILITY") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        root.addView(button("3. SAMBUNG SHIZUKU") { ShizukuBridge.requestPermission() })
        root.addView(button("START EDGE PANEL") { startOverlay() })
        root.addView(button("RECORD MACRO (SHIZUKU)") { startRecordingFlow() })
        root.addView(button("NEW MACRO") { createMacroDialog() })
        root.addView(button("MACRO LIST / EDITOR") { showMacros() })
        root.addView(button("TEST SHIZUKU") {
            ShizukuBridge.bind { ready -> runOnUiThread { toast(if (ready) "Shizuku UserService tersambung" else "Shizuku belum siap") } }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun statusText(): String = buildString {
        append("Overlay: "); append(Settings.canDrawOverlays(this@MainActivity)); append("\n")
        append("Accessibility: "); append(AlfAccessibilityService.isReady()); append("\n")
        append("Shizuku: "); append(ShizukuBridge.isAvailable()); append(" / permission "); append(ShizukuBridge.hasPermission())
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply { text=label; setOnClickListener { action() } }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) { toast("Izinkan overlay dulu"); openOverlaySettings(); return }
        ContextCompatStart.start(this)
    }

    private fun startRecordingFlow() {
        if (!ShizukuBridge.isAvailable() || !ShizukuBridge.hasPermission()) {
            ShizukuBridge.requestPermission()
            toast("Aktifkan Shizuku dan izinkan ALF Auto Trigger")
            return
        }
        val dm = resources.displayMetrics
        RecorderEngine.start(dm.widthPixels, dm.heightPixels) { ok, info ->
            runOnUiThread {
                toast(if (ok) "REC aktif • $info" else "REC gagal: $info")
                if (ok) {
                    startOverlay()
                    try {
                        moveTaskToBack(true)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun createMacroDialog() {
        val input = EditText(this).apply { hint="Nama macro" }
        AlertDialog.Builder(this).setTitle("Macro baru").setView(input)
            .setPositiveButton("BUAT") { _, _ ->
                val m = MacroRepository.newMacro(input.text.toString().ifBlank { "Macro ${repo.all().size+1}" })
                repo.upsert(m); toast("Macro kosong dibuat untuk diedit / diisi rekaman"); render()
            }.setNegativeButton("BATAL", null).show()
    }

    private fun showMacros() {
        val list = repo.all()
        val names = list.map { "${it.name}  •  ${it.actions.size} aksi" }.toTypedArray()
        if (names.isEmpty()) { toast("Belum ada macro"); return }
        AlertDialog.Builder(this).setTitle("Macro").setItems(names) { _, which -> showMacroDetail(list[which]) }
            .setNegativeButton("Tutup", null).show()
    }

    private fun showMacroDetail(m: Macro) {
        val info = TextView(this).apply { setTextColor(Color.WHITE); setPadding(35,20,35,20); text = buildString { append("${m.name}\nloop=${m.loop}, speed=${m.speed}\n\n"); m.actions.forEachIndexed { i,a -> append("${i+1}. $a\n") } } }
        AlertDialog.Builder(this).setTitle("Macro Editor").setView(info)
            .setPositiveButton("PLAY") { _,_ -> MacroRunner.play(m) }
            .setNeutralButton("LOOP") { _,_ -> m.loop=!m.loop; repo.upsert(m); toast("Loop: ${m.loop}") }
            .setNegativeButton("HAPUS") { _,_ -> repo.remove(m.id); toast("Dihapus"); render() }.show()
    }

    object ContextCompatStart {
        fun start(ctx: Context) {
            val i = Intent(ctx, AlfOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
}
