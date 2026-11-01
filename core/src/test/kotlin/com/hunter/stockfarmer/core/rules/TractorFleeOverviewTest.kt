package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.Fertilizer
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

class TractorFleeOverviewTest {
    private val now = Instant.ofEpochSecond(40_000_000)
    private val home = GridPoint(10, 10)
    private val barn = GridPoint(30, 30)

    /** A tractor mid-trip along row 10, whose reserved tiles reach column 12, with a crop far down the row. */
    private fun drivingEngine(): GameEngine {
        val state = GameState()
        state.putBuilding(home, "tractor1")
        val crop = GridPoint(10, 25)
        state.farmland.add(crop)
        state.plots[crop] = Plot("wheat", now.advancedBy(-100.0))
        val engine = GameEngine(state)
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        return engine
    }

    private fun withCow(
        point: GridPoint,
        engine: GameEngine,
    ): Pair<GameEngine, AnimalRef> {
        val cow = Livestock(0, LivestockSpecies.cow.variants[0].id, "Cow", point, bornAt = now)
        engine.state.herds[barn] = Herd(listOf(cow), now)
        return engine to AnimalRef.Livestock(barn, 0)
    }

    private fun directions(
        engine: GameEngine,
        animal: AnimalRef,
        random: SeededRandom,
    ) = (0 until 80).mapNotNull { engine.wanderDirection(animal, now, random) }.toSet()

    @Test
    fun `a farm animal near the driving tractor steps away from it`() {
        val random = SeededRandom(4)
        val (engine, cow) = withCow(GridPoint(10, 14), drivingEngine())
        repeat(20) { assertEquals(GridPoint(0, 1), engine.wanderDirection(cow, now, random)) }
    }

    @Test
    fun `a hen flees too`() {
        val engine = drivingEngine()
        val hen = Chicken(0, ChickenVariant.all[0].id, "Hen", GridPoint(10, 14), hatchedAt = now)
        engine.state.coops[barn] = Coop(chickens = listOf(hen), nextChickAt = now)
        assertEquals(GridPoint(0, 1), engine.wanderDirection(AnimalRef.Chicken(barn, 0), now, SeededRandom(4)))
    }

    @Test
    fun `an animal farther than three tiles wanders as usual`() {
        val (engine, cow) = withCow(GridPoint(10, 17), drivingEngine())
        assertTrue(directions(engine, cow, SeededRandom(4)).size > 1)
    }

    @Test
    fun `nothing flees a parked tractor`() {
        val parked = drivingEngine()
        parked.state.tractorRun = null
        val (engine, cow) = withCow(GridPoint(10, 13), parked)
        assertTrue(directions(engine, cow, SeededRandom(4)).size > 1)
    }

    @Test
    fun `pets do not flee the tractor`() {
        val engine = drivingEngine()
        engine.state.pets[barn] = Pet(breed = Cat.starter.id, name = "Cat", position = GridPoint(10, 14))
        assertTrue(directions(engine, AnimalRef.Pet(barn), SeededRandom(4)).size > 1)
    }

    @Test
    fun `a cornered animal never steps toward the tractor`() {
        val (engine, cow) = withCow(GridPoint(10, 14), drivingEngine())
        for (tile in listOf(GridPoint(10, 15), GridPoint(9, 14), GridPoint(11, 14))) {
            engine.state.fences[tile] = FenceColor.starter.id
        }
        assertNull(engine.wanderDirection(cow, now, SeededRandom(4)))
    }

    // Crop growth overview

