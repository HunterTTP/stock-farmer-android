package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero
import kotlin.math.max

object LevelRules {
    /** Harvests of a fully planted field of the newest crop it takes to leave a level. */
    const val HARVESTS_PER_LEVEL = 3

    /** The last crop unlocks at the last level, and there are no levels beyond it. */
    val maxLevel: Int get() = Crop.progression.size

    /**
     * XP spent inside [level] before the next one: three harvests of every farmland tile available at that level, each
     * earning the XP of the crop that unlocked at that level.
     */
    fun xpToLeave(level: Int): Int {
        if (level - 1 !in Crop.progression.indices) return 0
        return HARVESTS_PER_LEVEL * FarmlandRules.tilesUnlocked(level) * plainHarvestXP(Crop.progression[level - 1])
    }

    /** The XP at which each level begins, from level 1. */
    private val levelStarts: List<Int> by lazy {
        (1 until Crop.progression.size).fold(mutableListOf(0)) { starts, level ->
            starts.also { it += starts[level - 1] + xpToLeave(level) }
        }
    }

    /** Total XP needed to be at [level]. */
    fun xpToReach(level: Int): Int = levelStarts[level.coerceIn(1, maxLevel) - 1]

    fun levelForXP(xp: Int): Int {
        var low = 0
        var high = levelStarts.size
        while (low < high) {
            val middle = (low + high) / 2
            if (levelStarts[middle] <= xp) low = middle + 1 else high = middle
        }
        return max(1, low)
    }

    /** XP earned inside the current level and the XP that level spans. At the last level the bar is simply full. */
    fun progress(xp: Int): Pair<Int, Int> {
        val level = levelForXP(xp)
        if (level >= maxLevel) return 1 to 1
        return xp - xpToReach(level) to xpToLeave(level)
    }

    /**
     * Harvesting a fully grown crop earns XP, and nothing is earned at the last level. Wheat earns 2 XP and every later
     * crop one more. A fruit tree earns one and a half times what a crop of its level would.
     */
    fun harvestXP(crop: Crop): Int =
        if (crop.isFruitTree) {
            (plainHarvestXP(crop) * CropEconomy.FRUIT_TREE_CYCLES).roundedHalfAwayFromZero().toInt()
        } else {
            plainHarvestXP(crop)
        }

    /**
     * A fish takes minutes to land, so an average common one earns this share of the XP it takes to leave the player's
     * level, which keeps it worth the same at every level.
     */
    const val COMMON_FISH_SHARE_OF_A_LEVEL = 1.0 / 300

    /**
     * Landing a fish earns that, times 1 for a common fish and more the rarer and harder to land it is (2, 4, 7 and 10),
     * and 0.5 to 1.5 times that by how heavy it is for its kind; nothing is earned at the last level.
     */
    fun fishXP(
        kind: FishKind,
        weight: Double,
        atLevel: Int,
    ): Int {
        if (atLevel >= maxLevel) return 0
        val xp =
            xpToLeave(atLevel).toDouble() * COMMON_FISH_SHARE_OF_A_LEVEL * kind.rarity.xpMultiplier *
                kind.xpFactor(weight)
        return max(1, xp.roundedHalfAwayFromZero().toInt())
    }

    /**
     * Taking what an animal has ready earns the XP of harvesting a crop of the level its home unlocks at: that level plus
     * one, so an egg, a can of milk, a fleece or fertilizer is worth about what a crop of its time in the game is.
     * Nothing is earned at the last level.
     */
    fun produceXP(
        item: StorageItem,
        atLevel: Int,
    ): Int = if (atLevel < maxLevel) item.unlockLevel + 1 else 0

    /**
     * A butterfly is caught with a tap, so a common one earns half what a common fish does, and rarer wings earn the
     * fish's multipliers (or 15 times for wings that change color), at every level.
     */
    const val COMMON_BUTTERFLY_SHARE_OF_A_LEVEL = 1.0 / 600
    private const val SHIFTING_BUTTERFLY_MULTIPLIER = 15

    /** Catching a wild butterfly earns that, never less than 1, and nothing at the last level. */
    fun butterflyXP(
        wings: Butterflies.Wings,
        atLevel: Int,
    ): Int {
        if (atLevel >= maxLevel) return 0
        val multiplier =
            when (wings) {
                is Butterflies.Wings.Color -> wings.rarity.xpMultiplier
                Butterflies.Wings.Shifting -> SHIFTING_BUTTERFLY_MULTIPLIER
            }
        val xp = xpToLeave(atLevel).toDouble() * COMMON_BUTTERFLY_SHARE_OF_A_LEVEL * multiplier
        return max(1, xp.roundedHalfAwayFromZero().toInt())
    }

    /** What a crop of that level earns, which is also what the levels' XP is worked out from. */
    private fun plainHarvestXP(crop: Crop): Int = crop.unlockLevel + 1
}
