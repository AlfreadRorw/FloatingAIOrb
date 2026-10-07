package com.alfread.alfvision.core

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CaptureEventBus {
    private val _commands = MutableSharedFlow<CaptureCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<CaptureCommand> = _commands.asSharedFlow()

    fun request(region: RegionRect?) {
        _commands.tryEmit(CaptureCommand.CAPTURE(region))
    }

    fun stop() {
        _commands.tryEmit(CaptureCommand.STOP)
    }
}

sealed interface CaptureCommand {
    data class CAPTURE(val region: RegionRect?) : CaptureCommand
    data object STOP : CaptureCommand
}

data class RegionRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotation: Int = 0,
    val displayId: Int = 0
)

data class CapturedFrame(
    val bitmap: Bitmap,
    val fullWidth: Int,
    val fullHeight: Int,
    val region: RegionRect?
)

object VisionEventBus {
    private val _frames = MutableSharedFlow<CapturedFrame>(extraBufferCapacity = 2)
    val frames: SharedFlow<CapturedFrame> = _frames.asSharedFlow()

    fun publish(frame: CapturedFrame) {
        _frames.tryEmit(frame)
    }
}

object FloatingCommandBus {
    const val SHOW = "show"
    const val SHOW_REGION_SELECTOR = "show_region_selector"
    const val HIDE_REGION_SELECTOR = "hide_region_selector"
    const val MINIMIZE = "minimize"
    const val MAXIMIZE = "maximize"
}
