package com.alfread.alflauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.ViewGroup

class LauncherWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
    fun createWidgetView(appWidgetId: Int): AppWidgetHostView? {
        val manager = AppWidgetManager.getInstance(context)
        val info = manager.getAppWidgetInfo(appWidgetId) ?: return null
        return runCatching {
            createView(context, appWidgetId, info).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }.getOrNull()
    }

    companion object {
        const val HOST_ID = 0xA1F
    }
}
