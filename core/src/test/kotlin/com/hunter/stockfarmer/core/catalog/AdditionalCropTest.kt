package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.rules.FarmlandRules
import com.hunter.stockfarmer.core.rules.LevelRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class AdditionalCropTest {
    @Test
    fun `new crops unlock after blackberry with the same time and increasing profit`() {
        val blackberry = Crop.named("blackberry")!!
        assertEquals(34, Crop.all.size)
        assertEquals(34, LevelRules.maxLevel)
        assertEquals(24, blackberry.unlockLevel)
        assertEquals(0.32, Crop.named("carrot")?.seedPrice)
        assertEquals(73.19, blackberry.seedPrice)
        var previousProfit = blackberry.sellValue - blackberry.seedPrice
        for ((index, crop) in Crop.additional.withIndex()) {
            assertEquals(25 + index, crop.unlockLevel)
            assertEquals(blackberry.growDuration, crop.growDuration)
            val profit = crop.sellValue - crop.seedPrice
            assertTrue(abs(profit / previousProfit - 1.025) < 0.001)
            assertEquals(crop.unlockLevel + 1, LevelRules.harvestXP(crop))
            assertEquals(
                3 * FarmlandRules.tilesUnlocked(crop.unlockLevel) * LevelRules.harvestXP(crop),
                LevelRules.xpToLeave(crop.unlockLevel),
            )
            assertTrue(crop.harvestTool == HarvestTool.scythe && !crop.isFruitTree)
            previousProfit = profit
        }
    }
}
