package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class RarityTest {
    @Test fun `theOneScaleIsShownInWhiteGreenBluePurpleAndOrange`() {
        assertEquals(listOf(Rarity.common, Rarity.uncommon, Rarity.rare, Rarity.epic, Rarity.legendary), Rarity.entries)
        val colors = Rarity.entries.map { it.color }
        assertTrue(colors.all { listOf(it.red, it.green, it.blue).all { value -> value in 0.0..1.0 } })
        val (white, green, blue, purple, orange) = colors
        assertTrue(white.red == 1.0 && white.green == 1.0 && white.blue == 1.0)
        assertTrue(green.green > green.red && green.green > green.blue)
        assertTrue(blue.blue > blue.red && blue.blue > blue.green)
        assertTrue(purple.red > purple.green && purple.blue > purple.green && purple.red < purple.blue)
        assertTrue(orange.red > orange.green && orange.green > orange.blue)
    }

    @Test fun `allFiveRaritiesAreUsedByNamedSpecies`() {
        assertEquals(Rarity.entries.toSet(), FishKind.all.map { it.rarity }.toSet())
        listOf("bluegill", "carp", "mackerel", "kingsalmon", "bluemarlin").forEachIndexed { i, id ->
            assertEquals(Rarity.entries[i], FishKind.named(id)!!.rarity)
        }
    }

    @Test fun `theCommonestFishAreSmallPondFishAndTheHugeOnesAreLegendary`() {
        val weights =
            Rarity.entries.map { rarity ->
                FishKind.all
                    .filter { it.rarity == rarity }
                    .map { (it.weightRange.start + it.weightRange.endInclusive) / 2 }
                    .average()
            }
        assertTrue(weights.first() == weights.min() && weights.last() == weights.max())
        assertTrue(weights[3] > weights[2])
        assertEquals(
            listOf(6, 7, 15, 8, 6),
            Rarity.entries.map { rarity ->
                FishKind.all.count { it.rarity == rarity }
            },
        )
        for (id in listOf(
            "bluegill",
            "sunfish",
            "yellowperch",
            "crappie",
        )) {
            assertEquals(Rarity.common, FishKind.named(id)?.rarity)
        }
        for (id in listOf(
            "bluemarlin",
            "sailfish",
            "whitemarlin",
            "tuna",
            "sandbarshark",
            "sturgeon",
        )) {
            assertEquals(Rarity.legendary, FishKind.named(id)?.rarity)
        }
    }

    @Test fun `fishPricesRiseWithRarityToAThousandDollarsAndXPGrowsWithLevel`() {
        // The LevelRules fishXP assertions await LevelRules.
        assertTrue(FishKind.all.all { it.value == it.rarity.fishPrice })
        val prices = Rarity.entries.map { it.fishPrice }
        assertEquals(prices.sorted(), prices)
        assertEquals(5, prices.toSet().size)
        assertEquals(1000.0, prices.last())
        assertEquals(1000.0, Rarity.legendary.fishPrice)
        assertTrue(FishKind.all.all { it.value <= 1000 })
        val multipliers = Rarity.entries.map { it.xpMultiplier }
        assertEquals(multipliers.sorted(), multipliers)
        assertEquals(5, multipliers.toSet().size)
    }

    @Test fun `everyFishHasARarityAndEveryRarityHasFishAndOneSpawnRatePerLevelBand`() {
        assertEquals(Rarity.entries.toSet(), FishKind.all.map { it.rarity }.toSet())
        for (level in 0..30 step 5) {
            val rates = Rarity.entries.map { it.spawnRate(level) }
            assertTrue(rates.all { it > 0 })
            assertTrue(rates == rates.sortedDescending() || level >= 25)
        }
    }

    @Test fun `ratesStepEveryFiveLevelsAndStopChangingAtThirty`() {
        for (rarity in Rarity.entries) {
            assertEquals(rarity.spawnRate(5), rarity.spawnRate(9))
            // The original LevelRules.maxLevel comparison is blocked; check the table clamp directly.
            assertEquals(rarity.spawnRate(30), rarity.spawnRate(Int.MAX_VALUE))
            assertEquals(rarity.spawnRate(-3), rarity.spawnRate(0))
        }
    }

    @Test fun `rarerFishBecomeLikelierAndCommonerFishUnlikelierAsTheLevelRisesToThirty`() {
        fun share(
            rarity: Rarity,
            level: Int,
        ) = rarity.spawnRate(level) / Rarity.entries.sumOf { it.spawnRate(level) }
        for (level in 0 until 30 step 5) {
            assertTrue(share(Rarity.common, level + 5) < share(Rarity.common, level))
            for (rarity in listOf(Rarity.rare, Rarity.epic, Rarity.legendary)) {
                assertTrue(share(rarity, level + 5) > share(rarity, level))
            }
        }
        assertTrue(share(Rarity.common, 30) < 0.2)
    }

    @Test fun `rarityUnlocksAtLevels10_15And20`() {
        assertEquals(listOf(1, 10, 10, 15, 20), Rarity.entries.map { it.fishUnlockLevel })
        for (level in listOf(1, 9)) assertEquals(listOf(Rarity.common), Rarity.spawning(level))
        for (level in listOf(
            10,
            14,
        )) {
            assertEquals(listOf(Rarity.common, Rarity.uncommon, Rarity.rare), Rarity.spawning(level))
        }
        for (level in listOf(
            15,
            19,
        )) {
            assertEquals(listOf(Rarity.common, Rarity.uncommon, Rarity.rare, Rarity.epic), Rarity.spawning(level))
        }
        assertEquals(Rarity.entries, Rarity.spawning(20))
    }

    @Test fun `fishOnlyComeFromTheRaritiesUnlockedAtTheLevelByTheirRates`() {
        val generator = SeededRandom(90)
        for (level in listOf(1, 9, 10, 14, 15, 19, 20, 25, 30)) {
            val unlocked = Rarity.spawning(level)
            val kinds = List(20_000) { FishKind.random(level, generator) }
            assertTrue(kinds.all { it.rarity in unlocked })
            val total = unlocked.sumOf { it.spawnRate(level) }
            for (rarity in unlocked) {
                val share = kinds.count { it.rarity == rarity }.toDouble() / 20_000
                assertTrue(abs(share - rarity.spawnRate(level) / total) < 0.02)
            }
        }
    }

    @Test fun `speciesOfOneRarityAreEquallyLikely`() {
        val generator = SeededRandom(91)
        val counts = List(30_000) { FishKind.random(1, generator) }.groupingBy { it.id }.eachCount()
        val species = FishKind.all.filter { it.rarity == Rarity.common }
        val expected = 30_000 / species.size
        assertTrue(counts.size == species.size && counts.values.all { abs(it - expected) < expected / 4 })
    }

    @Test fun `weightPercentilesFollowTheActualMiddleWeightedDistribution`() {
        for (kind in FishKind.all) {
            val low = kind.weightRange.start
            val span = kind.weightRange.endInclusive - low
            for ((fraction, expected) in listOf(0.0 to 0.0, 0.25 to 0.125, 0.5 to 0.5, 0.75 to 0.875, 1.0 to 1.0)) {
                assertTrue(abs(kind.weightPercentile(low + fraction * span) - expected) < 0.000_001)
            }
            assertEquals(0.0, kind.weightPercentile(low - 10))
            assertEquals(1.0, kind.weightPercentile(kind.weightRange.endInclusive + 10))
            assertEquals(Rarity.entries, listOf(0.1, 0.6, 0.7, 0.85, 0.95).map { kind.weightRarity(low + it * span) })
        }
    }
}
