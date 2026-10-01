package com.alf.pet

import android.content.Context
import android.content.SharedPreferences

class PetRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): PetState {
        if (!prefs.contains(KEY_LAST_UPDATE)) {
            return PetState(lastUpdate = System.currentTimeMillis())
        }
        return PetState(
            hunger = prefs.getFloat(KEY_HUNGER, 80f),
            happiness = prefs.getFloat(KEY_HAPPINESS, 80f),
            energy = prefs.getFloat(KEY_ENERGY, 80f),
            cleanliness = prefs.getFloat(KEY_CLEANLINESS, 80f),
            coins = prefs.getInt(KEY_COINS, 0),
            xp = prefs.getInt(KEY_XP, 0),
            level = prefs.getInt(KEY_LEVEL, 1),
            lastUpdate = prefs.getLong(KEY_LAST_UPDATE, System.currentTimeMillis())
        )
    }

    fun save(state: PetState) {
        prefs.edit()
            .putFloat(KEY_HUNGER, state.hunger)
            .putFloat(KEY_HAPPINESS, state.happiness)
            .putFloat(KEY_ENERGY, state.energy)
            .putFloat(KEY_CLEANLINESS, state.cleanliness)
            .putInt(KEY_COINS, state.coins)
            .putInt(KEY_XP, state.xp)
            .putInt(KEY_LEVEL, state.level)
            .putLong(KEY_LAST_UPDATE, state.lastUpdate)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "alf_pet_save"
        const val KEY_HUNGER = "hunger"
        const val KEY_HAPPINESS = "happiness"
        const val KEY_ENERGY = "energy"
        const val KEY_CLEANLINESS = "cleanliness"
        const val KEY_COINS = "coins"
        const val KEY_XP = "xp"
        const val KEY_LEVEL = "level"
        const val KEY_LAST_UPDATE = "last_update"
    }
}
