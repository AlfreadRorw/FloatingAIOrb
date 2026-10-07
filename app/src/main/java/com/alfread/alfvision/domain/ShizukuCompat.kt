package com.alfread.alfvision.domain

import android.content.Context

class ShizukuCompat(private val context: Context) {
    fun isInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    }.getOrDefault(false)

    fun statusText(): String = if (isInstalled()) "Installed / optional package detected" else "Not installed"

    fun enhancedModeExplanation(): String =
        "Enhanced mode is optional and stays disabled unless a future compatible enhancement can be verified on this device. Standard MediaProjection mode remains the supported path."
}
