package com.alfread.alfvoicecontrol.screen

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.alfread.alfvoicecontrol.security.AlfDeviceAdminReceiver

sealed class ScreenActionResult {
    object Success : ScreenActionResult()
    data class Failed(val reason: String) : ScreenActionResult()
}

/**
 * Real screen wake / screen off actions using only Android's official APIs.
 * - Screen ON: acquires a short wake lock and launches [ScreenWakeActivity],
 *   which carries the window flags that actually turn the display on. If the
 *   device has a PIN/pattern/password, the normal Android lock screen still
 *   appears afterwards - this never bypasses it.
 * - Screen OFF: requires the user to have enabled ALF as a Device
 *   Administrator; uses [DevicePolicyManager.lockNow] which is the official
 *   API for an app to lock the screen. No root, no exploit, no hidden API.
 */
object ScreenController {

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, AlfDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(admin)
    }

    fun requestDeviceAdminIntent(context: Context): Intent {
        val admin = ComponentName(context, AlfDeviceAdminReceiver::class.java)
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "ALF needs this permission only to turn the screen off when you say your " +
                    "\"screen off\" command. It never reads your PIN or unlocks your device."
            )
        }
    }

    fun turnScreenOn(context: Context): ScreenActionResult {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "alf:screen_on"
            )
            wakeLock.acquire(5000L)

            val intent = Intent(context, ScreenWakeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)

            if (wakeLock.isHeld) wakeLock.release()
            ScreenActionResult.Success
        } catch (e: Exception) {
            ScreenActionResult.Failed(e.message ?: "Could not wake the screen")
        }
    }

    fun turnScreenOff(context: Context): ScreenActionResult {
        if (!isDeviceAdminActive(context)) {
            return ScreenActionResult.Failed(
                "Device Administrator permission is not enabled. Enable it in Settings > Device Administrator."
            )
        }
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.lockNow()
            ScreenActionResult.Success
        } catch (e: Exception) {
            ScreenActionResult.Failed(e.message ?: "Could not lock the screen")
        }
    }
}
