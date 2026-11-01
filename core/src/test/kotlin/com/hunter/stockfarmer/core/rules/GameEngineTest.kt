package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
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

class GameEngineTest {
    private val start = Instant.ofEpochSecond(1_000_000)
    private val farmlandPoint = GridPoint(24, 24)

    @Test
    fun `planting requires farmland`() {
        val engine = GameEngine()
        val events = engine.tap(GridPoint(0, 0), start)
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedFarmland)), events)
        assertTrue(engine.state.plots.isEmpty())
    }

    @Test
    fun `plants selected crop on farmland`() {
        val engine = GameEngine()
        val events = engine.tap(farmlandPoint, start)
        assertEquals(listOf<GameEvent>(GameEvent.Planted(farmlandPoint, 0.0)), events)
        assertEquals(Plot("wheat", start), engine.state.plots[farmlandPoint])
    }

    @Test
    fun `cannot harvest before grown`() {
        val engine = GameEngine()
        engine.tap(farmlandPoint, start)
        val events = engine.tap(farmlandPoint, start.advancedBy(29.0))
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.NotFinishedGrowing("Wheat", 1.0))), events)
        assertEquals(0.0, engine.state.money)
    }

    @Test
    fun `harvest stores the crop and clears plot`() {
        val engine = GameEngine()
        engine.tap(farmlandPoint, start)
        engine.select(HarvestTool.scythe)
        val events = engine.tap(farmlandPoint, start.advancedBy(30.0))
        assertEquals(listOf<GameEvent>(GameEvent.Stored(farmlandPoint, Crop.starter.id, 2)), events)
        assertEquals(1, engine.state.storage[Crop.starter.id])
        assertEquals(0.0, engine.state.money)
        assertNull(engine.state.plots[farmlandPoint])
    }

    @Test
    fun `tap outside grid is rejected`() {
        val engine = GameEngine()
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.OutOfBounds)), engine.tap(GridPoint(-1, 3), start))
    }

    private fun carrotEngine(money: Double): GameEngine {
        val engine = GameEngine(stateWith(money = money, xp = LevelRules.xpToReach(2)))
        engine.select(Crop.named("carrot")!!)
        return engine
    }

    @Test
    fun `planting charges the seed price per tile`() {
        val engine = carrotEngine(100.0)
        val price = Crop.named("carrot")!!.seedPrice
        assertTrue(price > 0)
        assertEquals(listOf<GameEvent>(GameEvent.Planted(farmlandPoint, price)), engine.tap(farmlandPoint, start))
        assertEquals(100 - price, engine.state.money)
    }

    @Test
    fun `planting without enough money is rejected`() {
        val engine = carrotEngine(0.0)
        val price = Crop.named("carrot")!!.seedPrice
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedMoney(price))),
            engine.tap(farmlandPoint, start),
        )
        assertTrue(engine.state.plots.isEmpty())
    }

    @Test
    fun `harvesting charges nothing and earns no money until sold`() {
        val engine = GameEngine()
        engine.tap(farmlandPoint, start)
        engine.select(HarvestTool.scythe)
        engine.tap(farmlandPoint, start.advancedBy(30.0))
        assertEquals(0.0, engine.state.money)
        engine.sellStored(StorageItem.CropItem(Crop.starter), 1)
        assertEquals(0.3, engine.state.money)
    }

    @Test
    fun `money never grows past the maximum`() {
        val engine = GameEngine(stateWith(money = GameState.MAX_MONEY - 1))
        engine.credit(100.0)
        assertEquals(GameState.MAX_MONEY, engine.state.money)
    }

    @Test
    fun `not finished message names the crop and remaining time`() {
        val message = Rejection.NotFinishedGrowing("Carrot", 65.0).message
        assertEquals("Carrot is not finished growing\nGrow time remaining: 1 min 5 sec", message)
    }

    @Test
    fun `remaining grow time is null without a crop and counts down with one`() {
        val engine = GameEngine()
        assertNull(engine.remainingGrowTime(farmlandPoint, start))
        engine.tap(farmlandPoint, start)
        assertEquals(20.0, engine.remainingGrowTime(farmlandPoint, start.advancedBy(10.0)))
    }

    @Test
    fun `harvesting keeps the current selection`() {
        val engine = GameEngine(stateWith(xp = LevelRules.xpToReach(2)))
        engine.tap(farmlandPoint, start)
        engine.select(Crop.named("carrot")!!)
        engine.select(HarvestTool.scythe)
        val xp = engine.state.xp
        val events = engine.tap(farmlandPoint, start.advancedBy(30.0))
        assertTrue(GameEvent.Stored(farmlandPoint, "wheat", LevelRules.harvestXP(Crop.starter)) in events)
        assertNull(engine.state.plots[farmlandPoint])
        assertEquals(1, engine.state.storage["wheat"])
        assertEquals(xp + LevelRules.harvestXP(Crop.starter), engine.state.xp)
        assertEquals(PlayMode.harvest, engine.state.mode)
        assertEquals("carrot", engine.state.selectedCropID)
    }

    @Test fun `harvests produce ordinary crops without the retired quality bonus`() {
        val now = Instant.ofEpochSecond(40_000_000)
        val engine = GameEngine(stateWith(money = 0.0, xp = lateGameXP, logs = 0))
        val point = GridPoint(15, 15)
        engine.state.farmland.add(point)
        engine.select(HarvestTool.scythe)
        repeat(50) { trial ->
            engine.state.plots[point] = Plot("wheat", now.advancedBy(-10_000.0 - trial))
            engine.useTool(point, now)
        }
        assertEquals(mapOf("wheat" to 50), engine.state.storage)
    }
}
