package com.alfread.alfvision.util

import android.util.Log

object AlfLogger {
    private const val TAG = "ALFVision"
    @Volatile
    var debugEnabled: Boolean = false

    fun d(message: String) {
        if (debugEnabled) Log.d(TAG, sanitize(message))
    }

    fun i(message: String) {
        if (debugEnabled) Log.i(TAG, sanitize(message))
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (debugEnabled) Log.e(TAG, sanitize(message), throwable)
    }

    private fun sanitize(input: String): String {
        return input
            .replace(Regex("Bearer\\s+[A-Za-z0-9._-]+", RegexOption.IGNORE_CASE), "Bearer [REDACTED]")
            .take(4000)
    }
}
