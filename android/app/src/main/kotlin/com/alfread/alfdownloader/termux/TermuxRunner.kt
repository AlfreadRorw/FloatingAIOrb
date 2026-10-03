package com.alfread.alfdownloader.termux

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

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
        private const val PREFIX = "/data/data/com.termux/files/usr"
        private const val HOME = "/data/data/com.termux/files/home"
    }

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun openTermux(): Boolean = runCatching {
        val i = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        true
    }.getOrDefault(false)

    private fun shellDir(dir: String): String {
        val d = dir.trim().trimEnd('/')
        val esc = d.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        return if (esc.startsWith("~")) "\"\$HOME" + esc.removePrefix("~") + "\"" else "\"$esc\""
    }

    fun startServer(serverDir: String): Result<Unit> = runCatching {
        if (!isInstalled()) error("Termux belum terpasang")
        val primary = shellDir(serverDir.ifBlank { "~/alf-downloader-server" })
        val script = """
            for d in $primary "${'$'}HOME/alf-downloader-server" "${'$'}HOME/Github/termux-server" "${'$'}HOME/termux-server"; do
              if [ -f "${'$'}d/server.py" ]; then cd "${'$'}d" && exec bash start.sh >> "${'$'}HOME/.alf-server.log" 2>&1; fi
            done
            echo "server.py tidak ditemukan" >> "${'$'}HOME/.alf-server.log"
        """.trimIndent()
        val intent = Intent().apply {
            setClassName(PACKAGE, SERVICE)
            action = ACTION
            putExtra(PATH, "$PREFIX/bin/bash")
            putExtra(ARGS, arrayOf("-c", script))
            putExtra(WORKDIR, HOME)
            putExtra(BACKGROUND, true)
        }
        try {
            context.startService(intent)
        } catch (e: SecurityException) {
            error("Izin \"Run commands in Termux\" belum diberikan. Buka Pengaturan Android > Aplikasi > ALF Downloader > Izin.")
        }
        Unit
    }
}
