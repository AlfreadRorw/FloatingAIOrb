package com.alfread.autotrigger.model

data class Macro(
    val id: String,
    var name: String,
    var loop: Boolean = false,
    var speed: Float = 1f,
    var enabled: Boolean = true,
    val actions: MutableList<Action> = mutableListOf()
)

sealed interface Action {
    data class Tap(val x: Float, val y: Float, val holdMs: Long = 20L) : Action
    data class Swipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val durationMs: Long) : Action
    data class Delay(val durationMs: Long) : Action
}
