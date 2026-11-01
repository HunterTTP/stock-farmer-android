package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.beginMoving
import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Dog
import com.hunter.stockfarmer.core.catalog.DogBreed
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.PetBreed
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.hatch
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

/** The Swift `ChickenTests`, `DogTests` and `RoosterTests`: coops, their chickens, dogs and roosters. */
class ChickenEngineTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val coop = GridPoint(20, 20)
    private val interval = Coop.HATCH_INTERVAL

    private fun engineWithCoop(): GameEngine {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.selectedBuildingID = "chickenCoop"
        engine.build(coop, Instant.ofEpochSecond(1))
        return engine
    }

    private fun GameEngine.setChickens(chickens: List<Chicken>) {
        state.coops[coop] = state.coops[coop]!!.copy(chickens = chickens)
    }

    @Test fun `the default hatching pool keeps its seven hens`() {
        assertEquals(7, ChickenVariant.all.size)
        assertTrue(ChickenVariant.all.all { it.id.startsWith("hen-") })
        assertEquals(
            7,
            ChickenVariant.all
                .map { it.id }
                .toSet()
                .size,
        )
    }

    @Test fun `the coop is limited to four and keeps its other stats`() {
        val building = Building.named("chickenCoop")!!
        assertTrue(building.isChickenCoop)
        assertEquals(4, building.placementLimit)
        assertEquals("Chicken Coop", building.name)
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.selectedBuildingID = "chickenCoop"
        for (index in 0 until 4) engine.build(GridPoint(3 * index, 5), now)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.BuildingLimit("Chicken Coop", 4))),
            engine.build(GridPoint(40, 40), now),
        )
    }

    @Test fun `the first chick hatches at once in front of the coop`() {
        val engine = engineWithCoop()
        val events = engine.hatch(now)
        val front = GridPoint(22, 20)
        assertEquals(listOf<GameEvent>(GameEvent.Hatched(front)), events)
        val chicken = engine.chickens(coop)
        assertEquals(1, chicken.size)
        assertEquals(front, chicken[0].position)
        assertEquals(now, chicken[0].hatchedAt)
    }

    @Test fun `more hatch over time but not before they are due`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        assertTrue(engine.hatch(now.advancedBy(interval - 1)).isEmpty())
        assertEquals(1, engine.hatch(now.advancedBy(interval)).size)
        assertEquals(2, engine.chickens(coop).size)
    }

    @Test fun `time away hatches everything that came due`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        engine.hatch(now.advancedBy(interval * 3))
        assertEquals(2, engine.chickens(coop).size)
    }

    @Test fun `a level one coop holds two chickens and then stops hatching`() {
        val engine = engineWithCoop()
        for (step in 0 until 40) engine.hatch(now.advancedBy(interval * step))
        assertEquals(2, engine.homeCapacity(coop))
        assertEquals(2, engine.chickens(coop).size)
        assertTrue(engine.hatch(now.advancedBy(interval * 100)).isEmpty())
    }

    @Test fun `selling from a full coop restarts the hatch timer`() {
        val engine = engineWithCoop()
        for (step in 0 until 10) engine.hatch(now.advancedBy(interval * step))
        val later = now.advancedBy(interval * 50)
        engine.hatch(later)
        val first = engine.chickens(coop).first()
        engine.releaseChicken(first.id, coop)
        assertTrue(engine.hatch(later).isEmpty())
        assertEquals(interval, engine.state.coops[coop]?.timeUntilNextChick(later, 2))
        assertEquals(1, engine.hatch(later.advancedBy(interval)).size)
        assertEquals(2, engine.chickens(coop).size)
    }

    @Test fun `chicks grow up after ten minutes`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        val chicken = engine.chickens(coop).first()
        assertFalse(chicken.isGrown(now.advancedBy(Chicken.GROW_DURATION - 1)))
        assertTrue(chicken.isGrown(now.advancedBy(Chicken.GROW_DURATION)))
    }

    @Test fun `each chicken gets its own random variant`() {
        val variants = mutableSetOf<String>()
        val random = SeededRandom(9)
        repeat(40) {
            val engine = engineWithCoop()
            for (step in 0 until 10) engine.updateCoops(now.advancedBy(interval * step), random)
            assertTrue(engine.chickens(coop).all { ChickenVariant.named(it.variant) != null })
            variants += engine.chickens(coop).map { it.variant }
        }
        assertTrue(variants.size > 3)
    }

    @Test fun `releasing a chicken removes it for no money or xp`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        val chick = engine.chickens(coop).first()
        val money = engine.state.money
        val xp = engine.state.xp
        assertEquals(listOf<GameEvent>(GameEvent.AnimalReleased(chick.position)), engine.releaseChicken(chick.id, coop))
        assertTrue(engine.state.money == money && engine.state.xp == xp && engine.state.salesToday.isEmpty())
        assertFalse(engine.hasWorked(chick.variant))
        assertTrue(engine.chickens(coop).isEmpty())
        assertTrue(engine.releaseChicken(chick.id, coop).isEmpty())
    }

    @Test fun `a chick knows how long until it grows up`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        val chick = engine.chickens(coop).first()
        assertEquals(Chicken.GROW_DURATION, chick.timeUntilGrown(now))
        assertEquals(Chicken.GROW_DURATION - 100, chick.timeUntilGrown(now.plusSeconds(100)))
        assertEquals(0.0, chick.timeUntilGrown(now.advancedBy(Chicken.GROW_DURATION * 2)))
    }

    @Test fun `a coop knows how long until the next chick`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        val coopState = engine.state.coops[coop]!!
        assertEquals(interval, coopState.timeUntilNextChick(now, 2))
        assertEquals(interval - 60, coopState.timeUntilNextChick(now.plusSeconds(60), 2))
        for (step in 1 until 12) engine.hatch(now.advancedBy(interval * step))
        assertNull(engine.state.coops[coop]?.timeUntilNextChick(now, 2))
    }

    @Test fun `chickens can be named and blank names fall back`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        val ref = AnimalRef.Chicken(coop, engine.chickens(coop).first().id)
        engine.rename(ref, "  Clucky ")
        assertEquals("Clucky", engine.name(ref))
        engine.rename(ref, "   ")
        assertEquals(Chicken.DEFAULT_NAME, engine.name(ref))
    }

    @Test fun `chickens may wander beyond five tiles of their coop`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        val animal = AnimalRef.Chicken(coop, engine.chickens(coop).first().id)
        engine.setTimeOfDay(720.0, now)
        val random = SeededRandom(1)
        var farthest = 0
        repeat(2_000) {
            val direction = engine.wanderDirection(animal, now, random) ?: return@repeat
            engine.move(animal, direction)
            val position = engine.position(animal)!!
            farthest = max(farthest, max(abs(position.row - coop.row), abs(position.col - coop.col)))
        }
        assertTrue(farthest > 5)
    }

    @Test fun `chickens and pets share tiles`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        val chicken = engine.chickens(coop).first()
        engine.state.putBuilding(GridPoint(30, 30), "petHouse")
        engine.state.pets[GridPoint(30, 30)] =
            Pet(breed = "grey", name = "x", position = GridPoint(chicken.position.row, chicken.position.col + 1))
        assertEquals(
            GridPoint(chicken.position.row, chicken.position.col + 1),
            engine.move(AnimalRef.Chicken(coop, chicken.id), GridPoint(0, 1)),
        )
    }

    @Test fun `chickens follow their coop when it moves and vanish with it`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        engine.beginMoving(engine.building(coop)!!, GridPoint(35, 35))
        val newCoop = engine.buildingPlacement!!.origin
        engine.confirmPlacement(now)
        assertTrue(engine.chickens(coop).isEmpty())
        assertNotNull(engine.chickens(newCoop).firstOrNull())
        assertTrue(engine.chickens(newCoop).all { it.position == GridPoint(newCoop.row + 2, newCoop.col) })
        engine.demolish(engine.building(newCoop)!!)
        assertTrue(engine.state.coops.isEmpty())
    }

    @Test fun `water on a chickens tile sends it back to the front of its coop`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        val front = engine.chickens(coop).first().position
        val strayed = GridPoint(front.row + 1, front.col)
        engine.setChickens(listOf(engine.chickens(coop)[0].copy(position = strayed)))
        engine.state.mode = PlayMode.landscape
        engine.select(LandscapeTool.water)
        engine.tap(strayed, now)
        assertEquals(front, engine.chickens(coop).first().position)
    }

    @Test fun `coops placed before chickens existed get their first chick`() {
        val engine = GameEngine()
        engine.state.putBuilding(coop, "chickenCoop")
        engine.hatch(now)
        assertEquals(1, engine.chickens(coop).size)
    }

    @Test fun `chickens survive a save and load`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.coops, loaded.coops)
    }

    @Test fun `every chicken lands on the same tile when the coop moves and then walks out`() {
        val engine = engineWithCoop()
        for (step in 0 until 2) engine.hatch(now.advancedBy(interval * step))
        engine.beginMoving(engine.building(coop)!!, GridPoint(35, 35))
        val newCoop = engine.buildingPlacement!!.origin
        engine.confirmPlacement(now)
        val landing = GridPoint(newCoop.row + 2, newCoop.col)
        val chickens = engine.chickens(newCoop)
        assertEquals(2, chickens.size)
        assertTrue(chickens.all { it.position == landing })
        val first = AnimalRef.Chicken(newCoop, chickens[0].id)
        assertEquals(GridPoint(landing.row + 1, landing.col), engine.move(first, GridPoint(1, 0)))
    }

    @Test fun `chickens share tiles with each other whatever their age`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        val variant = ChickenVariant.all[0]
        val tileA = GridPoint(24, 20)
        val tileB = GridPoint(24, 21)
        val grown = now.advancedBy(-Chicken.GROW_DURATION - 1)
        val adults =
            listOf(
                Chicken.hatched(100, variant, "A", tileA, grown),
                Chicken.hatched(101, variant, "B", tileB, grown),
            )
        val chick = Chicken.hatched(102, variant, "C", GridPoint(24, 22), now)
        engine.setChickens(adults + chick)
        val east = GridPoint(0, 1)
        val west = GridPoint(0, -1)
        // Grown chickens share a tile with each other.
        assertEquals(tileB, engine.move(AnimalRef.Chicken(coop, 100), east))
        // A chick steps onto a grown chicken's tile.
        assertEquals(tileB, engine.move(AnimalRef.Chicken(coop, 102), west))
        // And a grown chicken can step onto a chick's tile.
        assertEquals(GridPoint(24, 22), engine.move(AnimalRef.Chicken(coop, 101), east))
    }

    @Test fun `chickens would rather walk to a tile without chickens`() {
        val engine = engineWithCoop()
        engine.hatch(now)
        val variant = ChickenVariant.all[0]
        val hatched = now.advancedBy(-Chicken.GROW_DURATION - 1)
        engine.setChickens(
            listOf(
                Chicken.hatched(100, variant, "A", GridPoint(24, 20), hatched),
                Chicken.hatched(101, variant, "B", GridPoint(24, 21), hatched),
                Chicken.hatched(102, variant, "C", GridPoint(24, 21), hatched),
            ),
        )
        engine.state.water[GridPoint(23, 20)] = WaterStyle.deep
        engine.state.water[GridPoint(25, 20)] = WaterStyle.deep
        val random = SeededRandom(2)
        val towardEmpty =
            (0 until 1_000).count {
                engine.wanderDirection(AnimalRef.Chicken(coop, 100), now, random) == GridPoint(0, -1)
            }
        assertTrue(towardEmpty > 750)
        assertTrue(towardEmpty < 1_000)
    }

    // Dogs

    private val house = GridPoint(20, 20)

    private fun dogEngine(level: Int = LevelRules.maxLevel): GameEngine {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = LevelRules.xpToReach(level), mode = PlayMode.build))
        engine.state.selectedBuildingID = "petHouse"
        engine.build(house, now)
        return engine
    }

    @Test fun `there are nine dogs in three breeds`() {
        assertEquals(9, Dog.all.size)
        assertEquals(
            9,
            Dog.all
                .map { it.id }
                .toSet()
                .size,
        )
        assertTrue(DogBreed.entries.all { it.dogs.size == 3 })
        assertEquals("Gray Basenji", Dog.named("basenji-gray")?.name)
        assertEquals((0 until 9).toList(), Dog.all.map { it.doghouseRow }.sorted())
    }

    @Test fun `a dog moves into a pet house like a cat`() {
        val engine = dogEngine()
        val dog = Dog.named("labrador-white")!!
        val front = GridPoint(21, 20)
        assertEquals(listOf<GameEvent>(GameEvent.Adopted(front)), engine.movePetIn(house, PetBreed.dog(dog), "Rex"))
        val pet = engine.pet(house)!!
        assertEquals(Pet(PetSpecies.dog, "labrador-white", "Rex", front), pet)
        assertEquals(dog, pet.dog)
        assertEquals(PetBreed.dog(dog), pet.kind)
        assertEquals(AnimalRef.Pet(house), engine.animalStandingAt(front))
    }

    @Test fun `a dog without a name is called by its breed and color`() {
        val engine = dogEngine()
        engine.movePetIn(house, PetBreed.dog(Dog.starter), "  ")
        assertEquals("Brown Labrador", engine.pet(house)?.name)
        engine.renamePet(house, "")
        assertEquals("Brown Labrador", engine.pet(house)?.name)
    }

    @Test fun `every dog can move in from the start and one house holds one pet`() {
        val engine = dogEngine()
        engine.state.xp = 0
        val shepherd = Dog.named("germanShepherd-gray")!!
        assertEquals(1, engine.movePetIn(house, PetBreed.dog(shepherd), "x").size)
        engine.state.pets.remove(house)
        engine.movePetIn(house, PetBreed.dog(Dog.starter), "First")
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.HouseOccupied)),
            engine.movePetIn(house, PetBreed.cat(Cat.starter), "Second"),
        )
    }

    @Test fun `a dog can ripen a crop it stands on just as a cat can`() {
        val engine = dogEngine()
        engine.state.farmland += GridPoint(21, 20)
        engine.state.mode = PlayMode.plant
        engine.tap(GridPoint(21, 20), now)
        engine.movePetIn(house, PetBreed.dog(Dog.starter), "Rex")
        engine.state.pets[house] = engine.state.pets[house]!!.copy(position = GridPoint(21, 20))
        val random = SeededRandom(5)
        var ripened = false
        var tries = 0
        while (!ripened && tries++ < 2_000) {
            ripened =
                engine
                    .petTendsCrop(house, 20.0, 1.0, now.plusSeconds(1), random)
                    .contains(GameEvent.CropRushed(GridPoint(21, 20)))
        }
        assertTrue(ripened)
    }

    @Test fun `dogs survive a save and load`() {
        val engine = dogEngine()
        engine.movePetIn(house, PetBreed.dog(Dog.named("basenji-orange")!!), "Rex")
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.pets, loaded.pets)
        assertEquals("basenji-orange", loaded.pets[house]?.dog?.id)
    }

    // Roosters

    private val home = GridPoint(15, 15)

    @Test fun `optional roosters have new ids and old rooster ids map to hens`() {
        assertTrue(ChickenVariant.all.size == 7 && ChickenVariant.all.none { it.isRooster })
        assertEquals(7, ChickenVariant.roosters.size)
        for (rooster in ChickenVariant.roosters) {
            assertTrue(rooster.isRooster && rooster.id.startsWith("cock-"))
            assertEquals(rooster, ChickenVariant.named(rooster.id))
        }
        assertEquals(ChickenVariant.named("hen-golden"), ChickenVariant.named("rooster-golden"))
    }

    @Test fun `selected roosters hatch without changing existing chickens and save with their coop`() {
        var engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP, logs = 1_000))
        engine.select(Building.named("chickenCoop")!!)
        engine.build(home, now)
        engine.hatch(now)
        val existing =
            engine.state.coops[home]!!
                .chickens
                .first()
        assertTrue(engine.chooseChickVariant("cock-golden", home).isEmpty())
        engine = GameEngine(GameStateJson.decode(GameStateJson.encode(engine.state)))
        assertEquals("cock-golden", engine.state.coops[home]?.nextVariantID)
        engine.hatch(now.advancedBy(interval))
        assertEquals(
            existing,
            engine.state.coops[home]
                ?.chickens
                ?.first(),
        )
        assertEquals(
            true,
            engine.state.coops[home]
                ?.chickens
                ?.last()
                ?.kind
                ?.isRooster,
        )
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NothingHere)),
            engine.chooseChickVariant("not-a-breed", home),
        )
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NothingHere)),
            engine.chooseChickVariant(null, GridPoint(0, 0)),
        )
        assertTrue(engine.chooseChickVariant(null, home).isEmpty())
        assertNull(engine.state.coops[home]?.nextVariantID)
    }

    @Test fun `ornamental roosters never lay eggs`() {
        val engine = GameEngine(stateWith(logs = 0))
        engine.state.coops[home] =
            Coop.hatchingAt(now).copy(
                chickens =
                    listOf(
                        Chicken.hatched(
                            0,
                            ChickenVariant.named("cock-golden")!!,
                            "Rooster",
                            home,
                            now.advancedBy(-10_000.0),
                        ),
                    ),
            )
        engine.layEggs(100_000.0, now, SeededRandom(2))
        assertTrue(engine.state.eggs.isEmpty())
    }
}
