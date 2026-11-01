package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.Fertilizer
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LivestockHome
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.LivestockWork
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.eatsToProduce
import com.hunter.stockfarmer.core.model.favoriteCrop
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
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

class AnimalGrazingTest {
    private val now = Instant.ofEpochSecond(50_000_000)
    private val home = GridPoint(15, 15)
    private val point = GridPoint(24, 19)
    private val cow = AnimalRef.Livestock(home, 0)

    private fun engine(
        species: LivestockSpecies = LivestockSpecies.cow,
        grown: Boolean = true,
    ): GameEngine {
        val engine = GameEngine(stateWith(logs = 0))
        engine.state.putBuilding(home, "barn2")
        engine.setTimeOfDay(8.0 * 60, now)
        val born = if (grown) now.advancedBy(-species.growDuration) else now
        val animal = Livestock(0, species.variants[0].id, species.name, point, bornAt = born)
        engine.state.herds[home] = Herd(listOf(animal), now.advancedBy(100_000.0), processedAt = now)
        return engine
    }

    private fun modify(
        engine: GameEngine,
        change: (Livestock) -> Livestock,
    ) {
        val herd = engine.state.herds[home]!!
        engine.state.herds[home] = herd.copy(animals = listOf(change(herd.animals[0])))
    }

    private fun plant(
        id: String,
        at: GridPoint,
        engine: GameEngine,
        ripe: Boolean = true,
    ) {
        val crop = Crop.named(id)!!
        engine.state.farmland.add(at)
        engine.state.plots[at] = Plot(id, if (ripe) now.advancedBy(-crop.growDuration * 2) else now)
    }

    /** Feeds the animal standing on the point one of its favorite crop by hand. */
    private fun feed(
        id: String,
        engine: GameEngine,
    ) {
        val item = StorageItem.CropItem(Crop.named(id)!!)
        engine.state.storage[item.id] = 1
        engine.equip(item)
        engine.useTool(point, now)
    }

    @Test
    fun `a meal makes milk twice as fast and stops at its expiry`() {
        val engine = engine()
        feed("zucchini", engine)
        val random = SeededRandom(1)
        engine.updateHerds(now.advancedBy(180.0), random)
        assertEquals(StorageItem.Milk, engine.livestock(0, home)?.readyProduce(now.advancedBy(180.0))?.first)
        modify(engine) { it.copy(readyAt = now.advancedBy(1_000.0)) }
        engine.updateHerds(now.advancedBy(600.0), random)
        assertEquals(now.advancedBy(880.0), engine.livestock(0, home)?.readyAt)
    }

    @Test
    fun `baby livestock grow faster but meals never apply before they were eaten`() {
        val engine = engine(LivestockSpecies.rabbit, grown = false)
        modify(engine) { it.copy(meal = AnimalMeal(now)) }
        val random = SeededRandom(5)
        engine.updateHerds(now.advancedBy(240.0), random)
        assertTrue(engine.livestock(0, home)?.isGrown(now.advancedBy(240.0)) == true)
        val late = engine(LivestockSpecies.rabbit, grown = false)
        modify(late) { it.copy(meal = AnimalMeal(now.advancedBy(200.0))) }
        late.updateHerds(now.advancedBy(240.0), random)
        assertEquals(40.0, late.livestock(0, home)?.growthBonus)
    }

    @Test
    fun `every animal that gives something to harvest has a distinct favorite available by their homes level`() {
        val homeLevels =
            LivestockHome.all
                .flatMap { h -> h.species.map { it.id to (Building.named(h.buildingID)?.unlockLevel ?: 1) } }
                .toMap()
        val givers = LivestockSpecies.all.filter { it.eatsToProduce }
        assertEquals(setOf("rabbit", "sheep", "goat", "pig", "donkey", "cow"), givers.map { it.id }.toSet())
        assertTrue(LivestockSpecies.all.filter { it !in givers }.all { it.favoriteCrop == null })
        val favorites = givers.mapNotNull { it.favoriteCrop } + Chicken.favoriteCrop
        assertTrue(favorites.size == 7 && favorites.map { it.id }.toSet().size == 7)
        assertTrue(
            givers.all {
                !it.favoriteCrop!!.isFruitTree &&
                    it.favoriteCrop!!.unlockLevel <= (homeLevels[it.id] ?: 1)
            },
        )
        assertTrue(!Chicken.favoriteCrop.isFruitTree)
        assertTrue(Chicken.favoriteCrop.unlockLevel <= Building.named("chickenCoop")!!.unlockLevel)
    }

