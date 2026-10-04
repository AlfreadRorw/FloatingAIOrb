package com.alfread.alfdownloader.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alfread.alfdownloader.data.SettingsStore

/** Setelah HP menyala / ALF diperbarui: bar mengambang menyala otomatis dan memulai server Termux. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val p = SettingsStore(context).load()
        if (!p.startOnBoot || !p.floatingEnabled) return
        if (!OverlayController.canDrawOverlays(context)) return
        runCatching { OverlayController.start(context) }
    }
}
