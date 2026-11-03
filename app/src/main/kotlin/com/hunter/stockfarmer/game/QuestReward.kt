package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.rules.GameEvent

/** A finished market quest and the XP it paid, as the banner shows it. */
data class QuestReward(
    val summary: String,
    val xp: Int,
) {
    companion object {
        fun from(events: List<GameEvent>): List<QuestReward> =
            events.filterIsInstance<GameEvent.QuestCompleted>().map { QuestReward(it.summary, it.xp) }
    }
}
