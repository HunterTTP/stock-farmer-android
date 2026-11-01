package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Instant

class GrowingCropBlocksTest {
    private val planted = Instant.ofEpochSecond(11_000_000)
    private val tile = GridPoint(24, 24)
    private val halfway get() = planted.advancedBy(15.0)

    private fun fieldWithGrowingCrop(mode: PlayMode): GameEngine {
        val engine = GameEngine(stateWith(money = 100.0, xp = LevelRules.xpToReach(3)))
        engine.tap(tile, planted)
        engine.state.mode = mode
        return engine
    }

    /** What tapping a growing crop with a seed says; every other action must say the same. */
    private val plantModeRejection: Rejection
        get() {
            val engine = fieldWithGrowingCrop(PlayMode.plant)
            val first = engine.tap(tile, halfway).first()
            return (first as? GameEvent.Rejected)?.rejection ?: Rejection.OutOfBounds
        }

    @Test
    fun `planting on a growing crop says it is not finished and shows the time left`() {
        assertEquals(Rejection.NotFinishedGrowing("Wheat", 15.0), plantModeRejection)
        assertEquals("Wheat is not finished growing\nGrow time remaining: 15 sec", plantModeRejection.message)
    }

    @Test
    fun `every landscape tool gives the same message`() {
        for (tool in listOf(LandscapeTool.farmland, LandscapeTool.water, LandscapeTool.grass)) {
            val engine = fieldWithGrowingCrop(PlayMode.landscape)
            engine.select(tool)
            assertEquals(listOf<GameEvent>(GameEvent.Rejected(plantModeRejection)), engine.tap(tile, halfway))
            assertNotNull(engine.state.plots[tile])
        }
    }

    @Test
    fun `a building over a growing crop gives the same message`() {
        val engine = fieldWithGrowingCrop(PlayMode.build)
        engine.state.selectedBuildingID = "chickenCoop"
        engine.state.money = 1_000_000.0
        engine.state.xp = lateGameXP
        // chickenCoop is 1x2, so tapping the tile above covers the crop with its second row.
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(plantModeRejection)),
            engine.build(GridPoint(23, 24), halfway),
        )
        assertEquals(1_000_000.0, engine.state.money)
    }

    @Test
    fun `a finished crop in the footprint asks to harvest first`() {
        val engine = fieldWithGrowingCrop(PlayMode.build)
        engine.state.selectedBuildingID = "chickenCoop"
        engine.state.money = 1_000_000.0
        engine.state.xp = lateGameXP
        val finished = planted.advancedBy(Crop.starter.growDuration)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.CropReady)),
            engine.build(GridPoint(23, 24), finished),
        )
        assertEquals("A crop here is ready to harvest", Rejection.CropReady.message)
    }
}
