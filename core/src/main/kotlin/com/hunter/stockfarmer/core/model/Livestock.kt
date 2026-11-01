package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.LivestockVariant
import com.hunter.stockfarmer.core.catalog.LivestockWork
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.util.InstantSerializer
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.secondsSince
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.max

/** An animal living in a building that holds livestock: a baby at first, then the grown animal it was always going to be. */
@Serializable
data class Livestock(
    /** Unique within its building. */
    val id: Int,
    /** The id of its [LivestockVariant]. */
    val variant: String,
    val name: String,
    val position: GridPoint,
    val meal: AnimalMeal? = null,
    @Serializable(with = InstantSerializer::class) val bornAt: Instant,
    /** Extra age earned from fast growth on feed, added to the time since birth. */
    val growthBonus: Double = 0.0,
    /** When what it gives, if it gives something now and then, is next ready. */
    @Serializable(with = InstantSerializer::class) val readyAt: Instant = readyAtBirth(variant, bornAt),
    /** When what it gives was last taken from it, null if it never has been; until [readyAt] it shows it, a sheep without its wool. */
    @Serializable(with = InstantSerializer::class) val lastWorkedAt: Instant? = null,
) {
    val species: LivestockSpecies get() = LivestockSpecies.variant(variant)?.first ?: LivestockSpecies.cow

    val look: LivestockVariant get() = LivestockSpecies.variant(variant)?.second ?: LivestockSpecies.cow.variants[0]

    /** How old it counts as, with the extra age feed has given it. */
    fun age(now: Instant): Double = now.secondsSince(bornAt) + growthBonus

    fun isGrown(now: Instant): Boolean = age(now) >= species.growDuration

    /** How long until it grows up at the given growth speed; zero once it has. */
    fun timeUntilGrown(now: Instant): Double {
        val remaining = max(0.0, species.growDuration - age(now))
        val meal = meal
        if (meal == null || !meal.isActive(now)) return remaining
        val boosted = meal.endsAt.secondsSince(now)
        val rate = AnimalMeal.MULTIPLIER
        return if (remaining <= boosted * rate) remaining / rate else boosted + (remaining - boosted * rate)
    }

    /** Whether it has eaten since what it gives was last taken from it, which is what lets it start giving the next. */
    val hasEaten: Boolean
        get() {
            val meal = meal ?: return false
            return lastWorkedAt?.let { meal.eatenAt >= it } ?: true
        }

    /**
     * What it has ready to be collected from it, and the tool that collects it, if it is grown, its time has come and it
     * has eaten since the last time it was harvested.
     */
    fun readyProduce(now: Instant): Pair<StorageItem, HarvestTool>? {
        val work = species.work as LivestockWork.Gives
        if (!isGrown(now) || now < readyAt || !(hasEaten || !species.eatsToProduce)) return null
        return work.item to work.tool
    }

    companion object {
        const val MAX_NAME_LENGTH = 20

        fun born(
            id: Int,
            variant: LivestockVariant,
            name: String,
            position: GridPoint,
            bornAt: Instant,
        ) = Livestock(id, variant.id, name, position, bornAt = bornAt)

        private fun readyAtBirth(
            variant: String,
            bornAt: Instant,
        ): Instant {
            val species = LivestockSpecies.variant(variant)?.first ?: return bornAt
            return bornAt.advancedBy(species.growDuration + species.workInterval)
        }
    }
}

/** The animals a building has so far, when its next baby is due, and their last production update. */
@Serializable
data class Herd(
    val animals: List<Livestock> = emptyList(),
    @Serializable(with = InstantSerializer::class) val nextBirthAt: Instant,
    /** How far the herd's growth and births have been brought up to date. */
    @Serializable(with = InstantSerializer::class) val processedAt: Instant? = null,
    val nextAnimalID: Int = 0,
) {
    /** How long until the next baby, or null while the building is full. */
    fun timeUntilNextBirth(
        now: Instant,
        capacity: Int,
    ): Double? = if (animals.size >= capacity) null else max(0.0, nextBirthAt.secondsSince(now))
}
