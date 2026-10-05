package com.alfread.alfvoicecontrol.commands

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.alfread.alfvoicecontrol.model.ActionType
import com.alfread.alfvoicecontrol.model.VoiceCommand
import com.alfread.alfvoicecontrol.screen.ScreenWakeManager

class CommandExecutor(private val context: Context) {
    enum class Result {
        SUCCESS,
        DISABLED,
        TARGET_NOT_FOUND,
        BLOCKED,
        ADMIN_REQUIRED,
        FAILED
    }

    fun execute(command: VoiceCommand, screenWakeEnabled: Boolean, screenOffEnabled: Boolean): Result {
        return when (command.actionType) {
            ActionType.SCREEN_ON -> if (screenWakeEnabled) {
                if (ScreenWakeManager(context).wakeScreen()) Result.SUCCESS else Result.FAILED
            } else Result.DISABLED

            ActionType.SCREEN_OFF -> if (screenOffEnabled) {
                val manager = ScreenWakeManager(context)
                if (!manager.isDeviceAdminEnabled()) Result.ADMIN_REQUIRED
                else runCatching { if (manager.lockScreen()) Result.SUCCESS else Result.FAILED }.getOrElse { Result.FAILED }
            } else Result.DISABLED

            ActionType.OPEN_APP -> openApp(command.targetPackage)
        }
    }

    private fun openApp(packageName: String?): Result {
        if (packageName.isNullOrBlank()) return Result.TARGET_NOT_FOUND
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return Result.TARGET_NOT_FOUND
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching {
            context.startActivity(launchIntent)
            Result.SUCCESS
        }.getOrElse {
            // Android may block background activity starts. The caller reports the exact limitation instead of faking success.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Result.BLOCKED else Result.FAILED
        }
    }
}
