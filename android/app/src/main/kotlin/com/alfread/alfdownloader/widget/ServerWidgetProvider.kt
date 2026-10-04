package com.alfread.alfdownloader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.alfread.alfdownloader.MainActivity
import com.alfread.alfdownloader.R
import com.alfread.alfdownloader.data.SettingsStore
import com.alfread.alfdownloader.termux.TermuxRunner

class ServerWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_START = "com.alfread.alfdownloader.widget.ACTION_START"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_START) {
            val prefs = SettingsStore(context).load()
            val runner = TermuxRunner(context)
            if (!runner.isInstalled()) {
                Toast.makeText(context, "Termux belum terpasang", Toast.LENGTH_SHORT).show()
            } else {
                runner.startServer(prefs.serverDir)
                    .onSuccess { Toast.makeText(context, "Menyalakan server Alfread…", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, it.message ?: "Gagal menyalakan server", Toast.LENGTH_SHORT).show() }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle
    ) {
        updateWidget(context, manager, id)
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val options = manager.getAppWidgetOptions(id)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
        val compact = minWidth < 150

        val openIntent = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val startIntent = PendingIntent.getBroadcast(
            context, id,
            Intent(context, ServerWidgetProvider::class.java).setAction(ACTION_START),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val views = if (compact) {
            RemoteViews(context.packageName, R.layout.widget_small).apply {
                setOnClickPendingIntent(R.id.widget_root, openIntent)
                setOnClickPendingIntent(R.id.widget_start_small, startIntent)
            }
        } else {
            RemoteViews(context.packageName, R.layout.widget_large).apply {
                setOnClickPendingIntent(R.id.widget_root, openIntent)
                setOnClickPendingIntent(R.id.widget_open, openIntent)
                setOnClickPendingIntent(R.id.widget_start, startIntent)
            }
        }
        manager.updateAppWidget(id, views)
    }
}
