package com.alfread.alfpet

import android.content.Context

class PetStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): PetState {
        return PetState(
            hunger = prefs.getInt(KEY_HUNGER, 78),
            happiness = prefs.getInt(KEY_HAPPINESS, 84),
            energy = prefs.getInt(KEY_ENERGY, 72),
            cleanliness = prefs.getInt(KEY_CLEANLINESS, 82),
            coins = prefs.getInt(KEY_COINS, 125),
            xp = prefs.getInt(KEY_XP, 0),
            level = prefs.getInt(KEY_LEVEL, 1).coerceAtLeast(1),
            lastUpdate = prefs.getLong(KEY_LAST_UPDATE, System.currentTimeMillis())
        )
    }

    fun save(state: PetState) {
        prefs.edit()
            .putInt(KEY_HUNGER, state.hunger)
            .putInt(KEY_HAPPINESS, state.happiness)
            .putInt(KEY_ENERGY, state.energy)
            .putInt(KEY_CLEANLINESS, state.cleanliness)
            .putInt(KEY_COINS, state.coins)
            .putInt(KEY_XP, state.xp)
            .putInt(KEY_LEVEL, state.level)
            .putLong(KEY_LAST_UPDATE, state.lastUpdate)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "alf_pet_state"
        private const val KEY_HUNGER = "hunger"
        private const val KEY_HAPPINESS = "happiness"
        private const val KEY_ENERGY = "energy"
        private const val KEY_CLEANLINESS = "cleanliness"
        private const val KEY_COINS = "coins"
        private const val KEY_XP = "xp"
        private const val KEY_LEVEL = "level"
        private const val KEY_LAST_UPDATE = "lastUpdate"
    }
}