    @Test
    fun `meals survive saving and old animals load unfed`() {
        val engine = engine()
        val restored = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertNull(
            restored.herds[home]
                ?.animals
                ?.get(0)
                ?.meal,
        )
        plant("zucchini", point, engine)
        engine.eatFavoriteCrops(now)
        assertNotNull(engine.livestock(0, home)?.meal)
        assertTrue(sameState(GameStateJson.decode(GameStateJson.encode(engine.state)), engine.state))
    }

    @Test
    fun `ripe favorites are eaten once without harvest money XP or storage`() {
        val engine = engine()
        plant("zucchini", point, engine)
        assertEquals(1, engine.eatFavoriteCrops(now).size)
        assertTrue(engine.state.plots[point]?.servingsEaten == 1 && point in engine.state.farmland)
        assertTrue(engine.state.storage.isEmpty() && engine.state.xp == 0 && engine.state.money == 0.0)
        assertTrue(engine.livestock(0, home)?.meal?.isActive(now) == true)
        plant("zucchini", point, engine)
        assertTrue(engine.eatFavoriteCrops(now.advancedBy(1.0)).isEmpty())
        assertNotNull(engine.state.plots[point])
    }

    @Test
    fun `a tile feeds animals four times and is gone after the fourth`() {
        val engine = engine()
        plant("zucchini", point, engine)
        for (meal in 1..Plot.SERVINGS_PER_TILE) {
            modify(engine) { it.copy(meal = null) }
            assertEquals(
                listOf<GameEvent>(GameEvent.CropEaten(point, cow)),
                engine.eatFavoriteCrops(now),
            )
            val expected = if (meal < Plot.SERVINGS_PER_TILE) meal else null
            assertEquals(expected, engine.state.plots[point]?.servingsEaten)
        }
        assertTrue(engine.state.plots[point] == null && point in engine.state.farmland)
    }

    @Test
    fun `a partly eaten tile still harvests as one full crop`() {
        val engine = engine()
        plant("zucchini", point, engine)
        engine.eatFavoriteCrops(now)
        engine.select(HarvestTool.scythe)
        engine.useTool(point, now)
        assertTrue(engine.state.storage["zucchini"] == 1 && engine.state.plots[point] == null)
    }

    @Test
    fun `servings eaten are saved`() {
        val engine = engine()
        plant("zucchini", point, engine)
        engine.eatFavoriteCrops(now)
        assertEquals(1, GameStateJson.decode(GameStateJson.encode(engine.state)).plots[point]?.servingsEaten)
    }

    @Test
    fun `wrong or growing crops and sheltered animals are left alone`() {
        for ((id, ripe) in listOf("wheat" to true, "zucchini" to false)) {
            val engine = engine()
            plant(id, point, engine, ripe)
            assertTrue(engine.eatFavoriteCrops(now).isEmpty())
            assertNotNull(engine.state.plots[point])
        }
        val engine = engine()
        modify(engine) { it.copy(position = GridPoint(0, 0)) }
        plant("zucchini", point, engine)
        assertTrue(engine.eatFavoriteCrops(now).isEmpty())
    }

    @Test
    fun `animals that give nothing to harvest never graze`() {
        val engine = engine(LivestockSpecies.duck)
        plant("wheat", point, engine)
        assertTrue(engine.eatFavoriteCrops(now).isEmpty() && engine.state.plots[point] != null)
        assertFalse(engine.isHungry(cow, now.advancedBy(10 * AnimalMeal.hungerDelay)))
    }

