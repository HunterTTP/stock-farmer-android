package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class RemainingGameplayTest {
    private val now = Instant.ofEpochSecond(40_000_000)

    @Test fun `every animal comes back to the tile it was left on`() {
        val state = GameState()
        state.hasRocks = true
        val pet = GridPoint(10, 10)
        state.putBuilding(pet, "petHouse")
        state.pets[pet] = Pet(breed = Cat.starter.id, name = "Mittens", position = GridPoint(15, 13))
        val coop = GridPoint(30, 30)
        state.putBuilding(coop, "chickenCoop")
        state.coops[coop] =
            Coop(
                chickens = listOf(Chicken(0, ChickenVariant.all[0].id, "Henny", GridPoint(33, 34), hatchedAt = now)),
                nextChickAt = now.advancedBy(1_000_000.0),
            )
        val hutch = GridPoint(40, 10)
        state.putBuilding(hutch, "rabbitHutch")
        state.herds[hutch] =
            Herd(
                listOf(Livestock(0, LivestockSpecies.all[0].variants[0].id, "x", GridPoint(44, 12), bornAt = now)),
                now.advancedBy(1_000_000.0),
            )
        for (row in 20..24) for (col in 20..24) state.water[GridPoint(row, col)] = WaterStyle.deep
        state.herds[GridPoint(22, 22)] =
            Herd(
                listOf(Livestock(0, "duck:white", "Daisy", GridPoint(21, 22), bornAt = now)),
                now.advancedBy(1_000_000.0),
            )
        state.fishPonds[GridPoint(20, 20)] = FishPond(PondFish("carp", GridPoint(23, 21)), now)
        state.lastSavedAt = now
        val before = GameEngine(state)
        val engine = GameEngine(GameStateJson.decode(GameStateJson.encode(state)))
        val random = SeededRandom(1)
        val later = now.advancedBy(5 * 86_400.0)
        engine.reopen(later, random)
        engine.updateCoops(later, random)
        engine.updateHerds(later, random)
        assertEquals(4, before.animals.size)
        assertEquals(before.state.fishPonds, engine.state.fishPonds)
        for (animal in before.animals) assertEquals(before.position(animal), engine.position(animal), "$animal")
    }

    @Test fun `every addition can be planted harvested and saved`() {
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

    @Test fun `harvests produce ordinary crops without the retired quality bonus`() {
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

    @Test fun `a saved farm does not celebrate kinds it already has`() {
        val state = stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1000)
        state.putBuilding(GridPoint(3, 3), "shed")
        state.career.builtBuildingIDs.addAll(state.buildings.values)
        val engine = GameEngine(state)
        engine.state.selectedBuildingID = "shed"
        engine.beginPlacing(GridPoint(10, 10))
        assertFalse(engine.isPlacingFirstOfItsKind)
    }

    @Test fun `a game keeps its clock and starts at eight in the morning`() {
        val fresh = newGame(SeededRandom(1), now)
        val engine = GameEngine(fresh)
        assertEquals(480.0, engine.minuteOfDay(now), 0.0001)
        engine.setTimeOfDay(60.0, now)
        assertEquals(60.0, engine.minuteOfDay(now), 0.0001)
        assertEquals(1.0, DayNightCycle.darkness(now, engine.state.clockOffset))
        engine.setTimeOfDay(5000.0, now)
        assertEquals(1439.0, engine.minuteOfDay(now), 0.0001)
        assertEquals(engine.state.clockOffset, GameStateJson.decode(GameStateJson.encode(engine.state)).clockOffset)
        assertEquals(0.0, GameStateJson.decode("""{"money":1}""").clockOffset)
    }

    @Test fun `setting the time stays within the farms day whatever the real time of day`() {
        for (realHour in listOf(0.0, 5.0, 12.0, 23.9)) {
            val real = Instant.EPOCH.advancedBy(100 * DayNightCycle.secondsPerDay + realHour * 3600)
            val engine = GameEngine()
            engine.state.clockOffset = 3 * 3600.0
            val day = NightPulse.day(real, engine.state.clockOffset)
            for (minute in listOf(0.0, 5.0, 600.0, 1439.0)) {
                engine.setTimeOfDay(minute, real)
                assertEquals(minute, engine.minuteOfDay(real), 1e-6)
                assertEquals(day, NightPulse.day(real, engine.state.clockOffset))
            }
        }
    }

    @Test fun `seasons and sightings are remembered`() {
        val engine = GameEngine()
        assertTrue(engine.hasSeenEnvironment(CareerRecord.environmentID(Season.spring)))
        assertFalse(engine.hasSeenEnvironment(CareerRecord.environmentID(Season.winter)))
        engine.setSeason(Season.winter)
        engine.setSeason(Season.fall)
        assertTrue(engine.hasSeenEnvironment(CareerRecord.environmentID(Season.winter)))
        engine.noteSeenEnvironment(CareerRecord.environmentID(Butterflies.Wings.Shifting))
        assertTrue(engine.hasSeenEnvironment("butterfly:shifting"))
        assertFalse(engine.hasSeenEnvironment("butterfly:rare"))
    }

    @Test fun `a market stand in a big look from an old save becomes a large one`() {
        val engine = GameEngine()
        val big = GridPoint(10, 10)
        val small = GridPoint(20, 20)
        engine.state.putBuilding(big, "marketStand", "big")
        engine.state.putBuilding(small, "marketStand", "small")
        engine.reopen(now, SeededRandom(1))
        assertEquals(mapOf(big to "marketStandLarge", small to "marketStand"), engine.state.buildings)
        assertTrue(engine.state.buildingStyles.isEmpty())
    }
}
