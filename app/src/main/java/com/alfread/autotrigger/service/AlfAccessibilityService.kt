package com.alfread.autotrigger.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.alfread.autotrigger.model.Action
import com.alfread.autotrigger.model.Macro
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class AlfAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: AlfAccessibilityService? = null
        fun isReady() = instance != null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopPlayback() }
    override fun onDestroy() { instance = null; stopPlayback(); super.onDestroy() }

    fun play(macro: Macro, onFinished: (() -> Unit)? = null) {
        if (!running.compareAndSet(false, true)) return
        playIndex(macro, 0, onFinished)
    }

    fun stopPlayback() {
        running.set(false)
        handler.removeCallbacksAndMessages(null)
    }

    private fun playIndex(m: Macro, index: Int, onFinished: (() -> Unit)?) {
        if (!running.get()) return
        if (index >= m.actions.size) {
            if (m.loop && running.get()) {
                playIndex(m, 0, onFinished)
            } else {
                running.set(false)
                onFinished?.invoke()
            }
            return
        }
        when (val a = m.actions[index]) {
            is Action.Delay -> handler.postDelayed({ playIndex(m, index + 1, onFinished) }, max(1L, (a.durationMs / m.speed).toLong()))
            is Action.Tap -> dispatchTap(a, m.speed) { playIndex(m, index + 1, onFinished) }
            is Action.Swipe -> dispatchSwipe(a, m.speed) { playIndex(m, index + 1, onFinished) }
        }
    }

    private fun dispatchTap(a: Action.Tap, speed: Float, done: () -> Unit) {
        val p = Path().apply { moveTo(a.x, a.y) }
        val duration = max(1L, (a.holdMs / speed).toLong())
        val stroke = GestureDescription.StrokeDescription(p, 0, duration)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = done()
            override fun onCancelled(gestureDescription: GestureDescription?) { running.set(false) }
        }, handler)
    }

    private fun dispatchSwipe(a: Action.Swipe, speed: Float, done: () -> Unit) {
        val p = Path().apply { moveTo(a.x1, a.y1); lineTo(a.x2, a.y2) }
        val duration = max(1L, (a.durationMs / speed).toLong())
        val stroke = GestureDescription.StrokeDescription(p, 0, duration)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = done()
            override fun onCancelled(gestureDescription: GestureDescription?) { running.set(false) }
        }, handler)
    }
}
