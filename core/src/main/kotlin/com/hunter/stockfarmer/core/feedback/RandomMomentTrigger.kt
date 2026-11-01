package com.hunter.stockfarmer.core.feedback

import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.nextDouble
import kotlin.math.floor

/** Goes off once in every stretch of time, at a different moment each time, however often it is asked. */
data class RandomMomentTrigger(
    private val period: Double,
    private val window: Long = -1,
    private val moment: Double = 0.0,
    private val hasFired: Boolean = true,
) {
    fun isDue(
        at: Double,
        using: RandomSource,
    ): Pair<RandomMomentTrigger, Boolean> {
        val current = floor(at / period).toLong()
        val trigger =
            if (current !=
                window
            ) {
                copy(window = current, moment = using.nextDouble(0.0, period), hasFired = false)
            } else {
                this
            }
        if (trigger.hasFired || at - current.toDouble() * period < trigger.moment) return trigger to false
        return trigger.copy(hasFired = true) to true
    }
}
