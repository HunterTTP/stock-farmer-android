package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.hatch
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.updateHerds
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

class AnimalPlacementTest {
    private val house = GridPoint(20, 20)
    private val pet = AnimalRef.Pet(house)
    private val hatchNow = Instant.ofEpochSecond(11_000_000)
    private val day = Instant.ofEpochSecond(50_000_000)
    private val now = Instant.ofEpochSecond(40_000_000)
    private val origin = GridPoint(15, 15)

    private fun petEngine(position: GridPoint): GameEngine {
        val engine = GameEngine()
        engine.state.putBuilding(house, "petHouse")
        engine.state.pets[house] = Pet(breed = "grey", name = "Cat", position = position)
        return engine
    }

    // Footing

    @Test
    fun `an animal on a building tile walks off to open ground`() {
        val engine = petEngine(GridPoint(30, 30))
        engine.state.putBuilding(GridPoint(30, 30), "petHouse")
        val direction = engine.wanderDirection(pet, now, using = SeededRandom(1))
        assertNotNull(direction)
        val moved = direction?.let { engine.move(pet, it) }
        assertNotNull(moved)
        assertTrue(engine.isOpenGround(moved!!))
    }

    @Test
    fun `an animal deep inside a building crosses its tiles to get out`() {
        val engine = petEngine(GridPoint(22, 22))
        engine.state.putBuilding(GridPoint(20, 20), "house1")
        engine.state.pets[GridPoint(20, 20)] = Pet(breed = "grey", name = "Cat", position = GridPoint(22, 22))
        val generator = SeededRandom(2)
        repeat(10) {
            val direction = engine.wanderDirection(pet, now, using = generator) ?: return@repeat
            engine.move(pet, direction)
        }
        assertTrue(engine.isOpenGround(engine.position(pet)!!))
    }

    @Test
    fun `an animal on open ground never steps onto a building or water`() {
        val engine = petEngine(GridPoint(21, 20))
        engine.state.putBuilding(GridPoint(21, 21), "barn1")
        engine.state.water[GridPoint(21, 19)] = WaterStyle.medium
        assertNull(engine.move(pet, GridPoint(0, 1)))
        assertNull(engine.move(pet, GridPoint(0, -1)))
        val generator = SeededRandom(3)
        repeat(200) {
            val direction = engine.wanderDirection(pet, now, using = generator) ?: return@repeat
            engine.move(pet, direction)
            assertTrue(engine.isOpenGround(engine.position(pet) ?: house))
        }
    }

    @Test
    fun `a pet already on its front tile can be summoned without blocking itself`() {
        val engine = petEngine(GridPoint(21, 20))
        assertEquals(listOf<GameEvent>(GameEvent.Summoned(GridPoint(21, 20))), engine.summonPet(house))
    }

    // Summon and hatch

    @Test
    fun `summoning brings the pet to the front of its house`() {
        val engine = petEngine(GridPoint(28, 28))
        assertEquals(listOf<GameEvent>(GameEvent.Summoned(GridPoint(21, 20))), engine.summonPet(house))
        assertEquals(GridPoint(21, 20), engine.state.pets[house]?.position)
    }

    @Test
    fun `summoning with a blocked front puts the pet on the nearest free tile`() {
        val engine = petEngine(GridPoint(28, 28))
        engine.state.water[GridPoint(21, 20)] = WaterStyle.medium
        val first = engine.summonPet(house).first()
        assertTrue(first is GameEvent.Summoned)
        first as GameEvent.Summoned
        assertTrue(engine.isOpenGround(first.point))
        assertNotEquals(GridPoint(21, 20), first.point)
    }

    @Test
    fun `summoning without a pet does nothing`() {
        val engine = GameEngine()
        engine.state.putBuilding(house, "petHouse")
        assertTrue(engine.summonPet(house).isEmpty())
    }

    @Test
    fun `chicks only hatch on the tile directly in front of the coop`() {
        val engine = GameEngine()
        engine.state.putBuilding(house, "chickenCoop")
        engine.hatch(hatchNow)
        assertEquals(listOf(GridPoint(22, 20)), engine.chickens(house).map { it.position })
    }

    @Test
    fun `nothing hatches while the tile in front is blocked`() {
        val engine = GameEngine()
        engine.state.putBuilding(house, "chickenCoop")
        engine.state.fences[GridPoint(22, 20)] = "wood"
        assertTrue(engine.hatch(hatchNow).isEmpty())
        assertTrue(engine.chickens(house).isEmpty())
        engine.state.fences = mutableMapOf()
        assertEquals(1, engine.hatch(hatchNow).size)
        engine.state.water[GridPoint(22, 20)] = WaterStyle.medium
        engine.state.coops[house] = engine.state.coops[house]!!.copy(nextChickAt = hatchNow)
        assertTrue(engine.hatch(hatchNow.advancedBy(Coop.HATCH_INTERVAL)).isEmpty())
    }

    @Test
    fun `a pet standing in front of the coop does not block hatching`() {
        val engine = GameEngine()
        engine.state.putBuilding(house, "chickenCoop")
        engine.state.putBuilding(GridPoint(30, 30), "petHouse")
        engine.state.pets[GridPoint(30, 30)] = Pet(breed = "grey", name = "Cat", position = GridPoint(22, 20))
        assertEquals(1, engine.hatch(hatchNow).size)
    }

    // Animals inside

    private fun insideEngine(
        minute: Double,
        building: String,
    ): GameEngine {
        val engine = GameEngine()
        engine.state.putBuilding(origin.let { GridPoint(20, 20) }, building)
        engine.setTimeOfDay(minute, day)
        return engine
    }

