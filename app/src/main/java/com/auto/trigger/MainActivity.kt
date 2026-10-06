package com.auto.trigger
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
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
    private var editing: String? = null; private var editPage = 0
    private var pickerOpen: String? = null
    private var pulse: ObjectAnimator? = null
    private val permL = Shizuku.OnRequestPermissionResultListener { _, _ -> Shell.bind(this); runOnUiThread { render() } }
    private val binderL = Shizuku.OnBinderReceivedListener { Shell.bind(this); runOnUiThread { render() } }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s); S.init(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        Shizuku.addRequestPermissionResultListener(permL); Shizuku.addBinderReceivedListenerSticky(binderL); render()
    }
    override fun onResume() { super.onResume(); Shell.bind(this); render() }
    override fun onDestroy() { pulse?.cancel(); Shizuku.removeRequestPermissionResultListener(permL); Shizuku.removeBinderReceivedListener(binderL); super.onDestroy() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (editing != null) { editing = null; svc()?.reload(); render() } else super.onBackPressed() }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun svc() = FloatingService.inst
    private fun live() { svc()?.rebuildAll(); render() }
    private fun commit(ms: List<Macro>) { Store.save(this, ms); svc()?.reload(); render() }
    private fun speedSet(v: Int) { S.speed = v.coerceIn(10, 500); svc()?.speedChanged(); render() }

    private fun render() {
        pulse?.cancel(); pulse = null
        val y = sv?.scrollY ?: 0
        window.statusBarColor = T.bg; window.navigationBarColor = T.bg
        window.decorView.systemUiVisibility = if (S.theme == 1) 0x2010 else 0
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(T.bg) }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }; sv = scroll
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(44), dp(20), dp(24)) }
        when (tab) { 0 -> home(body); 1 -> if (editing != null) editor(body) else macrosPage(body); 2 -> game(body); else -> settings(body) }
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
            if (sel) background = rb(T.accent, 24)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) }
            addView(ImageView(this@MainActivity).apply { setImageResource(res); setColorFilter(if (sel) T.on(T.accent) else T.sub); layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)) })
            addView(label(name, 10f, true, if (sel) T.on(T.accent) else T.sub))
            setOnClickListener { hap(this); if (tab != i) { tab = i; editing = null; sv = null; render() } }
        }
    }

    private fun title(b: LinearLayout, small: String, big: String) {
        b.addView(label(small, 12f, true, T.accent).apply { letterSpacing = 0.2f })
        b.addView(label(big, 28f, true).apply { setPadding(0, dp(2), 0, dp(14)) })
    }
    private fun sec(b: LinearLayout, t: String) = b.addView(label(t, 11f, true, T.sub).apply { letterSpacing = 0.15f; setPadding(dp(4), dp(10), 0, dp(6)) })
    private fun note(t: String) = label(t, 11f, false, T.sub).apply { setPadding(0, 0, 0, dp(8)) }

    // ---------------- KECEPATAN (dipakai di Beranda & Game) ----------------
    private fun speedCard(b: LinearLayout) {
        val c = card()
        c.addView(LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL
            addView(label("Kecepatan langsung", 14f, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            addView(label(spdTxt(S.speed), 22f, true, if (S.turboOn) T.orange else T.accent)) })
        c.addView(note("0.1x sampai 5x. Bisa diubah saat macro sedang jalan, langsung terasa. Di panel & dock juga ada tombolnya."))
        val r = LinearLayout(this); SPEEDS.forEach { v -> r.addView(chip(spdTxt(v), S.speed == v) { speedSet(v) }) }
        c.addView(hscroll(r))
        c.addView(stepperF("Atur halus", S.speed, 10, 500, 5, { spdTxt(it) }, { S.speed = it }) { svc()?.speedChanged(); render() })
        c.addView(stepperF("Pengali turbo", S.turbo, 110, 500, 10, { spdTxt(it) }, { S.turbo = it }) { svc()?.speedChanged(); render() })
        c.addView(toggle("Turbo (susul ketinggalan)", S.turboOn, { S.turboOn = it == 1 }) { svc()?.speedChanged(); render() })
        b.addView(c)
    }

    // ---------------- BERANDA ----------------
    private fun statusRow(name: String, sub: String, ok: Boolean, action: String?, onClick: () -> Unit): View = card().apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(View(this@MainActivity).apply { background = oval(if (ok) T.green else T.hot); layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(12) } })
        addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(label(name, 14f, true)); addView(label(sub, 11f, false, T.sub)) })
        if (action != null && !ok) addView(chip(action, true, onClick = onClick))
    }
    private fun stat(v: String, n: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = rb(T.card, 18, true); setPadding(dp(6), dp(12), dp(6), dp(12))
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), dp(10)) }
        addView(label(v, 18f, true, T.accent)); addView(label(n, 10f, false, T.sub)) }

    private fun home(b: LinearLayout) {
        if (Shell.ready()) Touch.init()
        val on = svc() != null; val ms = Store.load(this)
        title(b, "AUTO TRIGGER", if (on) "Smart Panel aktif" else "Siap digunakan")
        val hero = ImageView(this).apply {
            setImageResource(R.drawable.ic_bolt); setColorFilter(android.graphics.Color.WHITE); val p = dp(42); setPadding(p, p, p, p)
            background = if (on) grad(T.green, 0xFF0A8F3C.toInt(), 72).apply { shape = android.graphics.drawable.GradientDrawable.OVAL } else grad(T.accent, 0xFF000000.toInt() or (T.accent and 0x7F7F7F), 72).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(dp(144), dp(144)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10) }
            setOnClickListener { hap(this); toggleService() } }
        b.addView(hero)
        if (on) pulse = ObjectAnimator.ofPropertyValuesHolder(hero, PropertyValuesHolder.ofFloat("scaleX", 1f, 1.06f), PropertyValuesHolder.ofFloat("scaleY", 1f, 1.06f)).apply {
            duration = 900; repeatCount = android.animation.ValueAnimator.INFINITE; repeatMode = android.animation.ValueAnimator.REVERSE; start() }
        b.addView(label(if (on) "Ketuk untuk mematikan" else "Ketuk untuk mengaktifkan", 12f, false, T.sub).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(14)); layoutParams = LinearLayout.LayoutParams(-1, -2) })
        if (on) { val r = LinearLayout(this).apply { gravity = Gravity.CENTER }
            r.addView(chip("Buka panel", true) { svc()?.showPanel() }); r.addView(chip("Tes sentuhan", false) { svc()?.selfTest() }); r.addView(chip("Stop semua", false, T.hot) { svc()?.stopAll() })
            b.addView(r) }
        val st = LinearLayout(this).apply { setPadding(0, dp(14), 0, 0) }
        st.addView(stat("${ms.size}", "macro")); st.addView(stat("${ms.sumOf { it.acts.size }}", "aksi")); st.addView(stat(spdTxt(S.speed), "kecepatan"))
        b.addView(st)
        speedCard(b)
        sec(b, "STATUS")
        val sub = if (!Shell.available()) "Tidak berjalan. Buka app Shizuku lalu Start" else if (!Shell.granted()) "Belum diizinkan" else if (Shell.ready()) "Terhubung" else "Menghubungkan..."
        b.addView(statusRow("Shizuku", sub, Shell.granted() && Shell.ready(), "Izinkan") {
            try { if (Shizuku.pingBinder()) Shizuku.requestPermission(0) else toast("Start Shizuku dulu") } catch (e: Throwable) { toast("Shizuku belum terpasang") } })
        b.addView(statusRow("Tampil di atas app", if (Settings.canDrawOverlays(this)) "Diizinkan" else "Diperlukan untuk panel & ikon", Settings.canDrawOverlays(this), "Izinkan") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })
        b.addView(statusRow("Sentuhan langsung", if (Touch.ok && Touch.canRead) "Aktif penuh: rekam sambil main + multi-touch" else if (!Shell.ready()) "Shizuku belum terhubung" else "Mode cadangan. Baca: ${if (Touch.canRead) "ya" else "tidak"}, Tulis: ${if (Touch.ok) "ya" else "tidak"}. ${Touch.diag}", Touch.ok && Touch.canRead, null) {})
        val ign = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        b.addView(statusRow("Anti dimatikan sistem", if (ign) "Hemat baterai dimatikan untuk app ini" else "Matikan hemat baterai agar panel awet", ign, "Atur") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
        sec(b, "CARA PAKAI")
        b.addView(card().apply {
            addView(label("1. Aktifkan, lalu tap garis di tepi layar.\n2. Tulis nama trigger, tekan rekam merah.\n3. Main seperti biasa. Layar tetap bisa disentuh.\n4. Tekan centang. Ikon muncul di layar (ikon & warna bisa diganti).\n5. Tap ikon: jalan. Tap lagi: jeda. Tap lagi: lanjut. Tahan: stop.\n6. Terlalu lambat? Naikkan kecepatan atau nyalakan Turbo kapan saja, bahkan saat macro jalan.", 12f, false, T.sub)) })
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
        tools.addView(chip("Hapus semua", false, T.hot) { AlertDialog.Builder(this).setMessage("Hapus semua macro?").setPositiveButton("Hapus") { _, _ -> commit(emptyList()) }.setNegativeButton("Batal", null).show() })
        b.addView(hscroll(tools))
        if (ms.isEmpty()) b.addView(label("\nBelum ada macro.\nAktifkan Smart Panel lalu rekam dari panel.", 13f, false, T.sub))
        ms.forEach { m ->
            val c = card()
            val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            head.addView(RoundIcon(this, 52).also { it.set(m, 0, 0f, ""); it.setOnClickListener { hap(it); pickerOpen = if (pickerOpen == m.name) null else m.name; render() } })
            val inf = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            inf.addView(label(m.name, 17f, true).apply { setOnClickListener { rename(m, ms) } })
            val eff = (S.speed * m.speed / 100).coerceAtLeast(1)
            inf.addView(label("${m.acts.size} aksi | ${if (m.land) "landscape" else "portrait"} | ${secTxt(m.loopMs() * 100 / eff)}/putaran", 11f, false, T.sub))
            inf.addView(label(if (m.pinned) "ikon di layar | ketuk nama: ganti nama | ketuk ikon: ubah ikon" else "ikon disembunyikan", 10f, false, T.sub))
            head.addView(inf); c.addView(head)
            if (pickerOpen == m.name) c.addView(pickerView(m) { Store.save(this, ms); svc()?.softReload() }.apply { setPadding(0, dp(10), 0, 0) })
            val r = LinearLayout(this).apply { setPadding(0, dp(10), 0, dp(4)) }
            r.addView(icon(R.drawable.ic_play, T.on(T.accent), 40, T.accent) { svc()?.runByName(m.name) ?: toast("Aktifkan Smart Panel dulu") })
            r.addView(icon(R.drawable.ic_pin, if (m.pinned) T.hot else T.fg, 40, T.bg) { m.pinned = !m.pinned; commit(ms) })
            r.addView(icon(R.drawable.ic_edit, T.fg, 40, T.bg) { editing = m.name; editPage = 0; sv = null; render() })
            r.addView(icon(R.drawable.ic_copy, T.fg, 40, T.bg) { var n = m.name + "2"; while (ms.any { it.name == n }) n += "'"; ms.add(m.copy(name = n, acts = m.acts.toMutableList(), pinned = false, ix = -1, iy = -1)); commit(ms) })
            r.addView(icon(R.drawable.ic_link, T.fg, 40, T.bg) { Store.exportOne(this, m); toast("Macro ini disalin ke clipboard") })
            r.addView(icon(R.drawable.ic_delete, T.fg, 40, T.bg) { ms.remove(m); ms.forEach { if (it.chain == m.name) it.chain = "" }; commit(ms) })
            c.addView(hscroll(r))
            c.addView(stepper("Ulangi (0 = tanpa henti)", m.loops, 0, 999, 1, "x", { m.loops = it }) { commit(ms) })
            c.addView(stepper("Jeda antar putaran", m.gap.toInt(), 0, 10000, 100, "ms", { m.gap = it.toLong() }) { commit(ms) })
            c.addView(stepperF("Kecepatan macro ini", m.speed, 10, 500, 5, { spdTxt(it) }, { m.speed = it }) { commit(ms) })
            c.addView(LinearLayout(this).apply { setPadding(0, dp(4), 0, 0)
                addView(chip(if (m.chain.isEmpty()) "Lanjut ke: tidak ada" else "Lanjut ke: ${m.chain}", m.chain.isNotEmpty()) { pickChain(m, ms) }) })
            b.addView(c)
        }
    }
    private fun pickChain(m: Macro, ms: List<Macro>) {
        val others = ms.filter { it.name != m.name }.map { it.name }
        val items = (listOf("(tidak ada)") + others).toTypedArray()
        AlertDialog.Builder(this).setTitle("Jalankan setelah selesai").setItems(items) { _, i -> m.chain = if (i == 0) "" else others[i - 1]; commit(ms) }.show()
    }
    private fun rename(m: Macro, ms: List<Macro>) {
        val et = EditText(this).apply { setText(m.name); setSingleLine() }
        AlertDialog.Builder(this).setTitle("Ganti nama").setView(et).setPositiveButton("Simpan") { _, _ ->
            val n = et.text.toString().trim(); if (n.isNotEmpty() && ms.none { it !== m && it.name == n }) { ms.forEach { if (it.chain == m.name) it.chain = n }; if (pickerOpen == m.name) pickerOpen = n; m.name = n; commit(ms) } else toast("Nama kosong/sudah dipakai") }
            .setNegativeButton("Batal", null).show()
    }

    // ---------------- EDITOR AKSI ----------------
    private fun editor(b: LinearLayout) {
        val ms = Store.load(this); val m = ms.find { it.name == editing }
        if (m == null) { editing = null; render(); return }
        fun save() { Store.save(this, ms); render() }
        val back = LinearLayout(this); back.addView(chip("‹ Selesai", true) { editing = null; svc()?.reload(); render() }); b.addView(back)
        title(b, "EDITOR AKSI", m.name)
        val c = card()
        c.addView(label("${m.acts.size} aksi | ${secTxt(m.loopMs())} per putaran (kecepatan 1x)", 12f, false, T.sub))
        val tr = LinearLayout(this).apply { setPadding(0, dp(8), 0, 0) }
        tr.addView(chip("Jeda ×0.5", false) { m.acts.replaceAll { it.copy(delay = it.delay / 2) }; save() })
        tr.addView(chip("Jeda ×2", false) { m.acts.replaceAll { it.copy(delay = it.delay * 2) }; save() })
        tr.addView(chip("Batasi jeda 1 dtk", false) { m.acts.replaceAll { it.copy(delay = minOf(it.delay, 1000)) }; save() })
        tr.addView(chip("Balik urutan", false) { m.acts.reverse(); save() })
        c.addView(hscroll(tr)); b.addView(c)
        val per = 40; val pages = (m.acts.size + per - 1) / per; editPage = editPage.coerceIn(0, maxOf(0, pages - 1))
        if (pages > 1) { val pr = LinearLayout(this).apply { gravity = Gravity.CENTER }
            pr.addView(chip("‹", false) { editPage--; render() }); pr.addView(label("  ${editPage + 1} / $pages  ", 12f, true)); pr.addView(chip("›", false) { editPage++; render() }); b.addView(pr) }
        for (i in editPage * per until minOf(m.acts.size, (editPage + 1) * per)) {
            val a = m.acts[i]; val moved = Math.abs(a.x1 - a.x2) > 8 || Math.abs(a.y1 - a.y2) > 8
            val k = card(10)
            k.addView(label("#${i + 1}  ${if (moved) "Geser (${a.x1},${a.y1}) → (${a.x2},${a.y2})" else if (a.dur >= 200) "Tahan (${a.x1},${a.y1})" else "Tap (${a.x1},${a.y1})"}", 12f, true))
            k.addView(stepperF("Jeda sebelum", a.delay.toInt(), 0, 60000, 50, { "$it ms" }, { m.acts[i] = a.copy(delay = it.toLong()) }) { save() })
            k.addView(stepperF("Durasi sentuh", a.dur.toInt(), 10, 10000, 10, { "$it ms" }, { m.acts[i] = a.copy(dur = it.toLong()) }) { save() })
            val r = LinearLayout(this)
            r.addView(icon(R.drawable.ic_up, T.fg, 36, T.bg) { if (i > 0) { val t = m.acts[i]; m.acts[i] = m.acts[i - 1]; m.acts[i - 1] = t; save() } })
            r.addView(icon(R.drawable.ic_down, T.fg, 36, T.bg) { if (i < m.acts.size - 1) { val t = m.acts[i]; m.acts[i] = m.acts[i + 1]; m.acts[i + 1] = t; save() } })
            r.addView(icon(R.drawable.ic_copy, T.fg, 36, T.bg) { m.acts.add(i + 1, a.copy()); save() })
            r.addView(icon(R.drawable.ic_delete, T.fg, 36, T.bg) { if (m.acts.size > 1) { m.acts.removeAt(i); save() } else toast("Minimal 1 aksi") })
            k.addView(r); b.addView(k)
        }
    }

    // ---------------- GAME ----------------
    private fun game(b: LinearLayout) {
        title(b, "KHUSUS GAME", "Mode Game")
        val c1 = card(); c1.addView(label("Preset tampilan", 14f, true))
        c1.addView(note("Normal: biasa. Game: tipis & transparan, dock mati. Siluman: nyaris tak terlihat dan memudar sendiri saat tidak disentuh."))
        val r = LinearLayout(this)
        r.addView(chip("Normal", false) { S.hAlpha = 80; S.hW = 6; S.hLen = 90; S.iconAlpha = 100; S.iconSize = 48; S.haptic = 1; S.alpha = 95; S.dock = 1; S.autoHide = 0; live() })
        r.addView(chip("Mode Game", false) { S.hAlpha = 30; S.hW = 4; S.hLen = 70; S.iconAlpha = 60; S.iconSize = 42; S.haptic = 0; S.alpha = 85; S.dock = 0; S.autoHide = 3; live() })
        r.addView(chip("Siluman", false) { S.hAlpha = 20; S.hW = 3; S.hLen = 60; S.iconAlpha = 40; S.iconSize = 36; S.haptic = 0; S.alpha = 80; S.dock = 0; S.autoHide = 2; S.showName = 0; live() })
        c1.addView(hscroll(r)); b.addView(c1)
        sec(b, "KECEPATAN")
        speedCard(b)
        sec(b, "AUTO CLICK (MULTI TARGET)")
        val c2 = card()
        c2.addView(note("Sampai 5 target bulat, masing-masing jalan sendiri dan bersamaan (multi-touch). Geser ke tombol game, tap untuk mulai/berhenti, tahan untuk hapus."))
        c2.addView(stepper("Kecepatan klik awal", S.cps, 1, 60, 1, " /dtk", { S.cps = it }) { svc()?.setAllCps(S.cps); render() })
        val mr = LinearLayout(this)
        listOf("Tap cepat", "Tahan terus", "Burst").forEachIndexed { i, n -> mr.addView(chip(n, S.tMode == i) { S.tMode = i; render() }) }
        c2.addView(label("Mode klik", 13f).apply { setPadding(0, dp(4), 0, dp(4)) }); c2.addView(hscroll(mr))
        if (S.tMode == 2) { c2.addView(stepper("Tap per burst", S.burstN, 1, 50, 1, "x", { S.burstN = it }) { render() }); c2.addView(stepper("Jeda burst", S.burstGap, 50, 3000, 50, "ms", { S.burstGap = it }) { render() }) }
        val tb = LinearLayout(this).apply { setPadding(0, dp(6), 0, 0) }
        tb.addView(chip("+ Target", true) { svc()?.addTargetPublic() ?: toast("Aktifkan Smart Panel dulu") })
        tb.addView(chip("Mulai semua", false) { svc()?.startAllTargets() ?: toast("Aktifkan Smart Panel dulu") })
        tb.addView(chip("Hapus semua", false) { svc()?.clearTargets() })
        c2.addView(hscroll(tb)); b.addView(c2)
        sec(b, "EKSEKUSI MACRO")
        val c3 = card()
        c3.addView(stepper("Variasi posisi acak", S.jitter, 0, 25, 1, "px", { S.jitter = it }) { render() })
        c3.addView(stepper("Variasi waktu acak", S.jitterT, 0, 40, 5, "%", { S.jitterT = it }) { render() })
        c3.addView(stepper("Hitung mundur mulai", S.countdown, 0, 10, 1, "s", { S.countdown = it }) { render() })
        c3.addView(stepper("Berhenti otomatis", S.autoStop, 0, 240, 5, "mnt", { S.autoStop = it }) { render() })
        c3.addView(toggle("Skala durasi geseran ikut kecepatan", S.scaleDur == 1, { S.scaleDur = it }) { render() })
        b.addView(c3)
        b.addView(card().apply { addView(label("Tips game:\n- Rekam & jalankan di orientasi yang sama (landscape/portrait).\n- Macro lebih lambat dari game? Naikkan kecepatan. Terlalu cepat? Turunkan sampai 0.1x.\n- Ketinggalan di tengah jalan? Tekan Turbo di dock/panel/notifikasi, matikan lagi kalau sudah pas.\n- Kunci posisi (ikon gembok di panel) supaya tidak tergeser saat tempur.\n- Satu jari untuk rekam; auto click mendukung banyak jari sekaligus.", 11f, false, T.sub)) })
    }

    // ---------------- SETELAN ----------------
    private fun settings(b: LinearLayout) {
        title(b, "PERSONALISASI", "Setelan")
        val t = card(); t.addView(label("Tema", 14f, true).apply { setPadding(0, 0, 0, dp(8)) })
        val tr = LinearLayout(this)
        listOf("Hitam", "Putih", "AMOLED").forEachIndexed { i, n -> tr.addView(chip(n, S.theme == i) { S.theme = i; live() }) }
        t.addView(tr)
        t.addView(label("Warna aksen", 13f).apply { setPadding(0, dp(12), 0, dp(6)) })
        val ar = LinearLayout(this)
        T.accents.forEachIndexed { i, col -> ar.addView(View(this).apply {
            background = oval(col).apply { if (S.accent == i) setStroke(dp(3), T.fg) }
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { setMargins(dp(4), 0, dp(4), 0) }
            setOnClickListener { hap(this); S.accent = i; live() } }) }
        t.addView(hscroll(ar))
        t.addView(label("Bentuk ikon macro", 13f).apply { setPadding(0, dp(12), 0, dp(6)) })
        val sr = LinearLayout(this); sr.addView(chip("Bulat", S.shape == 0) { S.shape = 0; live() }); sr.addView(chip("Kotak membulat", S.shape == 1) { S.shape = 1; live() })
        t.addView(sr); b.addView(t)
        sec(b, "GARIS PINTAR")
        val g = card()
        g.addView(stepper("Panjang garis", S.hLen, 40, 240, 10, "dp", { S.hLen = it }) { live() })
        g.addView(stepper("Tebal garis", S.hW, 3, 14, 1, "dp", { S.hW = it }) { live() })
        g.addView(stepper("Transparansi garis", S.hAlpha, 20, 100, 10, "%", { S.hAlpha = it }) { live() })
        g.addView(label("Geser garis di layar untuk memindah posisi (naik/turun/kiri/kanan).", 11f, false, T.sub)); b.addView(g)
        sec(b, "PANEL, DOCK & IKON")
        val p = card()
        p.addView(stepper("Opacity panel", S.alpha, 40, 100, 5, "%", { S.alpha = it }) { live() })
        p.addView(stepper("Lebar panel", S.panelW, 260, 420, 20, "dp", { S.panelW = it }) { live() })
        p.addView(stepper("Ukuran ikon", S.iconSize, 36, 72, 6, "dp", { S.iconSize = it }) { live() })
        p.addView(stepper("Opacity ikon", S.iconAlpha, 30, 100, 10, "%", { S.iconAlpha = it }) { live() })
        p.addView(toggle("Nama di bawah ikon", S.showName == 1, { S.showName = it }) { live() })
        p.addView(toggle("Dock bar melayang", S.dock == 1, { S.dock = it }) { live() })
        p.addView(toggle("Getar haptic", S.haptic == 1, { S.haptic = it }) { live() })
        p.addView(toggle("Kunci posisi (anti geser)", S.lockPos == 1, { S.lockPos = it }) { live() })
        p.addView(stepper("Pudar otomatis (0 = mati)", S.autoHide, 0, 30, 1, "s", { S.autoHide = it }) { live() })
        b.addView(p)
        sec(b, "TARGET AUTO CLICK")
        val tg = card()
        tg.addView(stepper("Ukuran target", S.tSize, 40, 96, 6, "dp", { S.tSize = it }) { live() })
        tg.addView(stepper("Opacity target", S.tAlpha, 30, 100, 10, "%", { S.tAlpha = it }) { live() })
        b.addView(tg)
        sec(b, "DATA")
        val d = LinearLayout(this)
        d.addView(chip("Reset posisi", false) { S.resetPos(); live(); toast("Posisi direset") })
        d.addView(chip("Reset setelan", false, T.hot) { AlertDialog.Builder(this).setMessage("Kembalikan semua setelan ke awal? Macro tetap aman.").setPositiveButton("Reset") { _, _ -> S.resetAll(); live() }.setNegativeButton("Batal", null).show() })
        b.addView(hscroll(d))
        b.addView(label("Auto Trigger v2.0", 11f, false, T.sub).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) })
    }
}
