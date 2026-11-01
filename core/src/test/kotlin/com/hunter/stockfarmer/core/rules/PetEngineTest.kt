package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.beginMoving
import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.PetBreed
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

/** The pet half of the Swift `PetTests`: moving in, names, houses, wandering and rushing crops. */
class PetEngineTest {
    private val now = Instant.ofEpochSecond(12_000_000)
    private val house = GridPoint(20, 20)
    private val grey = Cat.starter
    private val right = GridPoint(0, 1)

    private fun engine(level: Int = 1): GameEngine {
        val engine = GameEngine()
        engine.state.xp = LevelRules.xpToReach(max(level, 3))
        engine.state.money = 1_000_000.0
        engine.state.storage[StorageItem.Log.id] = 90
        engine.state.storage[StorageItem.Stone.id] = 5
        engine.state.mode = PlayMode.build
        engine.state.selectedBuildingID = "petHouse"
        engine.build(house, now)
        return engine
    }

    private fun engineWithPet(at: GridPoint? = null): GameEngine {
        val engine = GameEngine()
        // Animals only wander by day and out of the rain, so the clock is set to noon whenever the test runs.
        engine.setTimeOfDay(12 * 60.0, Instant.now())
        engine.state.putBuilding(house, "petHouse")
        engine.state.pets[house] = Pet(breed = "grey", name = "Cat", position = at ?: GridPoint(21, 20))
        return engine
    }

    private fun GameEngine.setPetPosition(position: GridPoint) {
        state.pets[house] = state.pets[house]!!.copy(position = position)
    }

    @Test fun `there are six colors`() {
        assertEquals(listOf("grey", "black", "white", "orange", "calico", "colorpoint"), Cat.all.map { it.id })
        assertEquals(listOf("Grey", "Black", "White", "Orange", "Calico", "Colorpoint"), Cat.all.map { it.colorName })
    }

    @Test fun `pet houses cost twenty five and do not speed up crops`() {
        val petHouse = Building.named("petHouse")!!
        assertTrue(petHouse.isPetHouse)
        assertEquals(25.0, petHouse.cost)
        assertEquals(1, Building.all.count { it.isPetHouse })
        val tile = GridPoint(21, 20)
        val state = stateWith(logs = 0)
        state.farmland += tile
        state.plots[tile] = Plot("wheat", now)
        val before = GameEngine(state).remainingGrowTime(tile, now)
        state.putBuilding(house, petHouse.id)
        assertEquals(before, GameEngine(state).remainingGrowTime(tile, now))
        assertEquals(Crop.starter.growDuration, before)
    }

    @Test fun `a pet moves in and appears in front of the house`() {
        val engine = engine()
        val events = engine.movePetIn(house, PetBreed.cat(grey), "Mittens")
        val front = GridPoint(21, 20)
        assertEquals(listOf<GameEvent>(GameEvent.Adopted(front)), events)
        assertEquals(Pet(breed = "grey", name = "Mittens", position = front), engine.pet(house))
        assertEquals(AnimalRef.Pet(house), engine.animalStandingAt(front))
    }

