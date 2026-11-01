package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.CareerRecord
import kotlinx.serialization.Serializable

/**
 * The season the farm is dressed for, which only changes how it looks. For now it is chosen by hand; [of] is the turn
 * of the seasons every thirty days of the farm's calendar, for when they follow it again.
 */
@Serializable
@Suppress("ktlint:standard:enum-entry-name-case")
enum class Season {
    spring,
    fall,
    winter,
    ;

    companion object {
        const val DAYS_PER_SEASON = 30

        fun of(day: Int): Season = entries[(maxOf(day, 1) - 1) / DAYS_PER_SEASON % entries.size]
    }
}

/** Dresses the farm for a season, and notes the season it leaves and the one it takes as seen in the Career. */
fun GameEngine.setSeason(season: Season) {
    noteSeenEnvironment(CareerRecord.environmentID(state.season))
    noteSeenEnvironment(CareerRecord.environmentID(season))
    state.season = season
}
