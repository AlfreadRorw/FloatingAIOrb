package com.auto.trigger
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val listener = Shizuku.OnRequestPermissionResultListener { _, _ -> Shell.bind(this); refresh() }
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        status = TextView(this).apply { textSize = 15f }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
        l.addView(TextView(this).apply { text = "Auto Trigger"; textSize = 26f })
        l.addView(status)
        l.addView(btn("1. Izin Shizuku") {
            try { if (Shizuku.pingBinder()) Shizuku.requestPermission(0) else toast("Shizuku belum jalan. Buka app Shizuku & start dulu") }
            catch (e: Throwable) { toast("Shizuku belum terpasang/jalan") } })
        l.addView(btn("2. Izin Overlay (tampil di atas app)") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })
        l.addView(btn("3. Aktifkan Smart Panel") {
            Shell.bind(this)
            if (!Settings.canDrawOverlays(this)) toast("Izin overlay dulu") else startForegroundService(Intent(this, FloatingService::class.java)) })
        l.addView(btn("Matikan Smart Panel") { stopService(Intent(this, FloatingService::class.java)) })
        l.addView(TextView(this).apply { textSize = 13f; setPadding(0, 32, 0, 0)
            text = "Cara pakai:\n• Garis putih di pinggir layar = Smart Panel. Tap untuk buka.\n• Tahan & geser garis untuk pindah posisi (naik/turun/kiri/kanan).\n• Di panel: Rekam, ▶ Jalan, 📌 jadikan ikon melayang, 🔁 loop, ⏱ jeda, 🗑 hapus." })
        setContentView(ScrollView(this).also { it.addView(l) })
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.addBinderReceivedListenerSticky { Shell.bind(this); runOnUiThread { refresh() } }
    }
    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroy() { Shizuku.removeRequestPermissionResultListener(listener); super.onDestroy() }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    private fun refresh() {
        status.text = "Shizuku: " + (if (!Shell.available()) "tidak jalan" else if (Shell.granted()) "diizinkan ✔" else "belum diizinkan") +
            "\nOverlay: " + (if (Settings.canDrawOverlays(this)) "✔" else "belum")
    }
}
