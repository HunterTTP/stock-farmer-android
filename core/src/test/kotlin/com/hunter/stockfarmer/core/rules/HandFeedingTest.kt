package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Dog
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class HandFeedingTest {
    private val now = Instant.ofEpochSecond(60_000_000)
    private val house = GridPoint(10, 10)
    private val barn = GridPoint(30, 10)
    private val petSpot = GridPoint(12, 12)
    private val sheepSpot = GridPoint(32, 12)
    private val lettuce: StorageItem = StorageItem.CropItem(Crop.named("lettuce")!!)
    private val broccoli: StorageItem = StorageItem.CropItem(Crop.named("broccoli")!!)
    private val carp: StorageItem = StorageItem.Fish(FishKind.named("carp")!!)
    private val sheep = AnimalRef.Livestock(barn, 0)
    private val fedSheep = listOf<GameEvent>(GameEvent.Fed(sheep))

    private fun reject(rejection: Rejection) = listOf<GameEvent>(GameEvent.Rejected(rejection))

    /** A cat living in a pet house and a sheep in a barn, each standing on a known tile, with some of everything in storage. */
    private fun engine(species: PetSpecies = PetSpecies.cat): GameEngine {
        val engine = GameEngine(stateWith(money = 100.0, xp = lateGameXP))
        engine.state.putBuilding(house, "petHouse")
        val breed = if (species == PetSpecies.cat) Cat.starter.id else Dog.starter.id
        engine.state.pets[house] = Pet(species, breed, "P", petSpot)
        engine.state.putBuilding(barn, "barn1")
        val animal =
            Livestock(0, LivestockSpecies.sheep.variants[0].id, "S", sheepSpot, bornAt = now.advancedBy(-1_000_000.0))
        engine.state.herds[barn] = Herd(listOf(animal), now.advancedBy(1_000_000.0))
        for (item in listOf(lettuce, broccoli, carp)) engine.state.storage[item.id] = 2
        return engine
    }

    @Test
    fun `every crop can be equipped as produce without touching the seed selection`() {
        val engine = engine()
        val seed = engine.state.selectedCropID
        val crops = StorageItem.all.filter { it is StorageItem.CropItem || it is StorageItem.RareCrop }
        assertTrue(crops.isNotEmpty() && crops.all { it.isEquippable })
        engine.state.storage[lettuce.id] = 1
        assertTrue(engine.equip(lettuce))
        assertTrue(engine.isEquipped(lettuce) && engine.state.mode == PlayMode.decorate)
        assertEquals(seed, engine.state.selectedCropID)
    }

    @Test
    fun `a favorite crop dragged onto an animal feeds it like eating it from the ground`() {
        val engine = engine()
        engine.equip(lettuce)
        assertEquals(fedSheep, engine.useTool(sheepSpot, now))
        assertEquals(1, engine.state.storage[lettuce.id])
        assertEquals(AnimalMeal(now), engine.meal(sheep))
    }

    @Test
    fun `an animal takes only its favorite and only when it is not already fed`() {
        val engine = engine()
        engine.equip(broccoli)
        assertEquals(reject(Rejection.WontEat), engine.useTool(sheepSpot, now))
        assertEquals(2, engine.state.storage[broccoli.id])
        engine.equip(lettuce)
        engine.useTool(sheepSpot, now)
        assertEquals(
            reject(Rejection.AlreadyFed),
            engine.useTool(sheepSpot, now.advancedBy(AnimalMeal.DURATION - 1)),
        )
        assertEquals(1, engine.state.storage[lettuce.id])
        // Fed until it has been harvested, however long that takes.
        assertEquals(
            reject(Rejection.AlreadyFed),
            engine.useTool(sheepSpot, now.advancedBy(AnimalMeal.DURATION + 1)),
        )
        val herd = engine.state.herds[barn]!!
        engine.state.herds[barn] =
            herd.copy(animals = listOf(herd.animals[0].copy(lastWorkedAt = now.advancedBy(AnimalMeal.DURATION + 2))))
        assertEquals(fedSheep, engine.useTool(sheepSpot, now.advancedBy(AnimalMeal.DURATION + 3)))
    }

    @Test
    fun `a sheep is hungry until fed and not again until harvested and pets never are`() {
        val engine = engine()
        assertTrue(engine.isHungry(sheep, now) && !engine.isHungry(AnimalRef.Pet(house), now))
        assertTrue(engine.favoriteCrop(AnimalRef.Pet(house)) == null && engine.favoriteCrop(sheep)?.id == "lettuce")
        engine.equip(lettuce)
        engine.useTool(sheepSpot, now)
        assertFalse(engine.isHungry(sheep, now.advancedBy(60.0)))
        assertFalse(engine.isHungry(sheep, now.advancedBy(10 * AnimalMeal.hungerDelay)))
    }

    @Test
    fun `cats and dogs eat any fish but no crop`() {
        for (species in PetSpecies.entries) {
            val engine = engine(species)
            engine.equip(carp)
            assertEquals(listOf<GameEvent>(GameEvent.Fed(AnimalRef.Pet(house))), engine.useTool(petSpot, now))
            assertEquals(AnimalMeal(now), engine.state.pets[house]?.meal)
            assertEquals(1, engine.state.storage[carp.id])
            engine.equip(if (species == PetSpecies.cat) broccoli else lettuce)
            assertEquals(reject(Rejection.WontEat), engine.useTool(petSpot, now.advancedBy(AnimalMeal.DURATION + 1)))
        }
    }

    @Test
    fun `fish are not for livestock and crops need an animal to eat them`() {
        val engine = engine()
        engine.equip(carp)
        assertEquals(reject(Rejection.WontEat), engine.useTool(sheepSpot, now))
        engine.equip(lettuce)
        assertEquals(reject(Rejection.NeedAnimalToFeed), engine.useTool(GridPoint(40, 40), now))
    }

    @Test
    fun `the last one fed is followed by a short quiet not an instant complaint`() {
        val engine = engine()
        engine.state.storage[lettuce.id] = 1
        engine.equip(lettuce)
        engine.useTool(sheepSpot, now)
        assertTrue(engine.useTool(sheepSpot, now.advancedBy(0.2)).isEmpty())
        assertEquals(reject(Rejection.NoneInStorage("Lettuce")), engine.useTool(sheepSpot, now.advancedBy(2.0)))
        assertNull(engine.state.storage[lettuce.id])
    }
}
