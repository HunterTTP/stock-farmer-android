package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.util.InstantSerializer
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.secondsSince
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.max

@Serializable
data class AnimalMeal(
    @Serializable(with = InstantSerializer::class) val eatenAt: Instant,
) {
    val endsAt: Instant get() = eatenAt.advancedBy(DURATION)

    fun isActive(now: Instant): Boolean = now >= eatenAt && now < endsAt

    internal fun productiveSeconds(
        interval: Double,
        endingAt: Instant,
    ): Double {
        val elapsed = max(0.0, interval)
        return elapsed + boostedSeconds(endingAt.advancedBy(-elapsed), endingAt) * (MULTIPLIER - 1)
    }

    internal fun boostedSeconds(
        start: Instant,
        end: Instant,
    ): Double = max(0.0, minOf(end, endsAt).secondsSince(maxOf(start, eatenAt)))

    companion object {
        const val DURATION = 5.0 * 60
        const val MULTIPLIER = 2.0

        /** How long an animal that gives something to harvest goes without eating before it shows that it is hungry. */
        val hungerDelay: Double = 2 * DayNightCycle.secondsPerDay
    }
}

/**
 * What it grazes and is fed by hand, a different plain crop for each kind that gives something to harvest, one that
 * unlocks by the level its home does; null for ducks, which give eggs without eating and so are never fed.
 */
val LivestockSpecies.favoriteCrop: Crop?
    get() =
        when (id) {
            "rabbit" -> "carrot"
            "pig" -> "potato"
            "sheep" -> "lettuce"
            "goat" -> "cabbage"
            "donkey" -> "broccoli"
            "cow" -> "zucchini"
            else -> null
        }?.let(Crop::named)

/** Whether it must eat its favorite crop before it gives anything: every animal that gives something does, except ducks. */
val LivestockSpecies.eatsToProduce: Boolean get() = favoriteCrop != null
