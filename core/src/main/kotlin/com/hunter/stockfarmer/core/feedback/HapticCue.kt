package com.hunter.stockfarmer.core.feedback

import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.containsRejection

/** The buzz that goes with what just happened: a warning for a refusal, success for good news, else a tap. */
enum class HapticCue {
    Warning,
    Success,
    Medium,
    Light,
    ;

    companion object {
        /** The cue for the most important thing in [events]; null when nothing happened. */
        fun cue(events: List<GameEvent>): HapticCue? =
            when {
                events.isEmpty() -> null
                events.containsRejection -> Warning
                events.any { it is GameEvent.QuestCompleted || it is GameEvent.LeveledUp } -> Success
                events.any { it.movesMoney } -> Medium
                else -> Light
            }

        private val GameEvent.movesMoney: Boolean
            get() =
                this is GameEvent.TractorCargoSold ||
                    this is GameEvent.Demolished ||
                    this is GameEvent.Unfenced ||
                    this is GameEvent.Undecorated ||
                    this is GameEvent.StorageSold ||
                    this is GameEvent.Sold ||
                    this is GameEvent.AnimalSold ||
                    this is GameEvent.BoughtShares ||
                    this is GameEvent.SoldShares
    }
}
