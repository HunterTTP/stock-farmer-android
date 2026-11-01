package com.hunter.stockfarmer.core.rules

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class SunShadow(
    val dx: Double,
    val dy: Double,
    val length: Double,
    val strength: Double,
)

/** Where shadows fall at a minute of the farm clock; it changes appearance, never gameplay. */
object Sun {
    internal val sunrise = 5.0 * 60
    internal val sunset = 19.0 * 60
    private val shortestLength = 1.2
    private val longestLength = 1.6
    private val widestSwing = 55.0 * PI / 180
    val stepMinutes = 10.0

    fun shadow(minuteOfDay: Double): SunShadow {
        val progress = min(1.0, max(0.0, (minuteOfDay - sunrise) / (sunset - sunrise)))
        val angle = progress * PI
        val height = sin(angle)
        val low = (1 - height) * (1 - height)
        val daylight = 1 - DayNightCycle.darkness(minuteOfDay)
        // The square root keeps shadows mostly to one side, standing straight back only at midday.
        val across = cos(angle)
        val swing = (if (across < 0) 1.0 else -1.0) * widestSwing * sqrt(abs(across))
        return SunShadow(
            sin(swing),
            cos(swing),
            shortestLength + (longestLength - shortestLength) * low,
            daylight * (0.5 + 0.5 * height),
        )
    }

    fun step(minuteOfDay: Double): Int = (minuteOfDay / stepMinutes).toInt()
}
