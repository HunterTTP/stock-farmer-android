package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.beginMoving
import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LivestockHome
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.LivestockWork
import com.hunter.stockfarmer.core.catalog.PlacedBuilding
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.eatsToProduce
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.updateHerds
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class LivestockCatalogTest {
    @Test
    fun `there are seven species with every picture in their folder used once`() {
        assertEquals(
            listOf("rabbit", "duck", "sheep", "goat", "pig", "donkey", "cow"),
            LivestockSpecies.all.map { it.id },
        )
        assertEquals(listOf(6, 4, 11, 4, 5, 4, 5), LivestockSpecies.all.map { it.variants.size })
        val ids = LivestockSpecies.all.flatMap { it.variants }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            ids.size,
            LivestockSpecies.all
                .flatMap { it.variants }
                .map { it.assetName }
                .toSet()
                .size,
        )
        assertEquals(LivestockSpecies.cow, LivestockSpecies.variant("cow:bigWhite")?.first)
        assertNull(LivestockSpecies.variant("nonsense"))
    }

    @Test
    fun `each building holds its kind of animal and they all unlock in order`() {
        val homes = LivestockHome.all.associate { it.buildingID to it.species.map { s -> s.id } }
        assertEquals(
            mapOf(
                "rabbitHutch" to listOf("rabbit"),
                "barn1" to listOf("sheep"),
                "goatBarn" to listOf("goat"),
                "pigBarn" to listOf("pig"),
                "stable1" to listOf("donkey"),
                "barn2" to listOf("cow"),
            ),
            homes,
        )
        for (home in LivestockHome.all) {
            val building = Building.named(home.buildingID)!!
            assertEquals(home, building.livestockHome)
            assertTrue(building.needsFrontTile)
        }
        assertEquals(listOf(LivestockSpecies.cow), Building.named("barn2")?.livestockHome?.species)
        assertNull(Building.named("shed")?.livestockHome)
    }

    @Test
    fun `the bigger animals take longer to grow`() {
        val order =
            listOf(
                LivestockSpecies.rabbit,
                LivestockSpecies.duck,
                LivestockSpecies.sheep,
                LivestockSpecies.goat,
                LivestockSpecies.pig,
                LivestockSpecies.donkey,
                LivestockSpecies.cow,
            )
        assertEquals(order.map { it.growDuration }.sorted(), order.map { it.growDuration })
    }

    @Test
    fun `variants that have sheared pictures are the woolly sheep`() {
        assertEquals(6, LivestockSpecies.sheep.variants.count { it.hasShearedPictures })
        assertTrue(
            LivestockSpecies.all.filter { it != LivestockSpecies.sheep }.all { s ->
                s.variants.all { !it.hasShearedPictures }
            },
        )
    }

    @Test
    fun `buildings for animals are limited to one`() {
        for (id in listOf("stable1", "rabbitHutch", "barn1", "goatBarn", "pigBarn", "barn2")) {
            assertEquals(1, Building.named(id)?.placementLimit)
        }
    }
}

class HerdTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val spot = GridPoint(20, 20)

    /** A game with one livestock building of the kind asked for. */
    private fun engine(buildingID: String = "barn2"): GameEngine {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.selectedBuildingID = buildingID
        engine.build(spot, Instant.ofEpochSecond(1))
        return engine
    }

    private fun home(engine: GameEngine): PlacedBuilding = engine.state.placedBuildings[0]

    @Test
    fun `the first baby is born at once in front of the building`() {
        val engine = engine()
        val placed = home(engine)
        val front = placed.building.frontTile(placed.origin)
        assertEquals(listOf<GameEvent>(GameEvent.Born(front)), engine.updateHerds(now))
        val animals = engine.animals(placed.origin)
        assertEquals(1, animals.size)
        assertEquals(front, animals[0].position)
        assertEquals(now, animals[0].bornAt)
        assertEquals(LivestockSpecies.cow, animals[0].species)
        assertEquals("Cow", animals[0].name)
        assertEquals(AnimalRef.Livestock(placed.origin, animals[0].id), engine.animalStandingAt(front))
    }

    @Test
    fun `more are born over time up to the buildings level then stop`() {
        val engine = engine()
        val placed = home(engine)
        val interval = placed.building.livestockHome!!.birthInterval
        engine.updateHerds(now)
        assertTrue(engine.updateHerds(now.advancedBy(interval - 1)).isEmpty())
        assertEquals(1, engine.updateHerds(now.advancedBy(interval)).size)
        for (step in 0 until 40) engine.updateHerds(now.advancedBy(interval * step))
        assertEquals(4, engine.animals(placed.origin).size)
        assertTrue(engine.updateHerds(now.advancedBy(interval * 100)).isEmpty())
    }

    @Test
    fun `time away brings every baby that came due`() {
        val engine = engine("rabbitHutch")
        engine.updateHerds(now)
        engine.updateHerds(now.advancedBy(4.0 * 60 * 3))
        assertEquals(4, engine.animals(home(engine).origin).size)
    }

    @Test
    fun `babies grow up after their species time`() {
        val engine = engine()
        val origin = home(engine).origin
        engine.updateHerds(now)
        val calf = engine.animals(origin).first()
        assertFalse(calf.isGrown(now.advancedBy(LivestockSpecies.cow.growDuration - 1)))
        assertTrue(calf.isGrown(now.advancedBy(LivestockSpecies.cow.growDuration)))
        assertEquals(LivestockSpecies.cow.growDuration, engine.timeUntilGrown(calf, now))
    }

    @Test
    fun `a building with several kinds bears each of them`() {
        val engine = engine("barn1")
        engine.updateHerds(now)
        engine.updateHerds(now.advancedBy(8.0 * 60 * 200))
        val kinds = engine.animals(home(engine).origin).map { it.species.id }.toSet()
        assertTrue(setOf("sheep", "goat", "pig").containsAll(kinds))
        assertEquals(4, engine.animals(home(engine).origin).size)
    }

    @Test
    fun `releasing earns nothing and the timer starts afresh on a full building`() {
        val engine = engine("stable1")
        val origin = home(engine).origin
        val interval = home(engine).building.livestockHome!!.birthInterval
        for (step in 0 until 10) engine.updateHerds(now.advancedBy(interval * step))
        val later = now.advancedBy(interval * 50)
        engine.updateHerds(later)
        assertEquals(4, engine.animals(origin).size)
        val foal = engine.animals(origin).first()
        engine.state.storage = mutableMapOf()
        val money = engine.state.money
        val xp = engine.state.xp
        assertEquals(
            listOf<GameEvent>(GameEvent.AnimalReleased(foal.position)),
            engine.releaseLivestock(foal.id, origin),
        )
        assertTrue(engine.state.money == money && engine.state.xp == xp && engine.state.salesToday.isEmpty())
        assertFalse(engine.hasWorked(foal.variant))
        assertEquals(3, engine.animals(origin).size)
        assertTrue(engine.updateHerds(later).isEmpty())
        assertEquals(1, engine.updateHerds(later.advancedBy(interval)).size)
    }

    @Test
    fun `demolishing or moving a building takes its animals along`() {
        val engine = engine("rabbitHutch")
        engine.updateHerds(now)
        val placed = home(engine)
        engine.beginMoving(placed, GridPoint(30, 30))
        val target = engine.buildingPlacement!!.origin
        engine.confirmPlacement(now)
        assertNull(engine.state.herds[placed.origin])
        val animal = engine.animals(target).first()
        assertEquals(GridPoint(target.row + 2, target.col + 1), animal.position)
        engine.demolish(engine.building(target)!!)
        assertTrue(engine.state.herds.isEmpty())
    }

    @Test
    fun `names are trimmed limited and blank ones fall back to the kind`() {
        val engine = engine()
        val origin = home(engine).origin
        engine.updateHerds(now)
        val ref = AnimalRef.Livestock(origin, 0)
        engine.rename(ref, "  Daisy  ")
        assertEquals("Daisy", engine.name(ref))
        engine.rename(ref, "")
        assertEquals("Cow", engine.name(ref))
        engine.rename(ref, "x".repeat(50))
        assertEquals(Livestock.MAX_NAME_LENGTH, engine.name(ref)?.length)
    }

    @Test
    fun `pets walk onto the tile of a grown or baby animal`() {
        val engine = engine()
        val origin = home(engine).origin
        engine.updateHerds(now)
        val ref = AnimalRef.Livestock(origin, 0)
        val tile = engine.position(ref)!!
        engine.state.pets[GridPoint(5, 5)] = Pet(breed = "grey", name = "x", position = GridPoint(0, 0))
        val pet = AnimalRef.Pet(GridPoint(5, 5))
        assertTrue(engine.canStand(tile, pet))
        engine.updateHerds(now.advancedBy(LivestockSpecies.cow.growDuration))
        assertTrue(engine.canStand(tile, pet))
    }

    @Test
    fun `herds survive a save`() {
        val engine = engine()
        engine.updateHerds(now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.herds, loaded.herds)
        assertTrue(GameStateJson.decode("""{"money": 5}""").herds.isEmpty())
    }

    @Test
    fun `buildings for animals need a free front tile`() {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.selectedBuildingID = "barn2"
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedsFrontTile)),
            engine.build(GridPoint(49, 20), now),
        )
    }
}

class LivestockWorkTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val home = GridPoint(20, 20)
    private val born = Instant.ofEpochSecond(1)

    /** A building of the kind asked for, holding one grown animal of a chosen kind that stands on [tile]. */
    private fun engine(
        buildingID: String,
        variantID: String,
        tile: GridPoint,
    ): GameEngine {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))
        engine.state.selectedBuildingID = buildingID
        engine.build(home, born)
        val origin = engine.state.placedBuildings[0].origin
        val variant = LivestockSpecies.variant(variantID)!!.second
        val animal = Livestock.born(0, variant, "x", tile, born).copy(meal = AnimalMeal(born))
        engine.state.herds[origin] = Herd(listOf(animal), now.advancedBy(1_000_000.0), nextAnimalID = 1)
        engine.state.storage = mutableMapOf()
        return engine
    }

    private fun grown(species: LivestockSpecies): Instant = born.advancedBy(species.growDuration + species.workInterval)

    private fun GameEngine.firstAnimal(): Livestock = animals(state.placedBuildings[0].origin)[0]

    private fun GameEngine.changeFirstAnimal(change: (Livestock) -> Livestock) {
        val origin = state.placedBuildings[0].origin
        updateLivestock(origin, 0, change)
    }

    @Test
    fun `the bucket milks a cow once it is ready and not before`() {
        val tile = GridPoint(30, 30)
        val engine = engine("barn2", "cow:bigWhite", tile)
        engine.select(HarvestTool.bucket)
        assertTrue(engine.useTool(tile, grown(LivestockSpecies.cow).advancedBy(-1.0)).isEmpty())
        val ready = grown(LivestockSpecies.cow)
        assertEquals(StorageItem.Milk, engine.readyProduce(tile, ready)?.first)
        assertEquals(listOf<GameEvent>(GameEvent.Stored(tile, "milk", 0)), engine.useTool(tile, ready))
        assertEquals(mapOf("milk" to 1), engine.state.storage)
        assertEquals(ready, engine.firstAnimal().lastWorkedAt)
        assertTrue(engine.useTool(tile, ready).isEmpty())
        assertNull(engine.readyProduce(tile, ready))
        val next = ready.advancedBy(LivestockSpecies.cow.workInterval)
        assertTrue(engine.useTool(tile, next).isEmpty())
        engine.changeFirstAnimal { it.copy(meal = AnimalMeal(ready.advancedBy(1.0))) }
        assertEquals(1, engine.useTool(tile, next).size)
    }

    @Test
    fun `a baby has nothing to give even if its time is up`() {
        val tile = GridPoint(30, 30)
        val engine = engine("barn2", "cow:bigWhite", tile)
        engine.select(HarvestTool.bucket)
        val farFuture = born.advancedBy(10.0)
        engine.changeFirstAnimal { it.copy(readyAt = Instant.ofEpochSecond(0)) }
        assertTrue(engine.useTool(tile, farFuture).isEmpty())
    }

    @Test
    fun `the scythe shears sheep and the bucket milks goats and nothing else does`() {
        val tile = GridPoint(30, 30)
        val sheep = engine("barn1", "sheep:woollyWhite", tile)
        sheep.select(HarvestTool.bucket)
        assertTrue(sheep.useTool(tile, grown(LivestockSpecies.sheep)).isEmpty())
        sheep.select(HarvestTool.scythe)
        assertEquals(
            listOf<GameEvent>(GameEvent.Stored(tile, "wool", 0)),
            sheep.useTool(tile, grown(LivestockSpecies.sheep)),
        )
        val goat = engine("barn1", "goat:white", tile)
        goat.select(HarvestTool.bucket)
        assertEquals(
            listOf<GameEvent>(GameEvent.Stored(tile, "goatMilk", 0)),
            goat.useTool(tile, grown(LivestockSpecies.goat)),
        )
        assertTrue(
            StorageItem.Wool.sellValue == 30.0 && StorageItem.GoatMilk.sellValue == 18.0 &&
                StorageItem.Milk.sellValue == 60.0,
        )
    }

    @Test
    fun `taking what animals have ready earns XP by the level their home unlocks at`() {
        val tile = GridPoint(30, 30)
        val cases =
            listOf(
                Triple("barn1", "sheep:woollyWhite", StorageItem.Wool to HarvestTool.scythe),
                Triple("goatBarn", "goat:white", StorageItem.GoatMilk to HarvestTool.bucket),
                Triple("barn2", "cow:bigWhite", StorageItem.Milk to HarvestTool.bucket),
                Triple("rabbitHutch", "rabbit:white", StorageItem.Fertilizer to HarvestTool.bucket),
            )
        for ((building, animal, produce) in cases) {
            val (item, tool) = produce
            val engine = engine(building, animal, tile)
            engine.state.xp = 0
            engine.select(tool)
            val species = LivestockSpecies.variant(animal)!!.first
            val xp = item.unlockLevel + 1
            assertTrue(LevelRules.produceXP(item, 1) == xp && xp >= 2)
            assertEquals(listOf<GameEvent>(GameEvent.Stored(tile, item.id, xp)), engine.useTool(tile, grown(species)))
            assertEquals(xp, engine.state.xp)
        }
    }

    @Test
    fun `animal produce earns nothing at the last level and can level the player up`() {
        val tile = GridPoint(30, 30)
        val engine = engine("barn2", "cow:bigWhite", tile)
        engine.select(HarvestTool.bucket)
        assertTrue(
            engine.state.level == LevelRules.maxLevel &&
                LevelRules.produceXP(StorageItem.Milk, LevelRules.maxLevel) == 0,
        )
        engine.state.xp = LevelRules.xpToReach(2) - 1
        engine.changeFirstAnimal { it.copy(readyAt = Instant.ofEpochSecond(0)) }
        val events = engine.useTool(tile, grown(LivestockSpecies.cow))
        assertTrue(engine.state.level >= 2 && events.any { it is GameEvent.LeveledUp })
    }

    @Test
    fun `the wrong tool says which one to use`() {
        val tile = GridPoint(30, 30)
        val engine = engine("barn2", "cow:bigWhite", tile)
        val ready = grown(LivestockSpecies.cow)
        engine.select(HarvestTool.scythe)
        assertTrue(engine.useTool(tile, ready).isEmpty())
        assertEquals(Rejection.UseTheBucketForAnimals, engine.missingHarvestToolRejection(tile, ready))
        engine.select(HarvestTool.bucket)
        assertNull(engine.missingHarvestToolRejection(tile, ready))
        assertEquals(
            "Use the Bucket from the Harvest tool to collect what it has ready",
            Rejection.UseTheBucketForAnimals.message,
        )
        val sheep = engine("barn1", "sheep:white", tile)
        sheep.select(HarvestTool.bucket)
        assertEquals(
            Rejection.UseTheScytheForWool,
            sheep.missingHarvestToolRejection(tile, grown(LivestockSpecies.sheep)),
        )
        assertEquals("Use the Scythe from the Harvest tool to shear sheep", Rejection.UseTheScytheForWool.message)
        sheep.select(HarvestTool.scythe)
        assertNull(sheep.missingHarvestToolRejection(tile, grown(LivestockSpecies.sheep)))
    }

    @Test
    fun `gathering needs room unless the tool sells`() {
        val tile = GridPoint(30, 30)
        val engine = engine("barn2", "cow:bigWhite", tile)
        engine.select(HarvestTool.bucket)
        engine.state.storage = mutableMapOf("wheat" to engine.storageCapacity)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.StorageFull)),
            engine.useTool(tile, grown(LivestockSpecies.cow)),
        )
        engine.setSelling(true, HarvestTool.bucket)
        assertEquals(
            listOf<GameEvent>(GameEvent.Sold(tile, "milk", 60.0, 0)),
            engine.useTool(tile, grown(LivestockSpecies.cow)),
        )
    }

    @Test
    fun `ducks give an egg without eating then count down to the next one once it is taken`() {
        val tile = GridPoint(30, 30)
        val engine = GameEngine(stateWith(money = 1_000.0, xp = lateGameXP))
        engine.state.water[tile] = WaterStyle.deep
        engine.state.harvestTool = HarvestTool.bucket
        val variant = LivestockSpecies.variant("duck:white")!!.second
        val duckAnimal = Livestock.born(0, variant, "x", tile, born)
        engine.state.herds[tile] = Herd(listOf(duckAnimal), now.advancedBy(1_000_000.0))
        val duck = LivestockSpecies.duck
        assertTrue(duck.work == LivestockWork.Gives(StorageItem.Egg, HarvestTool.bucket) && !duck.eatsToProduce)
        assertTrue(duck.workInterval > Egg.LAY_INTERVAL)
        val beforeReady = born.advancedBy(duck.growDuration + duck.workInterval - 1)
        assertNull(engine.readyProduce(tile, beforeReady))
        val ready = beforeReady.advancedBy(1.0)
        assertEquals(StorageItem.Egg, engine.readyProduce(tile, ready)?.first)
        assertEquals(StorageItem.Egg, engine.readyProduce(tile, ready.advancedBy(duck.workInterval * 5))?.first)
        engine.collectProduce(tile, HarvestTool.bucket, ready.advancedBy(500.0))
        assertEquals(1, engine.state.storage[StorageItem.Egg.id])
        assertNull(engine.readyProduce(tile, ready.advancedBy(500 + duck.workInterval - 1)))
        assertEquals(StorageItem.Egg, engine.readyProduce(tile, ready.advancedBy(500 + duck.workInterval))?.first)
    }

    @Test
    fun `grown donkeys do not add storage and can be released while old storage is over capacity`() {
        val tile = GridPoint(30, 30)
        val engine = engine("stable1", "donkey:gray", tile)
        val origin = engine.state.placedBuildings[0].origin
        assertEquals(BACKPACK_CAPACITY, engine.storageCapacity)
        engine.updateHerds(born.advancedBy(LivestockSpecies.donkey.growDuration + 10))
        assertEquals(BACKPACK_CAPACITY, engine.storageCapacity)
        engine.state.storage = mutableMapOf("wheat" to 120)
        assertNull(engine.demolishRejection(engine.state.placedBuildings[0]))
        assertEquals(1, engine.releaseLivestock(0, origin).size)
        assertEquals(BACKPACK_CAPACITY, engine.storageCapacity)
    }
}

class FullBuildingTest {
    @Test
    fun `the buildings that can look full have a picture for it`() {
        val fulls = Building.all.filter { it.fullAssetName != null }
        assertEquals(
            listOf("grainMachine", "stoneOven", "woodworkBench", "tailorTable", "diyTable", "dairy", "cheeseCellar"),
            fulls.map { it.id },
        )
        assertTrue(fulls.all { it.fullAssetName?.endsWith("_full") == true && it.assetName.endsWith("_empty") })
        assertEquals(GameState().money, 0.0)
    }
}
