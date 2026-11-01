package com.hunter.stockfarmer.core.rules

import java.time.Instant
import java.util.Locale

/** The visual farm clock, whose offset holds time while away. Daylight also controls animal shelter. */
object DayNightCycle {
    val minutesPerDay = 24.0 * 60

    /** A real second is one minute on the farm clock. */
    val secondsPerDay = minutesPerDay
    val nightStart = 21.0 * 60
    val nightEnd = 5.0 * 60
    private val twilight = 60.0

    fun minuteOfDay(
        at: Instant,
        offset: Double,
    ): Double {
        val seconds = (at.epochSecond.toDouble() + at.nano / 1_000_000_000.0 + offset) % secondsPerDay
        return (if (seconds < 0) seconds + secondsPerDay else seconds) * (minutesPerDay / secondsPerDay)
    }

    /** Darkness rises over the hour before 9 pm, and falls over the hour around 5 am. */
    fun darkness(minuteOfDay: Double): Double {
        val dusk = nightStart - twilight
        val sinceDusk = (minuteOfDay - dusk + minutesPerDay) % minutesPerDay
        val nightLength = (nightEnd - nightStart + minutesPerDay) % minutesPerDay + twilight * 1.5
        if (!(sinceDusk < nightLength)) return 0.0
        if (sinceDusk < twilight) return smooth(sinceDusk / twilight)
        if (sinceDusk > nightLength - twilight) return smooth((nightLength - sinceDusk) / twilight)
        return 1.0
    }

    fun darkness(
        at: Instant,
        offset: Double,
    ): Double = darkness(minuteOfDay(at, offset))

    fun offset(
        settingMinute: Double,
        at: Instant,
    ): Double = (settingMinute - minuteOfDay(at, 0.0)) * (secondsPerDay / minutesPerDay)

    fun clockText(minuteOfDay: Double): String {
        val whole = minuteOfDay.toLong() % minutesPerDay.toLong()
        val hour = whole / 60
        val twelveHour = if (hour % 12 == 0L) 12L else hour % 12
        return "$twelveHour:" + String.format(Locale.US, "%02d", whole % 60) + if (hour < 12) " AM" else " PM"
    }

    private fun smooth(x: Double): Double = x * x * (3 - 2 * x)
}
