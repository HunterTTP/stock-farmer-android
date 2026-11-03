package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.util.smoothstep
import java.time.Instant
import kotlin.math.min

/** Daily rain builds, pours, and dies away on the farm clock. */
object Weather {
    internal val rainStart = 14.0 * 60
    val rainStartMinute: Double get() = rainStart
    internal val rainLength = 180.0
    internal val fadeMinutes = 20.0

    fun rainAmount(minuteOfDay: Double): Double {
        val since = minuteOfDay - rainStart
        if (!(since > 0 && since < rainLength)) return 0.0
        return min(1.0, min(since, rainLength - since) / fadeMinutes)
    }

    /** Picture and sound follow the same easing so neither runs ahead of the other. */
    fun eased(amount: Double): Double = smoothstep(amount)

    /** Animals remain sheltered from halfway through the build-up until the rain stops. */
    fun isPouring(minuteOfDay: Double): Boolean {
        val since = minuteOfDay - rainStart
        return since >= fadeMinutes / 2 && since < rainLength
    }

    fun isPouring(
        at: Instant,
        offset: Double,
    ): Boolean = isPouring(DayNightCycle.minuteOfDay(at, offset))

    fun rainAmount(
        at: Instant,
        offset: Double,
    ): Double = rainAmount(DayNightCycle.minuteOfDay(at, offset))
}
