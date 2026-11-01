package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class WholeFieldGrownTest {
    private val planted = Instant.ofEpochSecond(9_000_000)
    private val ready get() = planted.advancedBy(Crop.starter.growDuration)

    private val startingTiles = GameState.startingFarmland.sortedWith(compareBy({ it.row }, { it.col }))

    private fun engine(planting: Int = 4): GameEngine {
        val engine = GameEngine()
        for (point in startingTiles.take(planting)) engine.tap(point, planted)
        return engine
    }

    @Test
    fun `a fully planted field is grown only once every crop is`() {
        val engine = engine()
        assertFalse(engine.isWholeFieldGrown(planted.advancedBy(5.0)))
        assertTrue(engine.isWholeFieldGrown(ready))
    }

    @Test
    fun `an unplanted farmland tile keeps it false`() {
        assertFalse(engine(planting = 3).isWholeFieldGrown(ready))
        val engine = engine()
        engine.state.xp = LevelRules.xpToReach(2)
        engine.state.farmland.add(GridPoint(30, 30))
        assertFalse(engine.isWholeFieldGrown(ready))
    }

    @Test
    fun `small fields count too`() {
        val engine = engine(planting = 3)
        engine.state.farmland =
            engine.state.plots.keys
                .toMutableSet()
        assertEquals(3, engine.state.farmland.size)
        assertTrue(engine.isWholeFieldGrown(ready))
    }

    @Test
    fun `a single tile field counts`() {
        val engine = engine(planting = 1)
        engine.state.farmland =
            engine.state.plots.keys
                .toMutableSet()
        assertTrue(engine.isWholeFieldGrown(ready))
    }

    @Test
    fun `a field with no farmland or no crops never counts`() {
        val empty = GameEngine()
        empty.state.farmland = mutableSetOf()
        assertFalse(empty.isWholeFieldGrown(ready))
        assertFalse(GameEngine().isWholeFieldGrown(ready))
    }

    @Test
    fun `one crop still growing keeps it false`() {
        val engine = engine()
        engine.state.farmland.add(GridPoint(30, 30))
        engine.state.plots[GridPoint(30, 30)] = Plot(Crop.starter.id, ready)
        assertFalse(engine.isWholeFieldGrown(ready))
        assertTrue(engine.isWholeFieldGrown(ready.advancedBy(Crop.starter.growDuration)))
    }

    @Test
    fun `harvesting a tile turns it off`() {
        val engine = engine()
        assertTrue(engine.isWholeFieldGrown(ready))
        engine.select(HarvestTool.scythe)
        engine.tap(GridPoint(24, 24), ready)
        assertFalse(engine.isWholeFieldGrown(ready))
    }

    @Test
    fun `the ready date is when the last crop finishes`() {
        val engine = engine(planting = 3)
        assertNull(engine.wholeFieldReadyDate())
        engine.tap(startingTiles[3], planted.advancedBy(10.0))
        val date = engine.wholeFieldReadyDate()
        assertEquals(4, engine.state.plots.size)
        assertNotNull(date)
        assertFalse(engine.isWholeFieldGrown(date!!.advancedBy(-1.0)))
        assertTrue(engine.isWholeFieldGrown(date))
    }

    @Test
    fun `all crops includes fruit trees on grass`() {
        val engine = engine()
        val apple = Crop.named("apple")!!
        engine.state.xp = lateGameXP
        engine.state.money = 100.0
        engine.select(apple)
        engine.beginPlacingFruitTree(GridPoint(10, 10))
        val events = engine.confirmPlacement(planted)
        assertEquals(listOf<GameEvent>(GameEvent.Planted(GridPoint(9, 10), apple.seedPrice)), events)
        assertFalse(engine.isWholeFieldGrown(ready))
        val allReady = planted.advancedBy(apple.growDuration)
        assertEquals(allReady, engine.wholeFieldReadyDate())
        assertTrue(engine.isWholeFieldGrown(allReady))
    }
}
