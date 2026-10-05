package com.alfread.alfvoicecontrol.screen

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.os.PowerManager
import com.alfread.alfvoicecontrol.admin.AlfDeviceAdminReceiver

class ScreenWakeManager(private val context: Context) {
    fun wakeScreen(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isInteractive) return true
        @Suppress("DEPRECATION")
        val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP
        val wakeLock = powerManager.newWakeLock(flags, "ALFVoiceControl:ScreenWake")
        return runCatching {
            wakeLock.acquire(2_500L)
            SystemClock.sleep(120L)
            powerManager.isInteractive
        }.getOrElse { false }.also {
            runCatching { if (wakeLock.isHeld) wakeLock.release() }
        }
    }

    fun lockScreen(): Boolean {
        val admin = ComponentName(context, AlfDeviceAdminReceiver::class.java)
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!devicePolicyManager.isAdminActive(admin)) return false
        return runCatching {
            devicePolicyManager.lockNow()
            true
        }.getOrDefault(false)
    }

    fun isDeviceAdminEnabled(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(context, AlfDeviceAdminReceiver::class.java))
    }
}
