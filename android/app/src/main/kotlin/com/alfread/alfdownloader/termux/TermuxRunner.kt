package com.alfread.alfdownloader.termux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Status bersama (satu proses): bila pengguna menekan "Matikan", pengawas tidak boleh menyalakan lagi. */
object ServerGuard {
    @Volatile var stoppedByUser = false
}

class TermuxRunner(private val context: Context) {
    companion object {
        const val PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val PACKAGE = "com.termux"
        private const val SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION = "com.termux.RUN_COMMAND"
        private const val PATH = "com.termux.RUN_COMMAND_PATH"
        private const val ARGS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        private const val PREFIX = "/data/data/com.termux/files/usr"
        private const val HOME = "/data/data/com.termux/files/home"

        /** Satu baris yang cukup ditempel sekali di Termux supaya ALF boleh mengendalikannya. */
        const val ALLOW_EXTERNAL_CMD =
            "mkdir -p ~/.termux && grep -q '^allow-external-apps' ~/.termux/termux.properties 2>/dev/null " +
                "&& sed -i 's/^allow-external-apps.*/allow-external-apps=true/' ~/.termux/termux.properties " +
                "|| echo 'allow-external-apps=true' >> ~/.termux/termux.properties; termux-reload-settings"
    }

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0); true
    }.getOrDefault(false)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun openTermux(): Boolean = runCatching {
        val i = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i); true
    }.getOrDefault(false)

    /** Salin perintah "allow-external-apps" ke clipboard lalu buka Termux (tinggal tempel + Enter). */
    fun copyAllowExternalAndOpen(): Boolean {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("termux", ALLOW_EXTERNAL_CMD))
        }
        return openTermux()
    }

    private fun shellDir(dir: String): String {
        val d = dir.trim().trimEnd('/')
        val esc = d.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        return if (esc.startsWith("~")) "\"\$HOME" + esc.removePrefix("~") + "\"" else "\"$esc\""
    }

    private fun send(script: String, background: Boolean): Result<Unit> = runCatching {
        if (!isInstalled()) error("Termux belum terpasang")
        val intent = Intent().apply {
            setClassName(PACKAGE, SERVICE)
            action = ACTION
            putExtra(PATH, "$PREFIX/bin/bash")
            putExtra(ARGS, arrayOf("-c", script))
            putExtra(WORKDIR, HOME)
            putExtra(BACKGROUND, background)
            if (!background) putExtra(SESSION_ACTION, "0") // buka sesi baru & tampilkan
        }
        try {
            // RunCommandService adalah foreground service → wajib startForegroundService di Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        } catch (e: SecurityException) {
            error("Izin \"Run commands in Termux\" belum diberikan (Pengaturan → Server Termux → Beri izin).")
        } catch (e: IllegalStateException) {
            error("Android memblokir start Termux dari latar belakang. Buka ALF sekali atau nyalakan bar mengambang.")
        }
        Unit
    }

    /**
     * Nyalakan server. Cepat & aman: start.sh mengecek /api/ping + pengawas dulu (tidak ada dua server).
     * File server di Termux ikut disegarkan dari APK, jadi perbaikan anti-mati langsung berlaku
     * tanpa perlu "Pasang server" ulang.
     */
    fun startServer(serverDir: String): Result<Unit> {
        val primary = shellDir(serverDir.ifBlank { "~/alf-downloader-server" })
        val start = runCatching { asset("start.sh").trimEnd() }.getOrNull()
        val server = runCatching { asset("server.py").trimEnd() }.getOrNull()
        val script = buildString {
            appendLine("for d in $primary \"\$HOME/alf-downloader-server\" \"\$HOME/Github/termux-server\" \"\$HOME/termux-server\"; do")
            appendLine("  if [ -f \"\$d/server.py\" ]; then")
            appendLine("    cd \"\$d\" || continue")
            if (start != null) {
                appendLine("    cat > start.sh.new <<'ALF_START_EOF'")
                appendLine(start)
                appendLine("ALF_START_EOF")
                appendLine("    mv -f start.sh.new start.sh; chmod +x start.sh")
            }
            if (server != null) {
                appendLine("    cat > server.py.new <<'ALF_SERVER_EOF'")
                appendLine(server)
                appendLine("ALF_SERVER_EOF")
                appendLine("    mv -f server.py.new server.py")
            }
            appendLine("    exec bash start.sh")
            appendLine("  fi")
            appendLine("done")
            appendLine("echo \"server.py tidak ditemukan — pakai tombol 'Pasang server otomatis' di Alfread Tools\" >> \"\$HOME/.alf-server.log\"")
        }
        return send(script, background = true)
    }

    private fun asset(name: String): String =
        context.assets.open("server/$name").bufferedReader().use { it.readText() }

    /**
     * Pasang semua otomatis dari dalam APK: paket Termux, library Python, server.py, start.sh,
     * lalu langsung jalankan. Berjalan di sesi Termux yang terlihat supaya progresnya bisa dipantau.
     */
    fun installServer(): Result<Unit> = runCatching {
        val server = asset("server.py")
        val start = asset("start.sh")
        val d = "\$HOME"
        val script = buildString {
            appendLine("set -e")
            appendLine("echo '== ALF: memasang server (sekali saja, sabar ya) =='")
            appendLine("mkdir -p $d/.termux; touch $d/.termux/termux.properties")
            appendLine("grep -q '^allow-external-apps' $d/.termux/termux.properties && sed -i 's/^allow-external-apps.*/allow-external-apps=true/' $d/.termux/termux.properties || echo 'allow-external-apps=true' >> $d/.termux/termux.properties")
            appendLine("termux-reload-settings 2>/dev/null || true")
            appendLine("yes | pkg update -y -o Dpkg::Options::=--force-confnew || true")
            appendLine("pkg install -y python ffmpeg nodejs termux-api curl")
            appendLine("python -m pip install -U pip")
            appendLine("python -m pip install -U 'yt-dlp[default]' flask waitress")
            appendLine("mkdir -p $d/alf-downloader-server")
            appendLine("cat > $d/alf-downloader-server/server.py <<'ALF_SERVER_EOF'")
            appendLine(server.trimEnd())
            appendLine("ALF_SERVER_EOF")
            appendLine("cat > $d/alf-downloader-server/start.sh <<'ALF_START_EOF'")
            appendLine(start.trimEnd())
            appendLine("ALF_START_EOF")
            appendLine("chmod +x $d/alf-downloader-server/start.sh")
            appendLine("mkdir -p '/storage/emulated/0/Download/ALF Downloader' 2>/dev/null || true")
            appendLine("python -m py_compile $d/alf-downloader-server/server.py")
            appendLine("echo '== Selesai! Server dijalankan… =='")
            appendLine("cd $d/alf-downloader-server && (nohup bash start.sh >> $d/.alf-server.log 2>&1 &)")
            appendLine("sleep 2; echo 'Server berjalan di latar belakang — Termux boleh ditutup.'")
        }
        send(script, background = false).getOrThrow()
    }
}
