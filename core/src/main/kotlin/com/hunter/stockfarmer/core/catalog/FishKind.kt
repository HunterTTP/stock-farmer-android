package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.nextDouble
import com.hunter.stockfarmer.core.util.nextInt
import com.hunter.stockfarmer.core.util.nextUnit
import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero

/** Fish of one kind sell for the same price, whatever their weight. */
data class FishKind private constructor(
    val id: String,
    val name: String,
    val rarity: Rarity,
) {
    val value: Double get() = rarity.fishPrice
    val assetName: String get() = "fish_fishing-$id"
    val weightRange: ClosedFloatingPointRange<Double> get() = weights[id] ?: 1.0..10.0

    fun heaviness(ofWeight: Double): Double =
        (
            (ofWeight - weightRange.start) /
                (weightRange.endInclusive - weightRange.start)
        ).coerceIn(0.0, 1.0)

    fun weightPercentile(forWeight: Double): Double {
        val fraction = heaviness(forWeight)
        return if (fraction <= 0.5) 2 * fraction * fraction else 1 - 2 * (1 - fraction) * (1 - fraction)
    }

    fun weightRarity(forWeight: Double): Rarity {
        val percentile = weightPercentile(forWeight)
        return when {
            percentile < 0.5 -> Rarity.common

            percentile < 0.75 -> Rarity.uncommon

            percentile < 0.9 -> Rarity.rare

            percentile <
                0.99 -> Rarity.epic

            else -> Rarity.legendary
        }
    }

/** Most weights are middling; the lightest and heaviest are rare. */
    fun randomWeight(using: RandomSource): Double {
        val middling = (using.nextUnit() + using.nextUnit()) / 2
        val range = weightRange
        return ((range.start + middling * (range.endInclusive - range.start)) * 10).roundedHalfAwayFromZero() / 10
    }

    fun sizeFactor(forWeight: Double): Double =
        lightestSizeFactor + (heaviestSizeFactor - lightestSizeFactor) * heaviness(forWeight)

    fun xpFactor(forWeight: Double): Double =
        lightestXPFactor + (heaviestXPFactor - lightestXPFactor) * heaviness(forWeight)

    companion object {
        val lightestXPFactor = 0.5
        val heaviestXPFactor = 1.5
        val lightestSizeFactor = 0.8
        val heaviestSizeFactor = 1.2
        val all =
            listOf(
                FishKind("angler", "Anglerfish", Rarity.epic),
                FishKind("bigmouthbass", "Bigmouth Bass", Rarity.uncommon),
                FishKind("bluegill", "Bluegill", Rarity.common),
                FishKind("bluemarlin", "Blue Marlin", Rarity.legendary),
                FishKind("brooktrout", "Brook Trout", Rarity.common),
                FishKind("brownray", "Brown Ray", Rarity.epic),
                FishKind("carp", "Carp", Rarity.uncommon),
                FishKind("cod", "Cod", Rarity.epic),
                FishKind("crappie", "Crappie", Rarity.common),
                FishKind("dorado", "Dorado", Rarity.rare),
                FishKind("haddock", "Haddock", Rarity.rare),
                FishKind("harborpike", "Harbor Pike", Rarity.epic),
                FishKind("jellyfish", "Jellyfish", Rarity.rare),
                FishKind("kingsalmon", "King Salmon", Rarity.epic),
                FishKind("lionfish", "Lionfish", Rarity.rare),
                FishKind("lobster", "Lobster", Rarity.rare),
                FishKind("longnosegar", "Longnose Gar", Rarity.uncommon),
                FishKind("mackerel", "Mackerel", Rarity.rare),
                FishKind("northernpike", "Northern Pike", Rarity.uncommon),
                FishKind("octopus", "Octopus", Rarity.epic),
                FishKind("pinksalmon", "Pink Salmon", Rarity.uncommon),
                FishKind("piranha", "Piranha", Rarity.rare),
                FishKind("plaice", "Plaice", Rarity.rare),
                FishKind("pollock", "Pollock", Rarity.epic),
                FishKind("pufferfish", "Pufferfish", Rarity.rare),
                FishKind("rainbowtrout", "Rainbow Trout", Rarity.common),
                FishKind("redlionfish", "Red Lionfish", Rarity.rare),
                FishKind("redporgy", "Red Porgy", Rarity.rare),
                FishKind("redsnapper", "Red Snapper", Rarity.epic),
                FishKind("sailfish", "Sailfish", Rarity.legendary),
                FishKind("salmon", "Salmon", Rarity.uncommon),
                FishKind("sandbarshark", "Sandbar Shark", Rarity.legendary),
                FishKind("sharptoothcatfish", "Sharptooth Catfish", Rarity.rare),
                FishKind("sockeyesalmon", "Sockeye Salmon", Rarity.uncommon),
                FishKind("spadefish", "Spadefish", Rarity.rare),
                FishKind("spotcroacker", "Spot Croaker", Rarity.rare),
                FishKind("squid", "Squid", Rarity.rare),
                FishKind("sturgeon", "Sturgeon", Rarity.legendary),
                FishKind("sunfish", "Sunfish", Rarity.common),
                FishKind("tuna", "Tuna", Rarity.legendary),
                FishKind("whitemarlin", "White Marlin", Rarity.legendary),
                FishKind("yellowperch", "Yellow Perch", Rarity.common),
            )

/** Draw a rarity by its unlocked spawn weight, then a species with equal probability. */
        fun random(
            atLevel: Int,
            using: RandomSource,
        ): FishKind {
            val unlocked = Rarity.spawning(atLevel)
            val total = unlocked.sumOf { it.spawnRate(atLevel) }
            require(total > 0) { "No fish rarity is unlocked" }
            var roll = using.nextDouble(0.0, total)
            val rarity =
                unlocked.firstOrNull {
                    roll -= it.spawnRate(atLevel)
                    roll < 0
                } ?: Rarity.common
            val species = all.filter { it.rarity == rarity }
            return species[using.nextInt(species.indices)]
        }

        fun named(id: String): FishKind? = all.firstOrNull { it.id == id }

        private val weights =
            mapOf(
                "angler" to 8.0..45.0,
                "bigmouthbass" to 2.0..12.0,
                "bluegill" to 0.3..2.0,
                "bluemarlin" to 150.0..500.0,
                "brooktrout" to 0.5..5.0,
                "brownray" to 5.0..30.0,
                "carp" to 5.0..40.0,
                "cod" to 5.0..50.0,
                "crappie" to 0.5..3.0,
                "dorado" to 10.0..50.0,
                "haddock" to 2.0..12.0,
                "harborpike" to 3.0..25.0,
                "jellyfish" to 1.0..20.0,
                "kingsalmon" to 15.0..60.0,
                "lionfish" to 0.5..2.5,
                "lobster" to 1.0..10.0,
                "longnosegar" to 5.0..35.0,
                "mackerel" to 1.0..6.0,
                "northernpike" to 5.0..30.0,
                "octopus" to 5.0..40.0,
                "pinksalmon" to 3.0..10.0,
                "piranha" to 1.0..4.0,
                "plaice" to 1.0..8.0,
                "pollock" to 3.0..25.0,
                "pufferfish" to 1.0..6.0,
                "rainbowtrout" to 1.0..15.0,
                "redlionfish" to 0.5..2.5,
                "redporgy" to 1.0..8.0,
                "redsnapper" to 3.0..25.0,
                "sailfish" to 40.0..150.0,
                "salmon" to 5.0..30.0,
                "sandbarshark" to 40.0..200.0,
                "sharptoothcatfish" to 5.0..45.0,
                "sockeyesalmon" to 3.0..12.0,
                "spadefish" to 1.0..10.0,
                "spotcroacker" to 0.5..3.0,
                "squid" to 1.0..20.0,
                "sturgeon" to 50.0..250.0,
                "sunfish" to 0.2..2.0,
                "tuna" to 30.0..200.0,
                "whitemarlin" to 50.0..180.0,
                "yellowperch" to 0.3..3.0,
            )
    }
}
