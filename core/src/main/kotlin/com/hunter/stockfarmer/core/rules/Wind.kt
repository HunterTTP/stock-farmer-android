package com.hunter.stockfarmer.core.rules

import java.time.Instant
import kotlin.math.floor

/**
 * Which way the wind blows. It keeps to one way for a long spell and then may change: the way is settled afresh every
 * five real minutes, which are five hours of the farm's clock, the same for everyone with the same clock, and the same
 * however many gusts come in it.
 */
object Wind {
    /** How long, in minutes of the farm's clock, the wind keeps one way at least: five real minutes. */
    const val SPELL_MINUTES = 300.0

    /** Whether the wind blows towards the right of the map at a moment, for a clock set by [offset] seconds. */
    fun pushesRight(
        at: Instant,
        offset: Double,
    ): Boolean {
        val seconds = at.epochSecond.toDouble() + at.nano / 1_000_000_000.0 + offset
        val minutes = seconds * (DayNightCycle.minutesPerDay / DayNightCycle.secondsPerDay)
        val spell = floor(minutes / SPELL_MINUTES).toLong()
        return StageRandom("wind|$spell").next() < 0.5
    }
}
