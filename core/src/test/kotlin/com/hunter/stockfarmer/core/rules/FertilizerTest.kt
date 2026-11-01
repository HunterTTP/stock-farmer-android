package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

class FertilizerTest {
    private val now = Instant.ofEpochSecond(7_000_000)
    private val tile = GridPoint(24, 24)

    private fun engine(money: Double = 100.0): GameEngine {
        val engine = GameEngine(stateWith(money = money, mode = PlayMode.fertilize))
        engine.selectFertilizer()
        return engine
    }

    @Test
    fun `fertilizing costs ten dollars per tile`() {
        val engine = engine()
        assertEquals(listOf<GameEvent>(GameEvent.Fertilized(tile, 10.0)), engine.tap(tile, now))
        assertEquals(90.0, engine.state.money)
        assertEquals(setOf(tile), engine.state.fertilized)
    }

    @Test
    fun `fertilizer only goes on empty unfertilized farmland`() {
        val engine = engine()
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedFarmland)), engine.tap(GridPoint(0, 0), now))
        engine.tap(tile, now)
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.AlreadyFertilized)), engine.tap(tile, now))
    }

    @Test
    fun `fertilizing needs money`() {
        val engine = engine(money = 9.0)
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedMoney(10.0))), engine.tap(tile, now))
        assertTrue(engine.state.fertilized.isEmpty())
    }

    @Test
    fun `fertilized crops grow ten percent faster`() {
        val engine = engine()
        engine.tap(tile, now)
        engine.state.mode = PlayMode.plant
        engine.tap(tile, now)
        val plain = Crop.starter.growDuration
        val remaining = engine.remainingGrowTime(tile, now)
        assertTrue(abs((remaining ?: 0.0) - plain / 1.1) < 1e-6)
    }

    @Test
    fun `harvesting uses the fertilizer up`() {
        val engine = engine()
        engine.tap(tile, now)
        engine.state.mode = PlayMode.plant
        engine.tap(tile, now)
        val ready = now.advancedBy(Crop.starter.growDuration)
        engine.select(HarvestTool.scythe)
        engine.tap(tile, ready)
        assertNull(engine.state.plots[tile])
        assertTrue(engine.state.fertilized.isEmpty())
    }

    @Test
    fun `destroying a crop leaves the fertilizer for the next one`() {
        val engine = engine()
        engine.tap(tile, now)
        engine.state.mode = PlayMode.plant
        engine.tap(tile, now)
        engine.destroyCrops(listOf(tile))
        assertEquals(setOf(tile), engine.state.fertilized)
        assertEquals(tile, engine.fertilizerToDestroy(tile))
    }

    @Test
    fun `fertilizer can be destroyed only while bare`() {
        val engine = engine()
        engine.tap(tile, now)
        engine.state.plots[tile] = Plot(Crop.starter.id, now)
        assertTrue(engine.destroyFertilizer(tile).isEmpty())
        engine.state.plots.remove(tile)
        val money = engine.state.money
        assertEquals(listOf<GameEvent>(GameEvent.Destroyed(1)), engine.destroyFertilizer(tile))
        assertTrue(engine.state.fertilized.isEmpty())
        assertEquals(money, engine.state.money)
    }

    @Test
    fun `clearing the farmland uses it up`() {
        val engine = engine()
        engine.tap(tile, now)
        engine.state.selectedLandscape = LandscapeTool.grass
        engine.state.mode = PlayMode.landscape
        engine.tap(tile, now)
        assertTrue(engine.state.fertilized.isEmpty())
    }

    @Test
    fun `a finished crop needs the scythe even while fertilizing`() {
        val engine = engine()
        engine.state.mode = PlayMode.plant
        engine.tap(tile, now)
        engine.state.mode = PlayMode.fertilize
        val ripe = now.advancedBy(Crop.starter.growDuration)
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.UseTheScythe)), engine.tap(tile, ripe))
        engine.select(HarvestTool.scythe)
        assertTrue(engine.tap(tile, ripe).any { it is GameEvent.Stored })
    }
}
