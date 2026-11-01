package com.hunter.stockfarmer.core.rules

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
