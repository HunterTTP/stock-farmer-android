package com.hunter.stockfarmer.game

import androidx.lifecycle.ViewModel

/** Keeps the game alive across screen rotations. */
class GameViewModel : ViewModel() {
    val session = GameSession()
}
