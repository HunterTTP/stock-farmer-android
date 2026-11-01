package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.TractorRules
import com.hunter.stockfarmer.core.model.tractorOccupiedTiles
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class TractorDrivingTest {
    private val now = Instant.ofEpochSecond(40_000_000)
    private val home = GridPoint(10, 10)
    private val near = GridPoint(10, 15)
    private val far = GridPoint(10, 20)

    private fun reject(rejection: Rejection) = listOf<GameEvent>(GameEvent.Rejected(rejection))

    private fun engine(crops: List<GridPoint> = listOf(near, far)): GameEngine {
        val state = GameState()
        state.putBuilding(home, "tractor1")
        for (tile in crops) {
            state.farmland.add(tile)
            state.plots[tile] = Plot("wheat", now.advancedBy(-100.0))
        }
        return GameEngine(state)
    }

    private fun finish(
        engine: GameEngine,
        limit: Int = 300,
    ): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        var steps = 0
        while (engine.state.tractorRun != null && steps++ < limit) {
            val step = engine.advanceTractor(TractorRules.SECONDS_PER_TILE, true, now)
            assertTrue(step.count { it is GameEvent.TractorGathered } <= 1)
            events += step
        }
        assertNull(engine.state.tractorRun)
        return events
    }

    @Test
    fun `only one tractor can be built but the existing one can move`() {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, logs = 100_000))
        val tractor = Building.named("tractor1")!!
        assertEquals(1, tractor.placementLimit)
        engine.select(tractor)
        assertFalse(engine.build(home, now).containsRejection)
        assertTrue(
            engine.hasReachedPlacementLimit(
                com.hunter.stockfarmer.core.model.ShopItem
                    .BuildingItem(tractor.id),
            ),
        )
        assertEquals(reject(Rejection.BuildingLimit("Tractor", 1)), engine.build(far, now))
        engine.beginMoving(engine.state.placedBuildings.first())
        engine.movePlacement(GridPoint(30, 30))
        assertFalse(engine.confirmPlacement(now).containsRejection)
        assertEquals(1, engine.state.buildings.size)
    }

    private fun assertFalse(value: Boolean) =
        org.junit.jupiter.api.Assertions
            .assertFalse(value)

    @Test
    fun `harvests the ripe plants of a crop and leaves the growing ones alone`() {
        val engine = engine()
        engine.state.plots[far] = Plot("wheat", now)
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        assertEquals(setOf(near), engine.state.tractorRun?.remaining)
        finish(engine)
        assertTrue(engine.state.plots[near] == null && engine.state.plots[far]?.plantedAt == now)
        assertEquals(mapOf("wheat" to 1), engine.state.tractorCargo[home])
    }

    @Test
    fun `refuses a crop that has nothing ripe or cannot be harvested by the tractor`() {
        val engine = engine()
        engine.state.plots[near] = Plot("wheat", now)
        engine.state.plots[far] = Plot("wheat", now)
        val before = engine.state.copy()
        assertEquals(reject(Rejection.TractorNeedsRipeCrops), engine.startTractorHarvest(home, "wheat", now))
        assertTrue(sameState(before, engine.state))
        assertEquals(reject(Rejection.TractorCannotHarvestFruitTrees), engine.startTractorHarvest(home, "apple", now))
        assertEquals(reject(Rejection.NothingToHarvest), engine.startTractorHarvest(home, "carrot", now))
    }

    @Test
    fun `drives slowly to nearest crops harvests only the chosen type and returns home`() {
        val engine = engine()
        val carrot = GridPoint(11, 15)
        engine.state.plots[carrot] = Plot("carrot", now.advancedBy(-10_000.0))
        engine.state.mode = PlayMode.landscape
        engine.state.harvestTool = HarvestTool.bucket
        engine.setSelling(true, HarvestTool.scythe)
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        assertTrue(engine.state.storage.isEmpty() && engine.state.plots[near] != null)
        assertEquals(GridPoint(10, 12), engine.state.tractorRun?.nextTile)
        assertTrue(engine.advanceTractor(TractorRules.SECONDS_PER_TILE / 2, true, now).isEmpty())
        val halfway = engine.state.tractorRun!!
        assertTrue(halfway.position == GridPoint(10, 11) && halfway.progress == 0.5)
        assertNotNull(engine.state.plots[near])
        val events = finish(engine)
        assertEquals(2, events.count { it is GameEvent.TractorGathered })
        assertTrue(engine.state.storage.isEmpty() && engine.state.tractorCargo[home] == mapOf("wheat" to 2))
        assertTrue(engine.state.plots.size == 1 && engine.state.plots[carrot] != null)
        assertEquals("tractor1", engine.state.buildings[home])
        assertTrue(engine.state.mode == PlayMode.landscape && engine.state.harvestTool == HarvestTool.bucket)
        assertTrue(engine.isSelling(HarvestTool.scythe) && engine.state.xp == 0)
    }

    @Test
    fun `no reachable crop cancels immediately without harvesting`() {
        val engine = engine(listOf(far))
        for (row in 0 until GameState.GRID_SIZE) engine.state.water[GridPoint(row, 13)] = WaterStyle.deep
        val before = engine.state.copy()
        assertEquals(reject(Rejection.TractorCannotReachCrops), engine.startTractorHarvest(home, "wheat", now))
        assertTrue(sameState(before, engine.state))
    }

    @Test
    fun `blocked closest crop is skipped for another reachable crop then returns home`() {
        val north = GridPoint(2, 13)
        val engine = engine(listOf(near, far, north))
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        repeat(4) { engine.advanceTractor(TractorRules.SECONDS_PER_TILE, true, now) }
        assertNull(engine.state.plots[near])
        for (row in 0 until GameState.GRID_SIZE) engine.state.water[GridPoint(row, 16)] = WaterStyle.deep
        val events = finish(engine)
        assertTrue(engine.state.plots[north] == null && engine.state.plots[far] != null)
        assertEquals(1, events.count { it == GameEvent.Rejected(Rejection.TractorCannotReachCrops) })
        assertEquals("tractor1", engine.state.buildings[home])
    }

    @Test
    fun `blocked home stops at current location and makes that the new home`() {
        val engine = engine(listOf(near))
        engine.state.putBuilding(home, "tractor1", "left")
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        repeat(5) { engine.advanceTractor(TractorRules.SECONDS_PER_TILE, true, now) }
        val run = engine.state.tractorRun!!
        val stopped = TractorRules.origin(run.position, run.facing)
        assertTrue(stopped != home)
        engine.state.rocks[home] = RockSize.small
        val events = finish(engine)
        assertTrue(GameEvent.Rejected(Rejection.TractorCannotReturnHome) in events)
        assertTrue(engine.state.buildings[home] == null && engine.state.buildings[stopped] == "tractor1")
        assertEquals(1, engine.state.buildings.size)
        assertEquals(
            Building.named("tractor1")?.styled(run.facing.styleID),
            engine.building(stopped)?.building,
        )
        assertTrue(engine.state.tractorCargo[stopped] == mapOf("wheat" to 1) && engine.state.tractorCargo[home] == null)
        assertTrue(engine.advanceTractor(100.0, true, now).isEmpty())
    }

    @Test
    fun `animals never block a crop and are moved out of the tractors way`() {
        val engine = engine(listOf(near))
        engine.state.pets[far] = Pet(breed = Cat.starter.id, name = "Cat", position = near)
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        var steps = 0
        while (engine.state.tractorRun != null && steps++ < 300) {
            val events = engine.advanceTractor(TractorRules.SECONDS_PER_TILE, true, now)
            assertFalse(events.containsRejection)
            engine.state.pets[far]?.let { assertFalse(it.position in engine.state.tractorOccupiedTiles) }
        }
        assertTrue(engine.state.tractorRun == null && engine.state.plots[near] == null)
    }

    @Test
    fun `a pet walking onto a reserved tile is refused`() {
        val engine = engine(listOf(near))
        engine.state.pets[far] = Pet(breed = Cat.starter.id, name = "Cat", position = GridPoint(20, 20))
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        val reserved = engine.state.tractorRun!!.nextTile!!
        engine.state.pets[far] = engine.state.pets[far]!!.copy(position = GridPoint(reserved.row + 1, reserved.col))
        assertNull(engine.move(AnimalRef.Pet(far), GridPoint(-1, 0)))
    }

    @Test
    fun `a crop in front of a pet house is harvested wherever the pet stands`() {
        val house = GridPoint(10, 16)
        val front = Building.named("petHouse")!!.frontTile(house)
        val engine = engine(listOf(front))
        engine.state.putBuilding(house, "petHouse")
        engine.state.pets[house] = Pet(breed = Cat.starter.id, name = "Cat", position = front)
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        finish(engine)
        assertTrue(engine.state.plots[front] == null && engine.state.tractorCargo[home] == mapOf("wheat" to 1))
    }

    @Test
    fun `a crop walled in on every side is the only one left behind`() {
        val engine = engine(listOf(near, far))
        for (tile in far.neighbors) engine.state.fences[tile] = FenceColor.starter.id
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        val events = finish(engine)
        assertEquals(1, events.count { it == GameEvent.Rejected(Rejection.TractorCannotReachCrops) })
        assertTrue(engine.state.plots[near] == null && engine.state.plots[far] != null)
    }

    @Test
    fun `each connected plot is finished before the next one is started`() {
        val rows = 20..24
        val first = rows.flatMap { row -> (14..18).map { GridPoint(row, it) } }
        val second = rows.flatMap { row -> (20..24).map { GridPoint(row, it) } }
        val engine = engine(first + second)
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        val gathered = finish(engine).filterIsInstance<GameEvent.TractorGathered>().map { it.point }
        assertEquals(50, gathered.size)
        val firstPlot = first.toSet()
        val secondPlot = second.toSet()
        val starts = gathered.take(25)
        val rest = gathered.takeLast(25)
        assertTrue(starts.all { it in firstPlot } || starts.all { it in secondPlot })
        assertTrue(rest.all { it in firstPlot } || rest.all { it in secondPlot })
    }

    @Test
    fun `inactive time does not move or harvest and trip round trips without offline progress`() {
        var engine = engine()
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        engine.advanceTractor(TractorRules.SECONDS_PER_TILE / 2, true, now)
        val paused = engine.state.copy()
        assertTrue(engine.advanceTractor(86_400.0, false, now.advancedBy(86_400.0)).isEmpty())
        assertTrue(sameState(paused, engine.state))
        val restored = GameStateJson.decode(GameStateJson.encode(paused))
        assertTrue(sameState(restored, paused))
        engine = GameEngine(restored)
        engine.reopen(now.advancedBy(86_400.0), SeededRandom(12))
        assertEquals(paused.tractorRun, engine.state.tractorRun)
    }

    @Test
    fun `a running tractor cannot start another job or be moved or demolished`() {
        val engine = engine()
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        assertEquals(reject(Rejection.TractorBusy), engine.startTractorHarvest(home, "wheat", now))
        val placed = engine.state.placedBuildings.first()
        assertEquals(reject(Rejection.TractorBusy), engine.demolish(placed))
        engine.beginMoving(placed)
        assertFalse(engine.isPlacingBuilding)
    }

    @Test
    fun `chickens and livestock never block a crop either`() {
        val engine = engine(listOf(near))
        val barn = GridPoint(30, 30)
        engine.state.putBuilding(barn, "chickenCoop")
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        finish(engine)
        assertNull(engine.state.plots[near])
    }

    @Test
    fun `cancelling before departure stops without collecting anything`() {
        val engine = engine()
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        assertTrue(engine.cancelTractorHarvest(home).isEmpty())
        finish(engine)
        assertTrue(engine.state.tractorCargo[home] == null && engine.state.plots[near] != null)
    }

    @Test
    fun `cancelling heads home immediately and keeps only already gathered cargo`() {
        val engine = engine()
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        var steps = 0
        while (engine.state.tractorCargo[home] == null && steps++ < 300) {
            engine.advanceTractor(TractorRules.SECONDS_PER_TILE, true, now)
        }
        assertTrue(engine.cancelTractorHarvest(home).isEmpty())
        finish(engine)
        assertEquals(mapOf("wheat" to 1), engine.state.tractorCargo[home])
        assertTrue(engine.state.plots[far] != null)
    }
}
