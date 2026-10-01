package com.alf.pet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PetRepository(application)

    private val _state = MutableStateFlow(
        PetRules.elapse(repository.load(), System.currentTimeMillis())
    )
    val state: StateFlow<PetState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var messageJob: Job? = null

    init {
        repository.save(_state.value)
        viewModelScope.launch {
            while (true) {
                delay(TICK_INTERVAL_MS)
                _state.value = PetRules.elapse(_state.value, System.currentTimeMillis())
                repository.save(_state.value)
            }
        }
    }

    fun feed() = perform(PetRules::feed, "Yummy!")
    fun play() = perform(PetRules::play, "That was fun!")
    fun sleep() = perform(PetRules::sleep, "Zzz...")
    fun clean() = perform(PetRules::clean, "Squeaky clean!")

    fun saveNow() {
        repository.save(_state.value)
    }

    private fun perform(action: (PetState) -> PetState, successMessage: String) {
        val current = PetRules.elapse(_state.value, System.currentTimeMillis())
        val updated = action(current)
        _state.value = updated
        repository.save(updated)
        if (updated == current) return
        showMessage(
            if (updated.level > current.level) "Level up! Level ${updated.level}" else successMessage
        )
    }

    private fun showMessage(text: String) {
        messageJob?.cancel()
        _message.value = text
        messageJob = viewModelScope.launch {
            delay(2_500)
            _message.value = null
        }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 5_000L
    }
}
