package com.alfread.alfvoicecontrol.commands

import android.content.Context
import android.widget.Toast
import com.alfread.alfvoicecontrol.data.ActionType
import com.alfread.alfvoicecontrol.data.InstalledAppsRepository
import com.alfread.alfvoicecontrol.data.VoiceCommand
import com.alfread.alfvoicecontrol.screen.ScreenActionResult
import com.alfread.alfvoicecontrol.screen.ScreenController

sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class Failed(val message: String) : ExecutionResult()
}

/**
 * Executes the real action behind a matched [VoiceCommand]. Every branch
 * calls an actual Android API - nothing here fakes success. If Android
 * denies or cannot perform the action, that is surfaced back as a Failed
 * result rather than pretended away.
 */
object CommandExecutor {

    fun execute(context: Context, command: VoiceCommand): ExecutionResult {
        return when (command.actionType) {
            ActionType.SCREEN_ON -> {
                when (val result = ScreenController.turnScreenOn(context)) {
                    is ScreenActionResult.Success -> ExecutionResult.Success("Screen turned on")
                    is ScreenActionResult.Failed -> ExecutionResult.Failed(result.reason)
                }
            }

            ActionType.SCREEN_OFF -> {
                when (val result = ScreenController.turnScreenOff(context)) {
                    is ScreenActionResult.Success -> ExecutionResult.Success("Screen locked")
                    is ScreenActionResult.Failed -> ExecutionResult.Failed(result.reason)
                }
            }

            ActionType.OPEN_APP -> {
                val pkg = command.targetPackage
                if (pkg.isNullOrBlank()) {
                    ExecutionResult.Failed("No target app configured for this command")
                } else {
                    val intent = InstalledAppsRepository.getLaunchIntent(context, pkg)
                    if (intent == null) {
                        ExecutionResult.Failed("${command.targetAppLabel ?: pkg} is not installed or cannot be opened")
                    } else {
                        runCatching { context.startActivity(intent) }
                            .fold(
                                onSuccess = { ExecutionResult.Success("Opened ${command.targetAppLabel ?: pkg}") },
                                onFailure = { ExecutionResult.Failed(it.message ?: "Could not open app") }
                            )
                    }
                }
            }
        }
    }

    fun showResultToast(context: Context, result: ExecutionResult) {
        val text = when (result) {
            is ExecutionResult.Success -> result.message
            is ExecutionResult.Failed -> "Failed: ${result.message}"
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
