package com.alfread.alfpet

import kotlin.math.roundToInt

data class PetState(
    val hunger: Int = 78,
    val happiness: Int = 84,
    val energy: Int = 72,
    val cleanliness: Int = 82,
    val coins: Int = 125,
    val xp: Int = 0,
    val level: Int = 1,
    val lastUpdate: Long = System.currentTimeMillis()
) {
    val moodScore: Int
        get() = ((hunger + happiness + energy + cleanliness) / 4f).roundToInt().coerceIn(0, 100)

    val mood: String
        get() = when (moodScore) {
            in 80..100 -> "Excellent"
            in 60..79 -> "Happy"
            in 40..59 -> "Okay"
            in 20..39 -> "Tired"
            else -> "Needs Care"
        }

    fun xpRequired(): Int = (100 + (level - 1) * 50).coerceAtMost(5000)
}
