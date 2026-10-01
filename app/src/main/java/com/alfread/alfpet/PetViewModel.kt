package com.alfread.alfpet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PetViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PetStore(application)
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<PetState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _levelUp = MutableStateFlow<Int?>(null)
    val levelUp: StateFlow<Int?> = _levelUp.asStateFlow()

    init {
        refreshFromClock()
    }

    fun refreshFromClock(now: Long = System.currentTimeMillis()) {
        val current = _state.value
        val elapsedMinutes = ((now - current.lastUpdate).coerceAtLeast(0L) / 60_000L).toInt()
        val blocks = elapsedMinutes / 10
        if (blocks <= 0) return

        val updated = current.copy(
            hunger = (current.hunger - blocks * 2).coerceIn(0, 100),
            happiness = (current.happiness - blocks).coerceIn(0, 100),
            energy = (current.energy - blocks).coerceIn(0, 100),
            cleanliness = (current.cleanliness - blocks).coerceIn(0, 100),
            lastUpdate = now
        )
        setState(updated)
    }

    fun persistNow(now: Long = System.currentTimeMillis()) {
        val updated = _state.value.copy(lastUpdate = now)
        _state.value = updated
        store.save(updated)
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearLevelUp() {
        _levelUp.value = null
    }

    fun feed() {
        val current = _state.value
        applyActivity(
            current.copy(
                hunger = (current.hunger + 18).coerceAtMost(100),
                happiness = (current.happiness + 2).coerceAtMost(100),
                coins = current.coins + 2
            ),
            xpGain = 8,
            message = "Fed ALF · Hunger +18"
        )
    }

    fun play() {
        val current = _state.value
        if (current.energy < 12) {
            _message.value = "Play is unavailable · Energy is too low"
            return
        }
        applyActivity(
            current.copy(
                happiness = (current.happiness + 20).coerceAtMost(100),
                energy = (current.energy - 12).coerceAtLeast(0),
                hunger = (current.hunger - 3).coerceAtLeast(0),
                coins = current.coins + 5
            ),
            xpGain = 12,
            message = "Play time · Happiness +20"
        )
    }

    fun sleep() {
        val current = _state.value
        applyActivity(
            current.copy(
                energy = (current.energy + 30).coerceAtMost(100),
                happiness = (current.happiness + 2).coerceAtMost(100)
            ),
            xpGain = 5,
            message = "Rested ALF · Energy +30"
        )
    }

    fun clean() {
        val current = _state.value
        applyActivity(
            current.copy(
                cleanliness = (current.cleanliness + 28).coerceAtMost(100),
                happiness = (current.happiness + 3).coerceAtMost(100),
                coins = current.coins + 1
            ),
            xpGain = 7,
            message = "Fresh and clean · Cleanliness +28"
        )
    }

    private fun applyActivity(base: PetState, xpGain: Int, message: String) {
        var level = base.level
        var xp = base.xp + xpGain
        var leveledUpTo: Int? = null

        while (xp >= (100 + (level - 1) * 50) && level < 99) {
            xp -= 100 + (level - 1) * 50
            level += 1
            leveledUpTo = level
        }

        val updated = base.copy(
            xp = xp,
            level = level,
            lastUpdate = System.currentTimeMillis()
        )
        setState(updated)
        _message.value = message
        if (leveledUpTo != null) {
            _levelUp.value = leveledUpTo
        }
    }

    private fun setState(updated: PetState) {
        _state.value = updated
        store.save(updated)
    }
}
