package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.model.roundedToCents
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Grow times, seed prices and harvest rewards all follow from a crop's tier, so they scale together. */
object CropEconomy {
    /** The starter crop has fixed stats: a free seed, a $0.30 reward and 30 seconds of growing. */
    const val STARTER_GROW_MINUTES = 0.5
    const val STARTER_SELL_VALUE = 0.30

    /**
     * The first paid crop grows in a minute, and every later one takes the same multiple longer than the one before, so
     * that the last crop in the catalog takes [SLOWEST_GROW_MINUTES].
     */
    const val FIRST_PAID_GROW_MINUTES = 1.0
    const val SLOWEST_GROW_MINUTES = 2.0 * 60

    /** Each crop earns this much more money per minute of growing than the one before it, until Zucchini. */
    const val EARNINGS_PER_MINUTE_GROWTH = 1.05

    /** Every crop after Zucchini rises by half that much, because the full rate paid too well in the late game. */
    const val LATE_EARNINGS_PER_MINUTE_GROWTH = 1.025
    const val LAST_FULL_GROWTH_TIER = 11.0

    /** A harvest pays this many times what the seed cost. */
    const val REWARD_PER_SEED_PRICE = 2.0

    /** A fruit tree's basket is worth this many of its tier's harvests. */
    const val FRUIT_TREE_CYCLES = 1.5

    /** A fruit tree ripens in this many grow cycles of the crop unlocked right after it, which sits this far up the tiers. */
    const val FRUIT_TREE_GROW_CYCLES = 3.0
    const val NEXT_CROP_TIER_OFFSET = 0.5

    /** A fruit tree costs this many of its baskets' sale value, because it never has to be replanted. */
    const val HARVESTS_TO_REPAY_FRUIT_TREE = 12.0

    private val growTimeGrowth: Double by lazy {
        val steps = (Crop.plainCrops.size - 2).toDouble()
        (SLOWEST_GROW_MINUTES / FIRST_PAID_GROW_MINUTES).pow(1 / steps)
    }

    fun growMinutes(tier: Double): Double {
        if (tier <= 0) return STARTER_GROW_MINUTES
        return FIRST_PAID_GROW_MINUTES * growTimeGrowth.pow(min(tier, (Crop.plainCrops.size - 1).toDouble()) - 1)
    }

    fun earningsPerMinute(tier: Double): Double =
        STARTER_SELL_VALUE / STARTER_GROW_MINUTES *
            EARNINGS_PER_MINUTE_GROWTH.pow(min(tier, LAST_FULL_GROWTH_TIER)) *
            LATE_EARNINGS_PER_MINUTE_GROWTH.pow(max(0.0, tier - LAST_FULL_GROWTH_TIER))

    /** Wheat seeds are free; a paid seed costs half of what its crop pays. */
    fun seedPrice(tier: Double): Double {
        if (tier <= 0) return 0.0
        return (growMinutes(tier) * earningsPerMinute(tier) / REWARD_PER_SEED_PRICE).roundedToCents
    }

    fun sellValue(tier: Double): Double {
        if (tier <= 0) return STARTER_SELL_VALUE
        return (seedPrice(tier) * REWARD_PER_SEED_PRICE).roundedToCents
    }
}