    private fun front(engine: GameEngine): GridPoint {
        val placed = engine.state.placedBuildings[0]
        return placed.building.frontTile(placed.origin)
    }

    @Test
    fun `a pet inside does not rush crops`() {
        for ((minute, rushes) in listOf(12.0 * 60 to true, 0.0 to false)) {
            val engine = insideEngine(minute, "petHouse")
            val spot = front(engine)
            engine.state.pets[house] = Pet(breed = Cat.starter.id, name = "x", position = spot)
            engine.state.farmland.add(spot)
            engine.state.plots[spot] = Plot(Crop.starter.id, day)
            val events = engine.petTendsCrop(house, 1_000.0, 1_000.0, day, SeededRandom(1))
            assertEquals(rushes, events.isNotEmpty())
        }
    }

    @Test
    fun `a hen inside lays no eggs`() {
        for ((minute, lays) in listOf(12.0 * 60 to true, 0.0 to false)) {
            val engine = insideEngine(minute, "chickenCoop")
            val hen =
                Chicken(
                    0,
                    ChickenVariant.all[0].id,
                    "x",
                    front(engine),
                    AnimalMeal(day.advancedBy(-AnimalMeal.DURATION - 1)),
                    day.advancedBy(-Chicken.GROW_DURATION),
                )
            engine.state.coops[house] = Coop(chickens = listOf(hen), nextChickAt = day.advancedBy(1_000_000.0))
            engine.layEggs(1_000.0, day, SeededRandom(2))
            assertEquals(lays, engine.state.eggs.isNotEmpty())
        }
    }

    @Test
    fun `a sheep and a rabbit inside give nothing to take`() {
        for ((minute, works) in listOf(12.0 * 60 to true, 0.0 to false)) {
            val engine = insideEngine(minute, "rabbitHutch")
            val spot = front(engine)
            for ((species, tool) in listOf(
                LivestockSpecies.sheep to HarvestTool.scythe,
                LivestockSpecies.rabbit to HarvestTool.bucket,
            )) {
                val later = day.advancedBy(100_000.0)
                val animal =
                    Livestock(
                        0,
                        species.variants.first().id,
                        "x",
                        spot,
                        AnimalMeal(later.advancedBy(-AnimalMeal.DURATION - 1)),
                        Instant.ofEpochSecond(1),
                    )
                engine.state.herds[house] = Herd(listOf(animal), day.advancedBy(1_000_000.0))
                engine.setTimeOfDay(minute, later)
                val taken = engine.collectProduce(spot, tool, later)
                assertEquals(works, taken.isNotEmpty(), species.id)
            }
        }
    }

    // Large home roaming

    private fun barnEngine(): GameEngine {
        val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP))
        engine.state.putBuilding(origin, "barn2")
        engine.setTimeOfDay(720.0, now)
        engine.updateHerds(now)
        return engine
    }

    @Test
    fun `calves and cows can leave their actual spawn tile and return to shelter`() {
        for (age in listOf(0.0, LivestockSpecies.cow.growDuration + 1)) {
            val engine = barnEngine()
            val cow = engine.animals(origin).first()
            val herd = engine.state.herds[origin]!!
            engine.state.herds[origin] = herd.copy(animals = listOf(cow.copy(growthBonus = age)))
            val ref = AnimalRef.Livestock(origin, cow.id)
            val front = engine.shelterTile(ref)!!
            assertEquals(front, cow.position)
            assertEquals(GridPoint(front.row, front.col + 1), engine.move(ref, GridPoint(0, 1)))
            val generator = SeededRandom(41)
            val visited = mutableSetOf<GridPoint>()
            repeat(100) {
                val direction = engine.wanderDirection(ref, now, generator)!!
                val destination = engine.move(ref, direction)!!
                visited += destination
                assertTrue(engine.isOpenGround(destination))
            }
            assertTrue(visited.size > 5)
            engine.setTimeOfDay(0.0, now)
            var steps = 0
            while (engine.position(ref) != front && steps++ < 30) {
                val direction = engine.wanderDirection(ref, now, generator)!!
                assertNotNull(engine.move(ref, direction))
            }
            assertEquals(front, engine.position(ref))
            assertTrue(engine.isInside(ref, now))
        }
    }

    @Test
    fun `animals wander far from home when nothing blocks them`() {
        val engine = barnEngine()
        val ref = AnimalRef.Livestock(origin, engine.animals(origin).first().id)
        val front = engine.shelterTile(ref)!!
        val generator = SeededRandom(7)
        var farthest = 0
        repeat(3_000) {
            val direction = engine.wanderDirection(ref, now, generator) ?: return@repeat
            val spot = engine.move(ref, direction) ?: return@repeat
            farthest = max(farthest, max(abs(spot.row - front.row), abs(spot.col - front.col)))
        }
        assertTrue(farthest > 5)
    }

    @Test
    fun `a fence ring keeps an animal in`() {
        val engine = barnEngine()
        val ref = AnimalRef.Livestock(origin, engine.animals(origin).first().id)
        val front = engine.shelterTile(ref)!!
        for (row in front.row - 3..front.row + 3) {
            for (col in front.col - 3..front.col + 3) {
                if (max(abs(row - front.row), abs(col - front.col)) ==
                    3
                ) {
                    engine.state.fences[GridPoint(row, col)] = "wood"
                }
            }
        }
        val generator = SeededRandom(7)
        repeat(1_000) {
            val direction = engine.wanderDirection(ref, now, generator) ?: return@repeat
            val spot = engine.move(ref, direction) ?: return@repeat
            assertTrue(max(abs(spot.row - front.row), abs(spot.col - front.col)) <= 2)
        }
    }

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
}
