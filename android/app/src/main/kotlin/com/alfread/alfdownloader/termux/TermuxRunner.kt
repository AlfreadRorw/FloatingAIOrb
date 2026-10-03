package com.alfread.alfdownloader.termux

import android.content.Context
import android.content.Intent

class TermuxRunner(private val context: Context) {
    companion object {
        private const val PACKAGE = "com.termux"
        private const val SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION = "com.termux.RUN_COMMAND"
        private const val PATH = "com.termux.RUN_COMMAND_PATH"
        private const val ARGS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    }

    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun startServer(): Result<Unit> = runCatching {
        if (!isInstalled()) error("Termux belum terpasang")
        val intent = Intent().apply {
            setClassName(PACKAGE, SERVICE)
            action = ACTION
            putExtra(PATH, "$PREFIX/bin/bash")
            putExtra(ARGS, arrayOf("-lc", "cd ~/alf-downloader-server && ./start.sh"))
            putExtra(WORKDIR, "~/alf-downloader-server")
            putExtra(BACKGROUND, true)
        }
        context.startService(intent)
    }

    private val PREFIX = "/data/data/com.termux/files/usr"
}
