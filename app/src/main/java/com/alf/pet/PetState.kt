package com.alf.pet

data class PetState(
    val hunger: Float = 80f,
    val happiness: Float = 80f,
    val energy: Float = 80f,
    val cleanliness: Float = 80f,
    val coins: Int = 0,
    val xp: Int = 0,
    val level: Int = 1,
    val lastUpdate: Long
)

object PetRules {
    const val MAX_STAT = 100f

    private const val MAX_OFFLINE_MINUTES = 480f
    private const val HUNGER_DECAY_PER_MIN = 0.8f
    private const val HAPPINESS_DECAY_PER_MIN = 0.5f
    private const val ENERGY_DECAY_PER_MIN = 0.4f
    private const val CLEAN_DECAY_PER_MIN = 0.6f
    private const val LOW_STAT = 25f
    private const val NEGLECT_MULTIPLIER = 1.5f
    private const val PLAY_ENERGY_COST = 15f

    fun xpToNext(level: Int): Int = 50 + (level - 1) * 25

    /** Menerapkan penurunan status berdasarkan waktu yang berlalu (maks 8 jam). */
    fun elapse(state: PetState, now: Long): PetState {
        if (now < state.lastUpdate) return state.copy(lastUpdate = now)
        if (now == state.lastUpdate) return state

        val minutes = ((now - state.lastUpdate) / 60_000f).coerceAtMost(MAX_OFFLINE_MINUTES)
        val neglected = state.hunger < LOW_STAT || state.cleanliness < LOW_STAT
        val happinessRate =
            if (neglected) HAPPINESS_DECAY_PER_MIN * NEGLECT_MULTIPLIER else HAPPINESS_DECAY_PER_MIN

        return state.copy(
            hunger = (state.hunger - HUNGER_DECAY_PER_MIN * minutes).coerceIn(0f, MAX_STAT),
            happiness = (state.happiness - happinessRate * minutes).coerceIn(0f, MAX_STAT),
            energy = (state.energy - ENERGY_DECAY_PER_MIN * minutes).coerceIn(0f, MAX_STAT),
            cleanliness = (state.cleanliness - CLEAN_DECAY_PER_MIN * minutes).coerceIn(0f, MAX_STAT),
            lastUpdate = now
        )
    }

    fun canFeed(s: PetState) = s.hunger < MAX_STAT
    fun canPlay(s: PetState) = s.energy >= PLAY_ENERGY_COST && s.happiness < MAX_STAT
    fun canSleep(s: PetState) = s.energy < MAX_STAT
    fun canClean(s: PetState) = s.cleanliness < MAX_STAT

    fun feed(s: PetState): PetState {
        if (!canFeed(s)) return s
        return reward(s.copy(hunger = (s.hunger + 25f).coerceAtMost(MAX_STAT)), xp = 5, coins = 1)
    }

    fun play(s: PetState): PetState {
        if (!canPlay(s)) return s
        return reward(
            s.copy(
                happiness = (s.happiness + 20f).coerceAtMost(MAX_STAT),
                energy = (s.energy - PLAY_ENERGY_COST).coerceAtLeast(0f)
            ),
            xp = 10,
            coins = 3
        )
    }

    fun sleep(s: PetState): PetState {
        if (!canSleep(s)) return s
        return reward(s.copy(energy = (s.energy + 30f).coerceAtMost(MAX_STAT)), xp = 4, coins = 1)
    }

    fun clean(s: PetState): PetState {
        if (!canClean(s)) return s
        return reward(
            s.copy(cleanliness = (s.cleanliness + 25f).coerceAtMost(MAX_STAT)),
            xp = 5,
            coins = 1
        )
    }

    fun moodLabel(s: PetState): String = when {
        s.energy < 20f -> "Sleepy"
        s.hunger < 25f -> "Hungry"
        s.cleanliness < 25f -> "Dirty"
        s.happiness < 25f -> "Sad"
        (s.hunger + s.happiness + s.energy + s.cleanliness) / 4f >= 70f -> "Happy"
        else -> "Okay"
    }

    private fun reward(s: PetState, xp: Int, coins: Int): PetState {
        var level = s.level
        var totalXp = s.xp + xp
        var totalCoins = s.coins + coins
        while (totalXp >= xpToNext(level)) {
            totalXp -= xpToNext(level)
            level++
            totalCoins += level * 5
        }
        return s.copy(level = level, xp = totalXp, coins = totalCoins)
    }
}