    @Test
    fun `grazing seeks reachable favorites but never crosses fences or overrides shelter`() {
        val engine = engine()
        plant("zucchini", GridPoint(24, 23), engine)
        assertEquals(GridPoint(0, 1), engine.wanderDirection(cow, now, SeededRandom(7)))
        for (row in 0 until GameState.GRID_SIZE) engine.state.fences[GridPoint(row, 20)] = FenceColor.starter.id
        assertNull(engine.directionToFavoriteCrop(cow, now))
        engine.state.fences = mutableMapOf()
        engine.setTimeOfDay(23.0 * 60, now)
        val front = engine.shelterTile(cow)!!
        modify(engine) { it.copy(position = front) }
        plant("zucchini", GridPoint(front.row + 1, front.col), engine)
        assertTrue(engine.eatFavoriteCrops(now).isEmpty())
    }

    @Test
    fun `an animal gives nothing until it eats then stays fed and is not hungry again until harvested`() {
        val engine = engine()
        modify(engine) { it.copy(readyAt = now) }
        assertTrue(engine.livestock(0, home)?.readyProduce(now) == null && engine.readyProduce(point, now) == null)
        plant("zucchini", point, engine)
        assertEquals(1, engine.eatFavoriteCrops(now).size)
        assertTrue(engine.livestock(0, home)?.readyProduce(now)?.first == StorageItem.Milk && engine.hasEaten(cow))
        // However long it waits to be harvested it neither grazes nor shows hunger.
        val muchLater = now.advancedBy(10 * AnimalMeal.hungerDelay)
        plant("zucchini", point, engine)
        assertTrue(engine.eatFavoriteCrops(muchLater).isEmpty() && !engine.isHungry(cow, muchLater))
        engine.select(HarvestTool.bucket)
        val xp = LevelRules.produceXP(StorageItem.Milk, engine.state.level)
        assertEquals(listOf<GameEvent>(GameEvent.Stored(point, "milk", xp)), engine.useTool(point, muchLater))
        assertTrue(!engine.hasEaten(cow) && engine.isHungry(cow, muchLater.advancedBy(AnimalMeal.hungerDelay)))
        assertFalse(engine.isHungry(cow, muchLater.advancedBy(AnimalMeal.hungerDelay - 1)))
        modify(engine) { it.copy(readyAt = muchLater) }
        assertNull(engine.livestock(0, home)?.readyProduce(muchLater.advancedBy(1.0)))
        assertEquals(1, engine.eatFavoriteCrops(muchLater.advancedBy(1.0)).size)
        assertEquals(StorageItem.Milk, engine.livestock(0, home)?.readyProduce(muchLater.advancedBy(1.0))?.first)
    }

    @Test
    fun `a never fed animal is hungry two days after it was born`() {
        val engine = engine()
        val born = engine.livestock(0, home)!!.bornAt
        assertFalse(engine.isHungry(cow, born.advancedBy(AnimalMeal.hungerDelay - 1)))
        assertTrue(engine.isHungry(cow, born.advancedBy(AnimalMeal.hungerDelay)))
    }

    @Test
    fun `a hen lays no eggs until it has eaten`() {
        val engine = engine()
        val coop = GridPoint(5, 5)
        engine.state.putBuilding(coop, "chickenCoop")
        val hen = Chicken(0, ChickenVariant.all[0].id, "Hen", GridPoint(8, 5), hatchedAt = now.advancedBy(-10_000.0))
        engine.state.coops[coop] = Coop(chickens = listOf(hen), nextChickAt = now)
        val random = SeededRandom(2)
        repeat(500) { engine.layEggs(100.0, now, random) }
        assertTrue(engine.state.eggs.isEmpty())
        engine.state.coops[coop] =
            engine.state.coops[coop]!!.let {
                it.copy(chickens = listOf(it.chickens[0].copy(meal = AnimalMeal(now.advancedBy(-100.0)))))
            }
        repeat(500) { engine.layEggs(100.0, now, random) }
        assertTrue(engine.state.eggs.isNotEmpty())
    }

    @Test
    fun `collecting between ticks does not boost the next batch retroactively`() {
        val engine = engine()
        modify(engine) {
            it.copy(
                meal = AnimalMeal(now),
                lastWorkedAt = now.advancedBy(2.0),
                readyAt = now.advancedBy(362.0),
            )
        }
        engine.updateHerds(now.advancedBy(5.0), SeededRandom(11))
        assertEquals(now.advancedBy(359.0), engine.livestock(0, home)?.readyAt)
    }