    @Test
    fun `overview groups only planted types in unlock order and waits for the last plant`() {
        val engine = GameEngine()
        val wheat = Crop.starter
        val carrot = Crop.named("carrot")!!
        engine.state.plots =
            mutableMapOf(
                GridPoint(20, 20) to Plot(carrot.id, now),
                GridPoint(21, 20) to Plot(wheat.id, now.advancedBy(-10.0)),
                GridPoint(22, 20) to Plot(wheat.id, now.advancedBy(-20.0)),
                GridPoint(23, 20) to Plot(wheat.id, now.advancedBy(-10_000.0)),
            )
        val before = engine.state.copy()
        val rows = engine.cropGrowthOverview(now)
        assertEquals(listOf(wheat.id, carrot.id), rows.map { it.crop.id })
        assertEquals(listOf(3, 1), rows.map { it.count })
        assertEquals(listOf(1, 0), rows.map { it.readyCount })
        assertEquals(listOf(wheat.growDuration - 10, carrot.growDuration), rows.map { it.remaining })
        assertEquals(wheat.growDuration - 15, engine.cropGrowthOverview(now.advancedBy(5.0)).first().remaining)
        assertTrue(engine.cropGrowthOverview(now.advancedBy(100_000.0)).all { it.remaining == 0.0 })
        assertTrue(sameState(before, engine.state))
        engine.state.plots =
            engine.state.plots
                .filterValues { it.cropID != wheat.id }
                .toMutableMap()
        assertEquals(listOf(carrot.id), engine.cropGrowthOverview(now).map { it.crop.id })
    }

    @Test
    fun `empty farmland and unplanted catalog types do not appear`() {
        assertTrue(GameState().farmland.isNotEmpty())
        assertTrue(GameEngine().cropGrowthOverview(now).isEmpty())
    }

    @Test
    fun `latest finishing plant accounts for growth bonuses instead of just planting time`() {
        val engine = GameEngine()
        val crop = Crop.named("carrot")!!
        val boosted = GridPoint(20, 20)
        val plain = GridPoint(30, 30)
        engine.state.farmland = mutableSetOf(boosted, plain)
        engine.state.water[GridPoint(20, 19)] = WaterStyle.deep
        engine.state.fertilized = mutableSetOf(boosted)
        engine.state.putDecoration(GridPoint(20, 21), PlacedDecoration(Decoration.named("scarecrow")!!.id))
        engine.state.plots = mutableMapOf(boosted to Plot(crop.id, now), plain to Plot(crop.id, now.advancedBy(-1.0)))
        assertEquals(crop.growDuration - 1, engine.cropGrowthOverview(now).first().remaining)
        engine.state.plots.remove(plain)
        val multiplier =
            GrowthConditions.SATURATION_MULTIPLIER * Fertilizer.growthMultiplier * Decoration.GUARD_MULTIPLIER
        val row = engine.cropGrowthOverview(now).first()
        assertTrue(abs(row.remaining - crop.growDuration / multiplier) < 1e-7)
    }

    @Test
    fun `fruit trees count once each and track the last ripening including regrowth`() {
        val engine = GameEngine()
        val apple = Crop.named("apple")!!
        val ripe = GridPoint(20, 20)
        val growing = GridPoint(30, 30)
        for (origin in listOf(ripe, growing)) {
            val plantedAt = now.advancedBy(if (origin == ripe) -apple.growDuration else -10.0)
            for (tile in apple.plotTiles(origin)) engine.state.plots[tile] = Plot(apple.id, plantedAt, origin)
        }
        // A crop can grow under a fruit tree's upper row.
        engine.state.plots[ripe] = Plot(Crop.starter.id, now)
        val rows = engine.cropGrowthOverview(now)
        assertEquals(listOf("wheat", "apple"), rows.map { it.crop.id })
        assertEquals(listOf(1, 2), rows.map { it.count })
        assertEquals(apple.growDuration - 10, rows.last().remaining)
        engine.select(HarvestTool.bucket)
        val events = engine.tapCrop(apple.plotTiles(ripe)[0], now)
        assertFalse(events.containsRejection)
        assertEquals(apple.growDuration, engine.cropGrowthOverview(now).last().remaining)
        assertEquals(2, engine.cropGrowthOverview(now).last().count)
    }
}
