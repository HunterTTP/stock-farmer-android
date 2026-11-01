package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.OreVein
import com.hunter.stockfarmer.core.catalog.Rock
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.TreeSize
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.hypot

class WildGrowthTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val middle = GridPoint(25, 25)

    private class Night(
        val engine: GameEngine,
        val plan: WildGrowthPlan,
    )

    /** Everything the wave of one night reaches, from the middle of the map. */
    private fun wholeSweep(origin: Double = GameState.GRID_SIZE / 2.0): List<GridPoint> =
        WildGrowthSweep(origin, origin).tilesReached(1.0)

    /** One whole night on a fresh map: planned, then grown as the wave reaches every tile. */
    private fun nights(
        count: Int,
        seed: Long,
        prepare: (GameEngine) -> Unit = {},
    ): List<Night> {
        val generator = SeededRandom(seed)
        return List(count) {
            val engine = GameEngine()
            prepare(engine)
            val plan = engine.planWildGrowth(generator)
            engine.growWild(wholeSweep(), plan, now)
            Night(engine, plan)
        }
    }

    @Test
    fun `the sweep reaches every tile once nearest first`() {
        val sweep = WildGrowthSweep(10.0, 40.0)
        val reached = mutableListOf<GridPoint>()
        var step = 0.0
        while (step <= 1.0) {
            reached += sweep.tilesReached(step)
            step += 0.05
        }
        assertTrue(reached.size == GameState.GRID_SIZE * GameState.GRID_SIZE && reached.toSet().size == reached.size)
        val distances = reached.map { hypot(it.row + 0.5 - 10, it.col + 0.5 - 40) }
        assertEquals(distances.sorted(), distances)
        assertTrue(sweep.tilesReached(1.0).isEmpty())
    }

    @Test
    fun `the sweep only has reached the tiles the front has passed`() {
        val sweep = WildGrowthSweep(25.0, 25.0)
        assertTrue(sweep.tilesReached(0.0).isEmpty())
        val early = sweep.tilesReached(0.2)
        assertTrue(early.isNotEmpty() && early.size < 600)
        val radius = NightPulse.radius(0.2, NightPulse.reach(25.0, 25.0))
        assertTrue(early.all { hypot(it.row + 0.5 - 25, it.col + 0.5 - 25) <= radius })
    }

    @Test
    fun `the edge weight is nothing in the middle and certain at the edge`() {
        assertTrue(edgeWeight(GridPoint(24, 24)) < 0.001)
        assertTrue(edgeWeight(GridPoint(0, 20)) == 1.0 && edgeWeight(GridPoint(30, 49)) == 1.0)
    }

    @Test
    fun `every night leaves trees rocks and an ore`() {
        for (night in nights(100, 11)) {
            val state = night.engine.state
            assertTrue(state.trees.size in NIGHTLY_TREES)
            assertTrue(state.rocks.values.count { it.ore == null } in NIGHTLY_ROCKS)
            assertEquals(1, state.rocks.values.count { it.ore != null })
        }
    }

    @Test
    fun `the nights things are dealt out evenly all the way round the map`() {
        for (night in nights(100, 12)) {
            val perSector = IntArray(WILD_SECTORS)
            for (tile in night.plan.tiles) perSector[wildSector(tile)] += 1
            assertTrue(perSector.all { it in 1..2 }, perSector.toList().toString())
        }
    }

    @Test
    fun `they lie mostly toward the edge of the map`() {
        val weights = nights(100, 13).flatMap { night -> night.plan.tiles.map(::edgeWeight) }
        assertTrue(weights.sum() / weights.size > 0.5)
        assertTrue(weights.count { it < 0.05 } < weights.size / 20)
    }

    @Test
    fun `nothing is planned on tiles that are taken or near the start`() {
        val generator = SeededRandom(14)
        val covered = GameEngine()
        for (tile in wholeSweep()) {
            covered.state.groundCover[tile] =
                com.hunter.stockfarmer.core.catalog.GroundCover.dirtPath
        }
        assertTrue(covered.planWildGrowth(generator).isEmpty)
        val wet = GameEngine()
        for (tile in wholeSweep()) wet.state.water[tile] = WaterStyle.deep
        assertTrue(wet.planWildGrowth(generator).isEmpty)
        for (night in nights(60, 15)) {
            val state = night.engine.state
            val tiles = state.trees.flatMap { it.value.tree.footprint(it.key) } + state.rocks.keys
            assertTrue(tiles.all { GameState.isClearOfStart(it) })
            assertEquals(tiles.size, tiles.toSet().size)
            assertTrue(state.trees.values.all { it.tree.size == TreeSize.small || it.tree.size == TreeSize.medium })
        }
    }

    @Test
    fun `stones and veins stop at their limits`() {
        val prepare = { engine: GameEngine ->
            for (col in 0 until Rock.onANewMap.last) engine.state.rocks[GridPoint(20, col)] = RockSize.small
            for (col in 0 until OreVein.maximumOnMap) engine.state.rocks[GridPoint(21, col)] = RockSize.gold
        }
        for (night in nights(30, 18, prepare)) {
            val state = night.engine.state
            assertEquals(Rock.onANewMap.last, state.rocks.values.count { it.ore == null })
            assertEquals(OreVein.maximumOnMap, state.rocks.values.count { it.ore != null })
            assertTrue(state.trees.size >= NIGHTLY_TREES.first)
        }
    }

    @Test
    fun `things only grow where the wave has reached and only if the ground is still free`() {
        val generator = SeededRandom(20)
        val engine = GameEngine()
        val plan = engine.planWildGrowth(generator)
        val tile = plan.tiles.first()
        val grewInTheMiddle = engine.growWild(listOf(middle), plan, now)
        assertTrue(grewInTheMiddle.isEmpty() && engine.state.trees.isEmpty() && engine.state.rocks.isEmpty())
        val taken = GameEngine(engine.state.copy())
        taken.state.farmland.add(tile)
        assertTrue(taken.growWild(listOf(tile), plan, now).isEmpty())
        assertEquals(listOf(tile), engine.growWild(listOf(tile), plan, now))
        assertEquals(1, engine.state.trees.size + engine.state.rocks.size)
    }

    @Test
    fun `reopening after any time away grows nothing`() {
        val engine = GameEngine()
        engine.state.hasRocks = true
        engine.state.hasOreVeins = true
        engine.markSaved(now)
        engine.reopen(now.advancedBy(30 * 24 * 60 * 60.0), SeededRandom(19))
        assertTrue(engine.state.trees.isEmpty() && engine.state.rocks.isEmpty())
    }
}
