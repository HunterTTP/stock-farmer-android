package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.util.InstantSerializer
import com.hunter.stockfarmer.core.util.secondsSince
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.max

/** A chicken living at a coop: a chick at first, then a hen. */
@Serializable
data class Chicken(
    /** Unique within its coop. */
    val id: Int,
    val variant: String,
    val name: String,
    val position: GridPoint,
    val meal: AnimalMeal? = null,
    @Serializable(with = InstantSerializer::class) val hatchedAt: Instant,
    /** When an egg of this hen was last collected, which is when it is hungry again. */
    @Serializable(with = InstantSerializer::class) val lastHarvestedAt: Instant? = null,
) {
    /** Whether it has eaten since an egg of it was last collected, which is what lets it lay. */
    val hasEaten: Boolean
        get() {
            val meal = meal ?: return false
            return lastHarvestedAt?.let { meal.eatenAt >= it } ?: true
        }

    val kind: ChickenVariant get() = ChickenVariant.named(variant) ?: ChickenVariant.all[0]

    fun isGrown(now: Instant): Boolean = now.secondsSince(hatchedAt) >= GROW_DURATION

    /** How long until a chick becomes a hen; zero once it has. */
    fun timeUntilGrown(now: Instant): Double = max(0.0, GROW_DURATION - now.secondsSince(hatchedAt))

    companion object {
        /** What every hen and chick likes best. */
        val favoriteCrop: Crop get() = Crop.named("beetroot") ?: Crop.starter

        const val MAX_NAME_LENGTH = 20
        const val GROW_DURATION = 10.0 * 60
        const val GROWN_SALE_PRICE = 50.0
        const val DEFAULT_NAME = "Chicken"

        fun hatched(
            id: Int,
            variant: ChickenVariant,
            name: String,
            position: GridPoint,
            hatchedAt: Instant,
        ) = Chicken(id, variant.id, name, position, hatchedAt = hatchedAt)
    }
}

/** The chickens a coop has hatched so far and when the next one is due. */
@Serializable
data class Coop(
    val nextVariantID: String? = null,
    val chickens: List<Chicken> = emptyList(),
    @Serializable(with = InstantSerializer::class) val nextChickAt: Instant,
    val nextChickenID: Int = 0,
) {
    /** How long until the next chick hatches, or null while the coop is full. */
    fun timeUntilNextChick(
        now: Instant,
        capacity: Int,
    ): Double? = if (chickens.size >= capacity) null else max(0.0, nextChickAt.secondsSince(now))

    companion object {
        const val HATCH_INTERVAL = 5.0 * 60

        fun hatchingAt(nextChickAt: Instant) = Coop(nextChickAt = nextChickAt)
    }
}
