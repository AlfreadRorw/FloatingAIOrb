package com.alfread.alfvision.core.util

import android.util.Log

class InternalLogger(private val enabled: () -> Boolean) {
    private val tag = "ALFVision"
    fun d(message: String) { if (enabled()) Log.d(tag, message) }
    fun i(message: String) { if (enabled()) Log.i(tag, message) }
    fun w(message: String) { if (enabled()) Log.w(tag, message) }
    fun e(message: String, throwable: Throwable? = null) { if (enabled()) Log.e(tag, message, throwable) }
}