    @Test fun `each house holds only one pet`() {
        val engine = engine()
        engine.movePetIn(house, PetBreed.cat(grey), "First")
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.HouseOccupied)),
            engine.movePetIn(house, PetBreed.cat(grey), "Second"),
        )
        assertEquals("First", engine.pet(house)?.name)
    }

    @Test fun `only pet houses take pets`() {
        val engine = engine()
        engine.state.putBuilding(GridPoint(30, 30), "barn1")
        assertTrue(engine.movePetIn(GridPoint(30, 30), PetBreed.cat(grey), "x").isEmpty())
        assertTrue(engine.movePetIn(GridPoint(5, 5), PetBreed.cat(grey), "x").isEmpty())
    }

    @Test fun `every color can move in from the start`() {
        for (cat in Cat.all) {
            val engine = engine()
            engine.state.xp = 0
            assertEquals(1, engine.movePetIn(house, PetBreed.cat(cat), "x").size)
            assertEquals(cat, engine.pet(house)?.cat)
        }
    }

    @Test fun `names are trimmed limited and blank ones fall back`() {
        val engine = engine()
        engine.movePetIn(house, PetBreed.cat(grey), "   ")
        assertEquals("Grey Cat", engine.pet(house)?.name)
        engine.renamePet(house, "  Mittens ")
        assertEquals("Mittens", engine.pet(house)?.name)
        engine.renamePet(house, "a".repeat(50))
        assertEquals(Pet.MAX_NAME_LENGTH, engine.pet(house)?.name?.length)
    }

    @Test fun `a pet skips tiles that are blocked when moving in`() {
        val engine = engine()
        engine.state.water[GridPoint(21, 20)] = WaterStyle.deep
        engine.movePetIn(house, PetBreed.cat(grey), "x")
        val position = engine.pet(house)!!.position
        assertTrue(position != GridPoint(21, 20))
        assertTrue(max(abs(position.row - house.row), abs(position.col - house.col)) <= 2)
    }

    @Test fun `water on a pets tile sends it back to the front of its house`() {
        val engine = engine()
        engine.movePetIn(house, PetBreed.cat(grey), "x")
        val strayed = GridPoint(22, 20)
        engine.setPetPosition(strayed)
        engine.state.mode = PlayMode.landscape
        engine.select(LandscapeTool.water)
        engine.tap(strayed, now)
        assertEquals(GridPoint(21, 20), engine.pet(house)?.position)
        assertNotNull(engine.state.water[strayed])
    }

    @Test fun `a building over a pet moves it aside`() {
        val engine = engine()
        engine.movePetIn(house, PetBreed.cat(grey), "x")
        engine.state.mode = PlayMode.build
        engine.state.selectedBuildingID = "shed"
        engine.state.xp = lateGameXP
        engine.beginPlacing(GridPoint(21, 20))
        engine.confirmPlacement(now)
        val position = engine.pet(house)!!.position
        assertNull(engine.building(position))
    }

    @Test fun `a pet follows its house when moved`() {
        val engine = engine()
        engine.movePetIn(house, PetBreed.cat(grey), "Mittens")
        engine.beginMoving(engine.building(house)!!, GridPoint(30, 30))
        val newHouse = engine.buildingPlacement!!.origin
        engine.confirmPlacement(now)
        assertNull(engine.pet(house))
        val pet = engine.pet(newHouse)!!
        assertEquals("Mittens", pet.name)
        assertEquals(GridPoint(newHouse.row + 1, newHouse.col), pet.position)
    }

    @Test fun `destroying a house removes its pet`() {
        val engine = engine()
        engine.movePetIn(house, PetBreed.cat(grey), "x")
        engine.demolish(engine.building(house)!!)
        assertTrue(engine.state.pets.isEmpty())
    }

    @Test fun `at most ten pet houses can be placed`() {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.selectedBuildingID = "petHouse"
        for (index in 0 until 10) {
            val tile = GridPoint(2 * (index / 10), 2 * (index % 10))
            assertEquals(GameEvent.Built(tile, 25.0), engine.build(tile, now).first())
        }
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.BuildingLimit("Pet House", 10))),
            engine.build(GridPoint(10, 10), now),
        )
        assertEquals(10, engine.state.buildings.size)
    }

    @Test fun `moving a pet house does not count against the limit`() {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.selectedBuildingID = "petHouse"
        for (index in 0 until 10) engine.build(GridPoint(2 * (index / 10), 2 * (index % 10)), now)
        engine.beginMoving(engine.building(GridPoint(0, 0))!!, GridPoint(30, 30))
        assertNull(engine.placementRejection(now))
    }

    @Test fun `pets survive a save and load`() {
        val engine = engine()
        engine.movePetIn(house, PetBreed.cat(grey), "Mittens")
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.pets, loaded.pets)
    }

    // Wandering

    @Test fun `a pet moves one tile in the given direction`() {
        val engine = engineWithPet()
        assertEquals(GridPoint(21, 21), engine.move(AnimalRef.Pet(house), right))
        assertEquals(GridPoint(21, 21), engine.state.pets[house]?.position)
    }

    @Test fun `a pet keeps its name when walking`() {
        val engine = engineWithPet()
        engine.state.pets[house] = engine.state.pets[house]!!.copy(name = "Mittens")
        engine.move(AnimalRef.Pet(house), right)
        assertEquals("Mittens", engine.state.pets[house]?.name)
    }

    @Test fun `a pet stops at water and buildings but walks onto other pets`() {
        val engine = engineWithPet()
        engine.state.water[GridPoint(21, 21)] = WaterStyle.medium
        assertNull(engine.move(AnimalRef.Pet(house), right))
        engine.state.water.clear()
        engine.state.pets[GridPoint(40, 40)] = Pet(breed = "grey", name = "x", position = GridPoint(21, 21))
        assertEquals(GridPoint(21, 21), engine.move(AnimalRef.Pet(house), right))
        engine.state.pets.remove(GridPoint(40, 40))
        engine.state.putBuilding(GridPoint(21, 21), "barn1")
        assertNull(engine.move(AnimalRef.Pet(house), right))
    }

    @Test fun `a pet moves and may wander far from its house`() {
        val at = Instant.ofEpochSecond(12_000_000)
        val engine = engineWithPet()
        engine.setTimeOfDay(720.0, at)
        val random = SeededRandom(1)
        val visited = mutableSetOf<GridPoint>()
        var farthest = 0
        repeat(2_000) {
            val direction = engine.wanderDirection(AnimalRef.Pet(house), at, random) ?: return@repeat
            engine.move(AnimalRef.Pet(house), direction)
            val position = engine.state.pets[house]!!.position
            visited += position
            farthest = max(farthest, max(abs(position.row - house.row), abs(position.col - house.col)))
        }
        assertTrue(visited.size > 1)
        assertTrue(farthest > 5)
    }

    @Test fun `a pet five tiles from its house can keep walking`() {
        val engine = engineWithPet(at = GridPoint(20, 25))
        assertEquals(GridPoint(20, 26), engine.move(AnimalRef.Pet(house), right))
    }

    @Test fun `directions skip water neighbors`() {
        val engine = engineWithPet(at = GridPoint(20, 25))
        engine.state.water[GridPoint(19, 25)] = WaterStyle.medium
        val random = SeededRandom(2)
        val allowed = listOf(GridPoint(0, -1), GridPoint(1, 0), right)
        repeat(30) {
            assertTrue(engine.wanderDirection(AnimalRef.Pet(house), using = random) in allowed)
        }
    }

    @Test fun `a boxed in pet has no direction`() {
        val engine = engineWithPet()
        for (neighbor in GridPoint(21, 20).neighbors) {
            if (neighbor != house) engine.state.water[neighbor] = WaterStyle.medium
        }
        assertNull(engine.wanderDirection(AnimalRef.Pet(house), using = SeededRandom(3)))
    }

    @Test fun `directions lean toward crops`() {
        val engine = engineWithPet()
        engine.state.plots[GridPoint(21, 23)] = Plot(Crop.starter.id, Instant.now())
        val random = SeededRandom(4)
        val towards = (0 until 1000).count { engine.wanderDirection(AnimalRef.Pet(house), using = random) == right }
        assertTrue(towards > 350)
    }

    // Crop rush

    private val rushNow = Instant.ofEpochSecond(5_000_000)
    private val tile = GridPoint(24, 24)
    private val rushHouse = GridPoint(10, 10)

    private fun engineWithPetOnCrop(): GameEngine {
        val engine = GameEngine()
        engine.tap(tile, rushNow)
        engine.state.pets[rushHouse] = Pet(breed = "grey", name = "Cat", position = tile)
        return engine
    }

    @Test fun `a pet never rushes a crop it is not standing on`() {
        val engine = GameEngine()
        engine.tap(tile, rushNow)
        engine.state.pets[rushHouse] = Pet(breed = "grey", name = "Cat", position = GridPoint(1, 1))
        val random = SeededRandom(5)
        repeat(200) {
            assertTrue(engine.petTendsCrop(rushHouse, 100.0, 1.0, rushNow, random).isEmpty())
        }
    }

    @Test fun `a long stay eventually ripens the crop`() {
        val engine = engineWithPetOnCrop()
        val random = SeededRandom(6)
        var events: List<GameEvent> = emptyList()
        var tries = 0
        while (events.isEmpty() && tries++ < 5_000) {
            events = engine.petTendsCrop(rushHouse, 100.0, 1.0, rushNow, random)
        }
        assertEquals(listOf<GameEvent>(GameEvent.CropRushed(tile)), events)
        val plot = engine.state.plots[tile]!!
        assertTrue(GrowthConditions(engine.state).isReady(plot, Crop.starter, tile, rushNow))
    }

    @Test fun `rushing is rare even after a good stay`() {
        val random = SeededRandom(7)
        val rushed =
            (0 until 2_000).count {
                val engine = engineWithPetOnCrop()
                (1..8).any { second ->
                    engine.petTendsCrop(rushHouse, second.toDouble(), 1.0, rushNow, random).isNotEmpty()
                }
            }
        assertTrue(rushed < 300)
    }

    @Test fun `a fresh arrival almost never ripens it`() {
        val random = SeededRandom(8)
        val rushed =
            (0 until 500).count {
                engineWithPetOnCrop().petTendsCrop(rushHouse, 0.0, 1.0, rushNow, random).isNotEmpty()
            }
        assertEquals(0, rushed)
    }

    @Test fun `the chance grows with the time spent on the crop`() {
        val random = SeededRandom(9)

        fun rushes(dwell: Double) =
            (0 until 5000).count {
                engineWithPetOnCrop().petTendsCrop(rushHouse, dwell, 1.0, rushNow, random).isNotEmpty()
            }
        assertTrue(rushes(3.0) < rushes(15.0))
    }

    @Test fun `a finished crop is left alone`() {
        val engine = engineWithPetOnCrop()
        val random = SeededRandom(10)
        val later = rushNow.advancedBy(Crop.starter.growDuration)
        repeat(500) {
            assertTrue(engine.petTendsCrop(rushHouse, 100.0, 1.0, later, random).isEmpty())
        }
    }

    @Test fun `a pet never ripens a fruit tree`() {
        val engine = GameEngine(stateWith(money = 1_000.0, xp = LevelRules.xpToReach(4), mode = PlayMode.plant))
        engine.state.selectedCropID = "apple"
        engine.beginPlacingFruitTree(GridPoint(30, 30))
        engine.confirmPlacement(rushNow)
        val standing =
            engine.state.plots.keys
                .first()
        engine.state.pets[rushHouse] = Pet(breed = "grey", name = "Cat", position = standing)
        val random = SeededRandom(11)
        repeat(2_000) {
            assertTrue(engine.petTendsCrop(rushHouse, 100.0, 1.0, rushNow, random).isEmpty())
        }
    }
}
