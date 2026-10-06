package com.auto.trigger
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private var tab = 0
    private var sv: ScrollView? = null
    private val permL = Shizuku.OnRequestPermissionResultListener { _, _ -> Shell.bind(this); runOnUiThread { render() } }
    private val binderL = Shizuku.OnBinderReceivedListener { Shell.bind(this); runOnUiThread { render() } }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s); S.init(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        Shizuku.addRequestPermissionResultListener(permL); Shizuku.addBinderReceivedListenerSticky(binderL); render()
    }
    override fun onResume() { super.onResume(); Shell.bind(this); render() }
    override fun onDestroy() { Shizuku.removeRequestPermissionResultListener(permL); Shizuku.removeBinderReceivedListener(binderL); super.onDestroy() }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun svc() = FloatingService.inst
    private fun live() { svc()?.rebuildAll(); render() }
    private fun commit(ms: List<Macro>) { Store.save(this, ms); svc()?.reload(); render() }

    private fun render() {
        val y = sv?.scrollY ?: 0
        window.statusBarColor = T.bg; window.navigationBarColor = T.bg
        window.decorView.systemUiVisibility = if (S.theme == 1) 0x2010 else 0
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(T.bg) }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }; sv = scroll
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(44), dp(20), dp(24)) }
        when (tab) { 0 -> home(body); 1 -> macrosPage(body); 2 -> game(body); else -> settings(body) }
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { background = rb(T.card, 30, true); setPadding(dp(6), dp(6), dp(6), dp(6)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(4), dp(16), dp(14)) } }
        nav.addView(navItem(0, R.drawable.ic_home, "Beranda")); nav.addView(navItem(1, R.drawable.ic_list, "Macro"))
        nav.addView(navItem(2, R.drawable.ic_gamepad, "Game")); nav.addView(navItem(3, R.drawable.ic_settings, "Setelan"))
        root.addView(nav); setContentView(root); scroll.post { scroll.scrollTo(0, y) }
    }

    private fun navItem(i: Int, res: Int, name: String): View {
        val sel = tab == i
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8))
            if (sel) background = rb(T.fg, 24)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) }
            addView(ImageView(this@MainActivity).apply { setImageResource(res); setColorFilter(if (sel) T.bg else T.sub); layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)) })
            addView(label(name, 10f, true, if (sel) T.bg else T.sub))
            setOnClickListener { hap(this); tab = i; sv = null; render() }
        }
    }

    private fun title(b: LinearLayout, small: String, big: String) {
        b.addView(label(small, 12f, true, T.sub).apply { letterSpacing = 0.2f })
        b.addView(label(big, 28f, true).apply { setPadding(0, dp(2), 0, dp(14)) })
    }
    private fun sec(b: LinearLayout, t: String) = b.addView(label(t, 11f, true, T.sub).apply { letterSpacing = 0.15f; setPadding(dp(4), dp(10), 0, dp(6)) })

    // ---------------- BERANDA ----------------
    private fun statusRow(name: String, sub: String, ok: Boolean, action: String?, onClick: () -> Unit): View = card().apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(View(this@MainActivity).apply { background = oval(if (ok) T.green else T.hot); layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(12) } })
        addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(label(name, 14f, true)); addView(label(sub, 11f, false, T.sub)) })
        if (action != null && !ok) addView(chip(action, true, onClick))
    }

    private fun home(b: LinearLayout) {
        val on = svc() != null
        title(b, "AUTO TRIGGER", if (on) "Smart Panel aktif" else "Siap digunakan")
        val hero = ImageView(this).apply {
            setImageResource(R.drawable.ic_bolt); setColorFilter(if (on) android.graphics.Color.WHITE else T.bg); val p = dp(42); setPadding(p, p, p, p)
            background = oval(if (on) T.green else T.fg); layoutParams = LinearLayout.LayoutParams(dp(144), dp(144)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10) }
            setOnClickListener { hap(this); toggleService() } }
        b.addView(hero)
        b.addView(label(if (on) "Ketuk untuk mematikan" else "Ketuk untuk mengaktifkan", 12f, false, T.sub).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(18)); layoutParams = LinearLayout.LayoutParams(-1, -2) })
        if (on) { val r = LinearLayout(this).apply { gravity = Gravity.CENTER }
            r.addView(chip("Buka panel", true) { svc()?.showPanel() }); r.addView(chip("Tes sentuhan", false) { svc()?.selfTest() }); r.addView(chip("Stop semua", false) { svc()?.stopAll() })
            b.addView(r) }
        sec(b, "STATUS")
        val sub = if (!Shell.available()) "Tidak berjalan. Buka app Shizuku lalu Start" else if (!Shell.granted()) "Belum diizinkan" else if (Shell.ready()) "Terhubung" else "Menghubungkan..."
        b.addView(statusRow("Shizuku", sub, Shell.granted() && Shell.ready(), "Izinkan") {
            try { if (Shizuku.pingBinder()) Shizuku.requestPermission(0) else toast("Start Shizuku dulu") } catch (e: Throwable) { toast("Shizuku belum terpasang") } })
        b.addView(statusRow("Tampil di atas app", if (Settings.canDrawOverlays(this)) "Diizinkan" else "Diperlukan untuk panel & ikon", Settings.canDrawOverlays(this), "Izinkan") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })
        b.addView(statusRow("Sentuhan langsung", if (Touch.ok) "Aktif: presisi tinggi, bisa rekam sambil main" else "Belum aktif (butuh Shizuku). Mode cadangan: input", Touch.ok, null) {})
        val ign = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        b.addView(statusRow("Anti dimatikan sistem", if (ign) "Hemat baterai dimatikan untuk app ini" else "Matikan hemat baterai agar panel awet", ign, "Atur") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
        sec(b, "CARA PAKAI")
        b.addView(card().apply {
            addView(label("1. Aktifkan, lalu tap garis putih di tepi layar.\n2. Tulis nama trigger, tekan rekam merah.\n3. Main/gerakkan layar seperti biasa. Layar tetap bisa disentuh.\n4. Tekan centang. Ikon bulat muncul di layar.\n5. Tap ikon: jalan. Tap lagi: jeda. Tap lagi: lanjut. Tahan: stop.", 12f, false, T.sub)) })
        b.addView(label("${Store.load(this).size} macro tersimpan", 11f, false, T.sub).apply { gravity = Gravity.CENTER })
    }
    private fun toggleService() {
        if (svc() != null) stopService(Intent(this, FloatingService::class.java))
        else { if (!Settings.canDrawOverlays(this)) { toast("Beri izin overlay dulu"); return }; Shell.bind(this); startForegroundService(Intent(this, FloatingService::class.java)) }
        sv?.postDelayed({ render() }, 500)
    }

    // ---------------- MACRO ----------------
    private fun macrosPage(b: LinearLayout) {
        title(b, "KOLEKSI", "Macro")
        val ms = Store.load(this)
        val tools = LinearLayout(this)
        tools.addView(chip("Ekspor", false) { Store.exportTo(this); toast("Disalin ke clipboard") })
        tools.addView(chip("Impor", false) { val n = Store.importFrom(this); toast(if (n < 0) "Clipboard bukan data macro" else "$n macro diimpor"); svc()?.reload(); render() })
        tools.addView(chip("Hapus semua", false) { AlertDialog.Builder(this).setMessage("Hapus semua macro?").setPositiveButton("Hapus") { _, _ -> commit(emptyList()) }.setNegativeButton("Batal", null).show() })
        b.addView(tools)
        if (ms.isEmpty()) b.addView(label("\nBelum ada macro.\nAktifkan Smart Panel lalu rekam dari panel.", 13f, false, T.sub))
        ms.forEach { m ->
            val c = card()
            c.addView(label(m.name, 17f, true).apply { setOnClickListener { rename(m, ms) } })
            c.addView(label("${m.acts.size} aksi  |  ${if (m.land) "landscape" else "portrait"}  |  ${if (m.pinned) "ikon di layar" else "ikon disembunyikan"}  |  ketuk nama untuk ganti", 11f, false, T.sub))
            val r = LinearLayout(this).apply { setPadding(0, dp(10), 0, dp(4)) }
            r.addView(icon(R.drawable.ic_play, T.bg, 42, T.fg) { svc()?.runByName(m.name) ?: toast("Aktifkan Smart Panel dulu") })
            r.addView(icon(R.drawable.ic_pin, if (m.pinned) T.hot else T.fg, 42, T.bg) { m.pinned = !m.pinned; commit(ms) })
            r.addView(icon(R.drawable.ic_copy, T.fg, 42, T.bg) { var n = m.name + "2"; while (ms.any { it.name == n }) n += "'"; ms.add(Macro(n, m.acts.toMutableList(), m.loops, m.gap, false, m.land)); commit(ms) })
            r.addView(icon(R.drawable.ic_delete, T.fg, 42, T.bg) { ms.remove(m); commit(ms) })
            c.addView(r)
            c.addView(stepper("Ulangi (0 = tanpa henti)", m.loops, 0, 999, 1, "x", { m.loops = it }) { commit(ms) })
            c.addView(stepper("Jeda antar putaran", m.gap.toInt(), 0, 10000, 100, "ms", { m.gap = it.toLong() }) { commit(ms) })
            b.addView(c)
        }
    }
    private fun rename(m: Macro, ms: List<Macro>) {
        val et = EditText(this).apply { setText(m.name); setSingleLine() }
        AlertDialog.Builder(this).setTitle("Ganti nama").setView(et).setPositiveButton("Simpan") { _, _ ->
            val n = et.text.toString().trim(); if (n.isNotEmpty() && ms.none { it !== m && it.name == n }) { m.name = n; commit(ms) } else toast("Nama kosong/sudah dipakai") }
            .setNegativeButton("Batal", null).show()
    }

    // ---------------- GAME ----------------
    private fun game(b: LinearLayout) {
        title(b, "KHUSUS GAME", "Mode Game")
        val c1 = card(); c1.addView(label("Preset tampilan", 14f, true))
        c1.addView(label("Game: garis & ikon dibuat tipis/transparan, haptic mati, dock disembunyikan.", 11f, false, T.sub).apply { setPadding(0, 0, 0, dp(8)) })
        val r = LinearLayout(this)
        r.addView(chip("Normal", false) { S.hAlpha = 80; S.hW = 6; S.hLen = 90; S.iconAlpha = 100; S.iconSize = 48; S.haptic = 1; S.alpha = 95; S.dock = 1; live() })
        r.addView(chip("Mode Game", false) { S.hAlpha = 30; S.hW = 4; S.hLen = 70; S.iconAlpha = 60; S.iconSize = 42; S.haptic = 0; S.alpha = 85; S.dock = 0; live() })
        c1.addView(r); b.addView(c1)
        sec(b, "AUTO CLICK CEPAT")
        val c2 = card()
        c2.addView(label("Target bulat: geser ke titik tombol di game, tap untuk mulai/berhenti. Cocok untuk tap-tap cepat.", 11f, false, T.sub).apply { setPadding(0, 0, 0, dp(8)) })
        c2.addView(stepper("Kecepatan klik", S.cps, 1, 40, 1, " /dtk", { S.cps = it }) { render() })
        c2.addView(chip("Tampilkan / sembunyikan target", true) { svc()?.toggleTargetPublic() ?: toast("Aktifkan Smart Panel dulu") })
        b.addView(c2)
        sec(b, "EKSEKUSI MACRO")
        val c3 = card()
        c3.addView(stepper("Kecepatan macro", S.speed, 50, 300, 25, "%", { S.speed = it }) { render() })
        c3.addView(stepper("Variasi posisi acak", S.jitter, 0, 25, 1, "px", { S.jitter = it }) { render() })
        c3.addView(stepper("Variasi waktu acak", S.jitterT, 0, 40, 5, "%", { S.jitterT = it }) { render() })
        c3.addView(stepper("Hitung mundur mulai", S.countdown, 0, 10, 1, "s", { S.countdown = it }) { render() })
        c3.addView(stepper("Berhenti otomatis", S.autoStop, 0, 240, 5, "mnt", { S.autoStop = it }) { render() })
        b.addView(c3)
        b.addView(card().apply { addView(label("Tips game:\n- Rekam & jalankan di orientasi yang sama (landscape/portrait).\n- Layar tetap menyala saat macro berjalan.\n- Jangan letakkan ikon bulat di atas titik yang di-tap macro.\n- Satu jari saja yang direkam (belum multi-touch).", 11f, false, T.sub)) })
    }

    // ---------------- SETELAN ----------------
    private fun settings(b: LinearLayout) {
        title(b, "PERSONALISASI", "Setelan")
        val t = card(); t.addView(label("Tema", 14f, true).apply { setPadding(0, 0, 0, dp(8)) })
        val tr = LinearLayout(this)
        listOf("Hitam", "Putih", "AMOLED").forEachIndexed { i, n -> tr.addView(chip(n, S.theme == i) { S.theme = i; live() }) }
        t.addView(tr); b.addView(t)
        sec(b, "GARIS PINTAR")
        val g = card()
        g.addView(stepper("Panjang garis", S.hLen, 40, 240, 10, "dp", { S.hLen = it }) { live() })
        g.addView(stepper("Tebal garis", S.hW, 3, 14, 1, "dp", { S.hW = it }) { live() })
        g.addView(stepper("Transparansi garis", S.hAlpha, 20, 100, 10, "%", { S.hAlpha = it }) { live() })
        g.addView(label("Geser garis di layar untuk memindah posisi (naik/turun/kiri/kanan).", 11f, false, T.sub)); b.addView(g)
        sec(b, "PANEL, DOCK & IKON")
        val p = card()
        p.addView(stepper("Opacity panel", S.alpha, 40, 100, 5, "%", { S.alpha = it }) { live() })
        p.addView(stepper("Ukuran ikon bulat", S.iconSize, 36, 72, 6, "dp", { S.iconSize = it }) { live() })
        p.addView(stepper("Opacity ikon bulat", S.iconAlpha, 30, 100, 10, "%", { S.iconAlpha = it }) { live() })
        p.addView(toggle("Dock bar melayang", S.dock == 1, { S.dock = it }) { live() })
        p.addView(toggle("Getar haptic", S.haptic == 1, { S.haptic = it }) { live() }); b.addView(p)
        b.addView(label("Auto Trigger v1.0", 11f, false, T.sub).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
    }
}
