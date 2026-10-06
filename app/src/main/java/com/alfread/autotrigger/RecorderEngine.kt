package com.alfread.autotrigger

import android.os.SystemClock
import com.alfread.autotrigger.model.Action
import com.alfread.autotrigger.shizuku.IAlfRecordCallback
import com.alfread.autotrigger.shizuku.ShizukuBridge
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

object RecorderEngine {
    @Volatile var recording = false
        private set
    private val actions = CopyOnWriteArrayList<Action>()
    private var displayW = 1
    private var displayH = 1
    private var lastEventMs = 0L
    private var currentX = 0f
    private var currentY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downMs = 0L
    private var active = false

    fun start(width: Int, height: Int, onReady: (Boolean, String) -> Unit) {
        ShizukuBridge.ensureBound {
            if (!it) { onReady(false, "Shizuku UserService belum tersambung"); return@ensureBound }
            val device = ShizukuBridge.findTouchDevice()
            if (device == null) { onReady(false, "Touchscreen input device tidak ditemukan"); return@ensureBound }
            actions.clear(); displayW = width.coerceAtLeast(1); displayH = height.coerceAtLeast(1)
            recording = true; lastEventMs = SystemClock.uptimeMillis(); active = false
            ShizukuBridge.startRecording(device, object : IAlfRecordCallback.Stub() {
                override fun onLine(line: String?) { if (line != null) parse(line) }
                override fun onStopped(reason: String?) { recording = false }
            })
            onReady(true, device)
        }
    }

    fun stop(): MutableList<Action> {
        if (active) finishTouch(SystemClock.uptimeMillis())
        recording = false
        ShizukuBridge.stopRecording()
        return actions.toMutableList()
    }

    fun snapshot(): MutableList<Action> = actions.toMutableList()

    private fun parse(line: String) {
        if (!recording) return
        val type = Regex("EV_ABS ABS_MT_(POSITION_X|POSITION_Y|TRACKING_ID) ([0-9a-fA-F]+)").find(line) ?: return
        val kind = type.groupValues[1]
        val raw = type.groupValues[2].toLongOrNull(16) ?: return
        val now = SystemClock.uptimeMillis()
        if (kind == "TRACKING_ID") {
            if (raw == 0xffffffffL || raw == 0xffffffffffffffffL) {
                if (active) finishTouch(now)
            } else {
                if (!active) {
                    active = true; downMs = now; downX = currentX; downY = currentY
                    addDelay(now)
                }
            }
            return
        }
        if (kind == "POSITION_X") currentX = raw.toFloat().coerceIn(0f, displayW.toFloat())
        if (kind == "POSITION_Y") currentY = raw.toFloat().coerceIn(0f, displayH.toFloat())
    }

    private fun addDelay(now: Long) {
        val delta = now - lastEventMs
        if (delta >= 18) actions.add(Action.Delay(delta.coerceAtMost(3000)))
        lastEventMs = now
    }

    private fun finishTouch(now: Long) {
        val x = currentX; val y = currentY
        val dx = abs(x - downX); val dy = abs(y - downY)
        val duration = (now - downMs).coerceAtLeast(20)
        if (dx < 22 && dy < 22) actions.add(Action.Tap(x, y, duration))
        else actions.add(Action.Swipe(downX, downY, x, y, duration.coerceAtMost(2000)))
        lastEventMs = now; active = false
    }
}
