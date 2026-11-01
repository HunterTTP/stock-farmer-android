package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.model.roundedToCents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class CropEconomyTest {
    @Test
    fun `wheat keeps its original stats`() {
        assertEquals(0.0, Crop.starter.seedPrice)
        assertEquals(0.3, Crop.starter.sellValue)
        assertEquals(30.0, Crop.starter.growDuration)
    }

    @Test
    fun `carrots take a minute`() {
        val carrot = Crop.named("carrot")!!
        assertEquals(60.0, carrot.growDuration)
        assertEquals((0.6 * 1.05 / 2).roundedToCents, carrot.seedPrice)
    }

    @Test
    fun `the slowest crop takes two hours`() {
        assertTrue(abs(Crop.plainCrops.last().growDuration - 2 * 3600) < 1e-6)
    }

    @Test
    fun `harvests pay double the seed cost`() {
        for (crop in Crop.plainCrops.drop(1)) {
            assertTrue(abs(crop.sellValue - crop.seedPrice * 2) < 0.0051)
        }
    }

    @Test
    fun `grow times rise by the same multiple for every paid crop`() {
        val paid = Crop.plainCrops.drop(1).map { it.growDuration }
        val ratios = paid.zipWithNext { a, b -> b / a }
        assertTrue(ratios.all { abs(it - ratios[0]) < 1e-9 })
        assertTrue(ratios[0] > 1.3 && ratios[0] < 1.35)
    }

    @Test
    fun `every crop earns five percent more per minute than the previous one up to zucchini and less after`() {
        val zucchini = Crop.plainCrops.indexOfFirst { it.id == "zucchini" }
        for (tier in 1 until Crop.plainCrops.size) {
            val ratio = CropEconomy.earningsPerMinute(tier.toDouble()) / CropEconomy.earningsPerMinute(tier - 1.0)
            assertTrue(abs(ratio - if (tier <= zucchini) 1.05 else 1.025) < 1e-9)
        }
        assertEquals(0.6, CropEconomy.earningsPerMinute(0.0))
        // The prices are rounded to cents, so check the real thing wherever that rounding is small.
        for (crop in Crop.plainCrops.drop(4)) {
            val expected = CropEconomy.earningsPerMinute(Crop.plainCrops.indexOf(crop).toDouble())
            assertTrue(abs(crop.sellValue / crop.growMinutes / expected - 1) < 0.01)
        }
    }

    @Test
    fun `seed prices rise with every crop`() {
        val prices = Crop.plainCrops.map { it.seedPrice }
        assertEquals(prices.sorted(), prices)
        val paid = Crop.plainCrops.drop(1).map { it.sellValue }
        assertEquals(paid.sorted(), paid)
        val times = Crop.plainCrops.map { it.growDuration }
        assertEquals(times.sorted(), times)
    }

    @Test
    fun `every crop still turns a profit`() {
        assertTrue(Crop.plainCrops.all { it.sellValue > it.seedPrice })
    }
}
