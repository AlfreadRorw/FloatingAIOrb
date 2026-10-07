package com.alfread.alfvision.core.util

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/** Ukuran layar nyata (px). Dipakai bersama oleh overlay dan MediaProjection supaya koordinatnya sama. */
object ScreenMetrics {
    fun size(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        if (wm == null) {
            val m = context.resources.displayMetrics
            return m.widthPixels to m.heightPixels
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }
}
