package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.rules.FarmlandRules
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
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

    @Test fun `every addition can be planted harvested and saved`() {
        val now = Instant.ofEpochSecond(40_000_000)
        val tile = GridPoint(24, 24)
        for (crop in Crop.additional) {
            val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP, logs = 0))
            engine.select(crop)
            assertEquals(listOf<GameEvent>(GameEvent.Planted(tile, crop.seedPrice)), engine.tap(tile, now))
            assertEquals(crop.id, GameStateJson.decode(GameStateJson.encode(engine.state)).plots[tile]?.cropID)
            engine.select(HarvestTool.scythe)
            assertEquals(
                listOf<GameEvent>(GameEvent.Stored(tile, crop.id, 0)),
                engine.useTool(tile, now.advancedBy(crop.growDuration + 1)),
            )
            assertEquals(mapOf(crop.id to 1), engine.state.storage)
            assertEquals(mapOf(crop.id to 1), GameStateJson.decode(GameStateJson.encode(engine.state)).storage)
        }
    }
}
