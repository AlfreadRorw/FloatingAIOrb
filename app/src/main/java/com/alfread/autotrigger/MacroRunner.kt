package com.alfread.autotrigger

import android.os.SystemClock
import com.alfread.autotrigger.model.Action
import com.alfread.autotrigger.model.Macro
import com.alfread.autotrigger.service.AlfAccessibilityService
import com.alfread.autotrigger.shizuku.ShizukuBridge

object MacroRunner {
    fun play(macro: Macro, onDone: (() -> Unit)? = null) {
        val service = AlfAccessibilityService.instance
        if (service != null) { service.play(macro, onDone); return }
        ShizukuBridge.ensureBound { ready ->
            if (!ready) return@ensureBound
            Thread {
                do {
                    for (a in macro.actions) {
                        when (a) {
                            is Action.Delay -> SystemClock.sleep((a.durationMs / macro.speed).toLong().coerceAtLeast(1))
                            is Action.Tap -> ShizukuBridge.run("input tap ${a.x.toInt()} ${a.y.toInt()}")
                            is Action.Swipe -> ShizukuBridge.run("input swipe ${a.x1.toInt()} ${a.y1.toInt()} ${a.x2.toInt()} ${a.y2.toInt()} ${(a.durationMs / macro.speed).toLong().coerceAtLeast(1)}")
                        }
                    }
                } while (macro.loop)
                onDone?.invoke()
            }.start()
        }
    }
}
