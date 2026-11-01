package com.hunter.stockfarmer.ui.sheets

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.DogBreed
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.PetBreed
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRun
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.WorkshopStatus
import com.hunter.stockfarmer.core.rules.buildingPlacement
import com.hunter.stockfarmer.core.rules.startWork
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SheetStateTest {
    private val now = Instant.ofEpochSecond(60_000_000)
    private val origin = GridPoint(10, 10)

    private fun building(
        engine: GameEngine,
        time: Instant = now,
    ) = requireNotNull(SheetState.from(engine, SheetTarget.Building(origin), time).building)

    @Test
    fun `workshop snapshots count ingredients and update time without changing the engine`() {
        val engine = GameEngine()
        engine.state.putBuilding(origin, "grainMachine", null)
        val recipe = requireNotNull(Recipe.named("grainMachine"))
        engine.state.storage[recipe.input.id] = recipe.inputCount
        val idle = requireNotNull(building(engine).workshop)
        assertEquals(WorkshopStatus.Idle, idle.status)
        assertEquals(recipe.inputCount, idle.inputStored)
        assertNull(idle.startRefusal)
        engine.startWork(origin, now)
        val working = requireNotNull(building(engine, now.plusSeconds(60)).workshop)
        assertEquals(WorkshopStatus.Working(120.0), working.status)
        assertTrue(working.statusText.contains("2 min"))
        val ready = requireNotNull(building(engine, now.plusSeconds(180)).workshop)
        assertEquals(WorkshopStatus.Ready, ready.status)
        assertEquals(Rejection.GoodsWaiting.message, ready.startRefusal)
        assertEquals(WorkshopStatus.Idle, idle.status)
        assertEquals(1, engine.state.workshops.size)
    }

    @Test
    fun `recipe locks and animal home capacities use core queries`() {
        val engine = GameEngine()
        engine.state.putBuilding(origin, "tailorTable", null)
        val choices = requireNotNull(building(engine).workshop).choices
        assertTrue(choices.first { it.recipe.id == "tailorTable" }.selected)
        assertNull(choices.first { it.recipe.id == "tailorTable" }.requiredLevel)
        assertNotNull(choices.first { it.recipe.id == "cottonCloth" }.requiredLevel)
        engine.state.putBuilding(origin, "chickenCoop", null)
        engine.state.coops[origin] = Coop(nextChickAt = now.plusSeconds(60))
        val home = building(engine)
        assertEquals("Chickens: 0/2\nNext chick in 1 min", home.homeMessage)
        assertTrue(home.homeLevel!!.contains("2 animals"))
        assertNotNull(home.upgrade?.refusal)
        engine.state.homeLevels[origin] = 3
        assertNull(building(engine).upgrade)
        assertTrue(building(engine).homeLevel!!.contains("6 animals"))
    }

    @Test
    fun `animals describe age food meals and release availability`() {
        val engine = GameEngine()
        val ref = AnimalRef.Chicken(origin, 0)
        val hen = Chicken.hatched(0, ChickenVariant.all.first(), "Hen", GridPoint(20, 20), now)
        engine.state.coops[origin] = Coop(chickens = listOf(hen), nextChickAt = now)
        engine.state.storage[Chicken.favoriteCrop.id] = 2
        val young = requireNotNull(SheetState.animal(engine, ref, now))
        assertTrue(young.description.any { it.startsWith("Grows up in") })
        assertEquals(2, young.foods.single().count)
        assertTrue(young.releasable)
        engine.state.coops[origin] =
            engine.state.coops.getValue(origin).copy(
                chickens =
                    listOf(hen.copy(meal = AnimalMeal(now))),
            )
        assertTrue(SheetState.animal(engine, ref, now)!!.description.any { it.startsWith("Well fed:") })
        engine.state.pets[origin] = Pet(breed = Cat.starter.id, name = "Cat", position = origin)
        val pet = requireNotNull(SheetState.animal(engine, AnimalRef.Pet(origin), now))
        assertFalse(pet.releasable)
        assertTrue(pet.foods.isEmpty())
        assertNull(SheetState.animal(engine, AnimalRef.Chicken(origin, 99), now))
    }

    @Test
    fun `ducks are sold instead of released and show no produce`() {
        val engine = GameEngine()
        val duck = Livestock.born(0, LivestockSpecies.duck.variants.first(), "Duck", origin, now.minusSeconds(100_000))
        engine.state.herds[origin] = Herd(listOf(duck), now)
        val ref = AnimalRef.Livestock(origin, 0)
        val state = requireNotNull(SheetState.animal(engine, ref, now))
        assertTrue(state.sellable)
        assertFalse(state.releasable)
        assertEquals(listOf("Sells for $25", "(Fully grown)"), state.description)
        val result = SheetActions.perform(engine, SheetTarget.Animal(ref), SheetAction.Sell, now)
        assertEquals(listOf<GameEvent>(GameEvent.AnimalSold(origin, 25.0)), result.events)
        assertTrue(result.shouldClose)
        assertNull(SheetState.animal(engine, ref, now))
    }

    @Test
    fun `grown livestock show their produce and its harvest tool`() {
        val engine = GameEngine()
        val sheep =
            Livestock
                .born(0, LivestockSpecies.sheep.variants.first(), "Sheep", origin, now.minusSeconds(100_000))
                .copy(meal = AnimalMeal(now), readyAt = now)
        engine.state.herds[origin] = Herd(listOf(sheep), now)
        val animal = requireNotNull(SheetState.animal(engine, AnimalRef.Livestock(origin, 0), now))
        assertTrue(animal.description.any { it.contains("Wool is ready") && it.contains("Scythe") })
    }

    @Test
    fun `tractor availability includes cargo planted crops and a returning run`() {
        val engine = GameEngine()
        engine.state.putBuilding(origin, "tractor1", null)
        val wheat = Crop.starter
        engine.state.plots[GridPoint(30, 30)] = Plot(wheat.id, now.minusSeconds(100_000))
        assertTrue(
            building(engine)
                .tractor!!
                .crops
                .single()
                .canHarvest,
        )
        engine.state.tractorCargo[origin] = mapOf(wheat.id to 3)
        val loaded = building(engine).tractor!!
        assertFalse(loaded.crops.single().canHarvest)
        assertEquals(
            3,
            loaded.crops
                .single()
                .summary.gathered,
        )
        engine.state.tractorRun =
            TractorRun(
                origin,
                wheat.id,
                GridPoint(20, 20),
                facing = TractorFacing.right,
                returning = true,
            )
        val returning = building(engine).tractor!!
        assertEquals("Tractor returning home", returning.run)
        assertFalse(returning.canCancel)
        assertFalse(returning.crops.single().canHarvest)
    }

    @Test
    fun `storage and house panels expose core capacity and upgrades`() {
        val engine = GameEngine()
        engine.state.putBuilding(origin, "shed", null)
        engine.state.storage[StorageItem.Log.id] = 120
        val storage = building(engine)
        assertTrue(storage.storage!!.contains("120 / 150"))
        assertNotNull(storage.demolishRefusal)
        engine.state.putBuilding(origin, "house1", null)
        val house = building(engine)
        assertEquals("Stock slots: 0 / 5", house.stocks)
        assertNotNull(house.upgrade?.refusal)
        engine.state.putBuilding(origin, "house2", null)
        assertEquals("Stock slots: 0 / 10", building(engine).stocks)
        assertNull(building(engine).upgrade)
    }

    @Test
    fun `pet selection resets dog color to a valid color of the chosen breed and limits names`() {
        val choice = PetChoiceState().copy(species = PetSpecies.dog).chooseBreed(DogBreed.basenji).named("a".repeat(50))
        assertEquals(DogBreed.basenji.dogs.first(), choice.dog)
        assertEquals(PetBreed.dog(choice.dog), choice.breed)
        assertEquals(Pet.MAX_NAME_LENGTH, choice.name.length)
        assertEquals(PetBreed.cat(Cat.starter), choice.copy(species = PetSpecies.cat).breed)
    }

    @Test
    fun `a disappeared building produces an empty snapshot`() {
        assertNull(SheetState.from(GameEngine(), SheetTarget.Building(origin), now).building)
    }
}
