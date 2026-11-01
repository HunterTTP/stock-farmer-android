package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRules
import com.hunter.stockfarmer.core.model.TractorRun
import com.hunter.stockfarmer.core.model.tractorOccupiedTiles
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class TractorDrivingObstaclesTest {
    private val now = Instant.ofEpochSecond(40_000_000)
    private val home = GridPoint(10, 10)
    private val near = GridPoint(10, 15)
    private val far = GridPoint(10, 20)
    private val seconds = TractorRules.SECONDS_PER_TILE

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

    private fun finish(engine: GameEngine): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        var steps = 0
        while (engine.state.tractorRun != null && steps++ < 300) {
            events += engine.advanceTractor(seconds, true, now)
        }
        assertNull(engine.state.tractorRun)
        return events
    }

    private fun standing(
        tile: GridPoint,
        height: Int,
    ) = GridPoint(tile.row - height + 1, tile.col)

    private fun start(engine: GameEngine) = assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())

    private fun drive(
        engine: GameEngine,
        steps: Int,
    ) = repeat(steps) { engine.advanceTractor(seconds, true, now) }

    @Test
    fun `a fruit tree blocks the ground it stands on but not the room above it`() {
        val apple = Crop.named("apple")!!
        val engine = engine(listOf(near))
        for (tile in apple.plotTiles(near)) engine.state.plots[tile] = Plot(apple.id, now, near)
        start(engine)
        finish(engine)
        assertNull(engine.state.plots[near])

        val walled = engine(listOf(far))
        val origin = GridPoint(far.row - 1, far.col - 2)
        for (tile in apple.plotTiles(origin)) walled.state.plots[tile] = Plot(apple.id, now, origin)
        for (tile in listOf(
            GridPoint(far.row - 1, far.col),
            GridPoint(far.row + 1, far.col),
            GridPoint(
                far.row,
                far.col + 1,
            ),
        )) {
            walled.state.fences[tile] = FenceColor.starter.id
        }
        assertEquals(reject(Rejection.TractorCannotReachCrops), walled.startTractorHarvest(home, "wheat", now))
    }

    @Test
    fun `every solid obstacle prevents driving over it`() {
        val petHouse = Building.named("petHouse")!!
        for (kind in listOf("water", "fence", "rock", "tree", "decoration", "building")) {
            val engine = engine(listOf(near))
            when (kind) {
                "water" -> {
                    engine.state.water[near] = WaterStyle.deep
                }

                "fence" -> {
                    engine.state.fences[near] = FenceColor.all[0].id
                }

                "rock" -> {
                    engine.state.rocks[near] = RockSize.small
                }

                "tree" -> {
                    val height =
                        Tree.starter
                            .footprint(near)
                            .map { it.row }
                            .toSet()
                            .size
                    engine.state.putTree(standing(near, height), PlacedTree(Tree.starter, TreeColor.green, now))
                }

                "decoration" -> {
                    engine.state.putDecoration(near, PlacedDecoration(Decoration.named("scarecrow")!!.id))
                }

                else -> {
                    engine.state.putBuilding(standing(near, petHouse.height), "petHouse")
                }
            }
            val before = engine.state.copy()
            assertEquals(
                reject(Rejection.TractorCannotReachCrops),
                engine.startTractorHarvest(home, "wheat", now),
                kind,
            )
            assertTrue(sameState(before, engine.state))
        }
    }

    @Test
    fun `chickens and livestock never block a crop either`() {
        for (kind in listOf("chicken", "cow")) {
            val engine = engine(listOf(near))
            if (kind == "chicken") {
                val hen = Chicken(0, ChickenVariant.all[0].id, "Hen", near, hatchedAt = now)
                engine.state.coops[far] = Coop(chickens = listOf(hen), nextChickAt = now)
            } else {
                val cow = Livestock(0, LivestockSpecies.cow.variants[0].id, "Cow", near, bornAt = now)
                engine.state.herds[far] = Herd(listOf(cow), now)
            }
            start(engine)
            finish(engine)
            assertNull(engine.state.plots[near])
        }
    }

    @Test
    fun `both crop and home failures are reported and all actions stop`() {
        val engine = engine()
        start(engine)
        drive(engine, 4)
        for (row in 0 until GameState.GRID_SIZE) engine.state.water[GridPoint(row, 16)] = WaterStyle.deep
        engine.state.rocks[home] = RockSize.small
        val events = finish(engine)
        assertEquals(1, events.count { it == GameEvent.Rejected(Rejection.TractorCannotReachCrops) })
        assertEquals(1, events.count { it == GameEvent.Rejected(Rejection.TractorCannotReturnHome) })
        assertNotNull(engine.state.plots[far])
        val parked = engine.state.copy()
        assertTrue(engine.advanceTractor(100.0, true, now).isEmpty())
        assertTrue(sameState(parked, engine.state))
    }

    @Test
    fun `a moving tractor is queryable and blocks placement`() {
        val engine = engine()
        start(engine)
        drive(engine, 3)
        val current = engine.state.tractorRun!!.position
        assertEquals(home, engine.building(current)?.origin)
        assertTrue(engine.state.tractorOccupiedTiles.all { engine.state.hasObstacle(it) })
        assertFalse(engine.isOpenGround(current))
    }

    @Test
    fun `horizontal tractor can use a single row lane of grass farmland and every path`() {
        val engine = engine(listOf(far))
        for (col in 0 until GameState.GRID_SIZE) {
            engine.state.rocks[GridPoint(9, col)] = RockSize.small
            engine.state.rocks[GridPoint(11, col)] = RockSize.small
        }
        engine.state.farmland.add(GridPoint(10, 13))
        for ((offset, path) in GroundCover.entries.withIndex()) {
            engine.state.groundCover[GridPoint(10, 14 + offset)] =
                path
        }
        start(engine)
        assertFalse(finish(engine).containsRejection)
        assertTrue(engine.state.plots[far] == null && engine.state.tractorCargo[home] == mapOf("wheat" to 1))
        assertEquals("tractor1", engine.state.buildings[home])
    }

    @Test
    fun `each arrival collects only the front crop and emits no standard harvest or level up events`() {
        val first = GridPoint(10, 12)
        val second = GridPoint(10, 13)
        val engine = engine(listOf(first, second))
        engine.state.xp = LevelRules.xpToReach(2) - 1
        start(engine)
        assertEquals(listOf<GameEvent>(GameEvent.TractorGathered(first)), engine.advanceTractor(seconds, true, now))
        assertTrue(engine.state.plots[first] == null && engine.state.plots[second] != null)
        assertTrue(engine.state.tractorCargo[home] == mapOf("wheat" to 1) && engine.state.storage.isEmpty())
        assertEquals(1, engine.state.level)
        val unloaded = engine.storeTractorCargo("wheat", home)
        assertTrue(engine.state.level == 2 && unloaded.any { it is GameEvent.LeveledUp })
    }

    @Test
    fun `cancelling before departure stops without collecting anything`() {
        val engine = engine()
        assertTrue(engine.cancelTractorHarvest(home).isEmpty())
        start(engine)
        val departing = engine.state.copy()
        assertTrue(engine.cancelTractorHarvest(far).isEmpty())
        assertTrue(sameState(departing, engine.state))
        assertTrue(engine.cancelTractorHarvest(home).isEmpty())
        finish(engine)
        assertTrue(engine.state.tractorRun == null && engine.state.tractorCargo.isEmpty())
        assertTrue(engine.state.plots.size == 2 && engine.state.xp == 0)
    }

    @Test
    fun `cancelling heads home immediately and keeps only already gathered cargo`() {
        val engine = engine()
        start(engine)
        drive(engine, 4)
        assertEquals(mapOf("wheat" to 1), engine.state.tractorCargo[home])
        assertTrue(engine.cancelTractorHarvest(home).isEmpty())
        val returning = engine.state.tractorRun!!
        assertTrue(returning.returning && returning.remaining.isEmpty())
        assertEquals(GridPoint(near.row, near.col - 1), returning.nextTile)
        assertTrue(finish(engine).isEmpty())
        assertTrue(engine.state.plots[far] != null && engine.state.tractorCargo[home] == mapOf("wheat" to 1))
        assertEquals(0, engine.state.xp)
        val xp = LevelRules.harvestXP(Crop.starter)
        assertEquals(
            listOf<GameEvent>(GameEvent.TractorCargoStored("wheat", 1, null, xp)),
            engine.storeTractorCargo("wheat", home),
        )
        assertEquals(xp, engine.state.xp)
    }

    @Test
    fun `cancelling mid step reverses without losing movement progress and survives saving`() {
        var engine = engine()
        start(engine)
        drive(engine, 4)
        engine.advanceTractor(seconds / 4, true, now)
        val outbound = engine.state.tractorRun!!
        assertTrue(engine.cancelTractorHarvest(home).isEmpty())
        val returning = engine.state.tractorRun!!
        assertTrue(
            returning.returning && returning.position == outbound.nextTile && returning.nextTile == outbound.position,
        )
        assertEquals(1 - outbound.progress, returning.progress, 1e-12)
        val saved = engine.state.copy()
        assertTrue(engine.cancelTractorHarvest(home).isEmpty() && sameState(saved, engine.state))
        engine = GameEngine(GameStateJson.decode(GameStateJson.encode(saved)))
        assertTrue(engine.advanceTractor(100.0, false, now).isEmpty() && sameState(saved, engine.state))
        assertTrue(finish(engine).isEmpty())
        assertTrue(engine.state.plots[far] != null && engine.state.tractorCargo[home] == mapOf("wheat" to 1))
    }

    @Test
    fun `cancelling with a blocked home or route parks here and moves cargo to the new home`() {
        for (blocksHome in listOf(false, true)) {
            val engine = engine()
            start(engine)
            drive(engine, 4)
            val run = engine.state.tractorRun!!
            val parked = TractorRules.origin(run.position, run.facing)
            if (blocksHome) {
                engine.state.rocks[home] = RockSize.small
            } else {
                for (row in 0 until GameState.GRID_SIZE) engine.state.water[GridPoint(row, 13)] = WaterStyle.deep
            }
            assertEquals(reject(Rejection.TractorCannotReturnHome), engine.cancelTractorHarvest(home))
            assertTrue(engine.state.tractorRun == null && engine.state.buildings[home] == null)
            assertEquals("tractor1", engine.state.buildings[parked])
            assertTrue(
                engine.state.tractorCargo[parked] == mapOf("wheat" to 1) && engine.state.tractorCargo[home] == null,
            )
            val stopped = engine.state.copy()
            assertTrue(engine.advanceTractor(100.0, true, now).isEmpty() && sameState(stopped, engine.state))
            assertNotNull(engine.state.plots[far])
        }
    }

    @Test
    fun `cancelling while already driving toward home keeps the current step`() {
        val beyondHome = GridPoint(10, 5)
        val engine = engine(listOf(beyondHome))
        engine.state.tractorCargo[home] = mapOf("wheat" to 1)
        engine.state.tractorRun =
            TractorRun(
                home = home,
                cropID = "wheat",
                position = GridPoint(10, 13),
                nextTile = GridPoint(10, 12),
                facing = TractorFacing.left,
                progress = 0.25,
                remaining = setOf(beyondHome),
            )
        val outbound = engine.state.tractorRun!!
        assertTrue(engine.cancelTractorHarvest(home).isEmpty())
        val returning = engine.state.tractorRun!!
        assertTrue(
            returning.position == outbound.position && returning.nextTile == outbound.nextTile &&
                returning.progress == outbound.progress,
        )
        assertTrue(finish(engine).isEmpty())
        assertTrue(engine.state.plots[beyondHome] != null && engine.state.tractorCargo[home] == mapOf("wheat" to 1))
    }

    @Test fun `parked tractor reserves its whole ground footprint`() {
        val tractor = Building.named("tractor1")!!
        for (style in listOf("right", "left", "front", "back")) {
            val building = tractor.styled(style)
            for (tile in building.footprint(home)) {
                val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, logs = 100_000))
                engine.select(tractor.styles.first { it.id == style }, tractor)
                engine.beginPlacing(home)
                engine.movePlacement(home)
                engine.state.rocks[tile] = RockSize.small
                assertEquals(Rejection.NotEnoughSpace, engine.placementRejection(now))
                engine.state.rocks.remove(tile)
                engine.state.fences[tile] = FenceColor.all[0].id
                assertEquals(Rejection.NotEnoughSpace, engine.placementRejection(now))
                engine.state.fences.remove(tile)
                assertFalse(engine.confirmPlacement(now).containsRejection)
                assertEquals(home, engine.state.buildingBlocking(tile)?.origin)
                engine.beginMoving(engine.building(home)!!)
                engine.movePlacement(GridPoint(home.row + 1, home.col))
                assertFalse(engine.confirmPlacement(now).containsRejection)
            }
        }
    }

    @Test fun `current trips reject steps longer than one tile`() {
        val engine = engine()
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        val run = engine.state.tractorRun!!
        engine.state.tractorRun = run.copy(nextTile = GridPoint(run.position.row, run.position.col + 2))
        assertThrows(Exception::class.java) { GameStateJson.decode(GameStateJson.encode(engine.state)) }
    }

    @Test fun `malformed saved trip is rejected before driving`() {
        val engine = engine()
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        engine.state.tractorRun = engine.state.tractorRun!!.copy(nextTile = GridPoint(Int.MAX_VALUE, Int.MAX_VALUE))
        assertThrows(Exception::class.java) { GameStateJson.decode(GameStateJson.encode(engine.state)) }
    }
}