    // Fertilizer animals

    private val tile = GridPoint(20, 20)
    private val later = Instant.ofEpochSecond(70_000_000)

    @Test
    fun `pigs rabbits and donkeys give fertilizer with the bucket and nothing else`() {
        for (species in listOf(LivestockSpecies.pig, LivestockSpecies.rabbit, LivestockSpecies.donkey)) {
            assertEquals(LivestockWork.Gives(StorageItem.Fertilizer, HarvestTool.bucket), species.work)
            assertTrue(species.workInterval > 0 && species.favoriteCrop != null)
        }
        assertNull(LivestockSpecies.duck.favoriteCrop)
    }

    @Test
    fun `fertilizer is a storage item that sells for half the price of a fertilized tile`() {
        assertEquals(StorageItem.Fertilizer, StorageItem.of("fertilizer"))
        assertTrue(StorageItem.Fertilizer in StorageItem.all)
        assertEquals(Fertilizer.price / 2, StorageItem.Fertilizer.sellValue)
        assertTrue(StorageItem.Fertilizer.isEquippable)
    }

    @Test
    fun `a fed pig gives fertilizer when it is ready and no land is fertilized by it walking over`() {
        val engine = GameEngine(stateWith(money = 0.0, xp = lateGameXP))
        val barn = GridPoint(10, 10)
        engine.state.putBuilding(barn, "pigBarn")
        val pig =
            Livestock(
                0,
                LivestockSpecies.pig.variants[0].id,
                "P",
                tile,
                bornAt = later.advancedBy(-LivestockSpecies.pig.growDuration * 2),
                readyAt = later,
            )
        engine.state.herds[barn] = Herd(listOf(pig), later.advancedBy(1_000_000.0))
        engine.state.farmland.add(tile)
        engine.select(HarvestTool.bucket)
        assertTrue(engine.useTool(tile, later).isEmpty())
        engine.state.herds[barn] = engine.state.herds[barn]!!.copy(animals = listOf(pig.copy(meal = AnimalMeal(later))))
        assertEquals(listOf<GameEvent>(GameEvent.Stored(tile, "fertilizer", 0)), engine.useTool(tile, later))
        assertTrue(engine.state.storage["fertilizer"] == 1 && engine.state.fertilized.isEmpty())
    }

    @Test
    fun `equipped fertilizer fertilizes a tile from storage and runs out`() {
        val engine = GameEngine(stateWith(money = 50.0, xp = lateGameXP))
        val second = GridPoint(20, 21)
        val third = GridPoint(20, 22)
        engine.state.farmland = mutableSetOf(tile, second, third)
        engine.state.storage["fertilizer"] = 2
        assertTrue(engine.equip(StorageItem.Fertilizer))
        assertTrue(engine.isEquipped(StorageItem.Fertilizer) && engine.isPlacingFromStorage)
        assertEquals(listOf<GameEvent>(GameEvent.Fertilized(tile, 0.0)), engine.useTool(tile, later))
        assertEquals(
            listOf<GameEvent>(GameEvent.Fertilized(second, 0.0)),
            engine.useTool(second, later.advancedBy(2.0)),
        )
        assertTrue(engine.state.storage["fertilizer"] == null && engine.state.money == 50.0)
        assertTrue(engine.useTool(third, later.advancedBy(2.1)).isEmpty())
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NoneInStorage("Fertilizer"))),
            engine.useTool(third, later.advancedBy(5.0)),
        )
        engine.selectFertilizer()
        assertFalse(engine.isPlacingFromStorage)
        engine.useTool(third, later.advancedBy(6.0))
        assertEquals(50.0 - Fertilizer.price, engine.state.money)
    }

    @Test
    fun `the fertilizer choice survives saves`() {
        val engine = GameEngine(stateWith(money = 0.0))
        engine.state.storage["fertilizer"] = 1
        engine.equip(StorageItem.Fertilizer)
        assertTrue(GameStateJson.decode(GameStateJson.encode(engine.state)).placesFertilizerFromStorage)
        assertFalse(GameStateJson.decode("{}").placesFertilizerFromStorage)
    }
}
