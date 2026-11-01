package com.hunter.stockfarmer.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps the game alive across screen rotations and lets time pass in it. */
class GameViewModel : ViewModel() {
    val session = GameSession()
    private val tick = GameTick(session)

    init {
        viewModelScope.launch {
            while (true) {
                tick.tick()
                delay(TICK_MILLIS)
            }
        }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}
