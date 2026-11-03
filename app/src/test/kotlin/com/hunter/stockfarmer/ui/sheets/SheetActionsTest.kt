package com.hunter.stockfarmer.ui.sheets

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.PetBreed
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.feedback.HapticCue
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRun
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.buildingPlacement
import com.hunter.stockfarmer.core.rules.storageCapacity
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.HapticRequest
import com.hunter.stockfarmer.game.SheetTarget
import com.hunter.stockfarmer.game.collectingHaptics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SheetActionsTest {
    private val now = Instant.ofEpochSecond(60_000_000)
    private val origin = GridPoint(10, 10)
    private val target = SheetTarget.Building(origin)

    private fun engine(building: String) =
        GameEngine().also {
            it.state.putBuilding(origin, building, null)
            it.state.money = 1_000_000.0
            it.state.xp = LevelRules.xpToReach(34)
            it.state.storage.clear()
        }

    private fun perform(
        engine: GameEngine,
        action: SheetAction,
        time: Instant = now,
    ) = GameSession(engine).sheetAction(target, action, time)

    @Test
    fun `successful recipe and chick choices emit light feedback and refused choices do not`() {
        val workshop = engine("grainMachine")
        val workshopSession = GameSession(workshop)
        val recipe = Recipe.named("grainMachine")!!
        val light = listOf(HapticRequest.Cue(HapticCue.Light))
        assertEquals(
            light,
            collectingHaptics(workshopSession) {
                assertNull(workshopSession.sheetAction(target, SheetAction.Recipe(recipe.id), now).refusal)
            },
        )
        workshop.state.storage[recipe.input.id] = recipe.inputCount
        workshopSession.sheetAction(target, SheetAction.StartWork, now)
        assertTrue(
            collectingHaptics(workshopSession) {
                assertEquals(
                    Rejection.WorkInProgress.message,
                    workshopSession.sheetAction(target, SheetAction.Recipe(recipe.id), now).refusal,
                )
            }.isEmpty(),
        )

        val coop = engine("chickenCoop")
        coop.state.coops[origin] = Coop(nextChickAt = now)
        val coopSession = GameSession(coop)
        val variant = ChickenVariant.roosters.first()
        assertEquals(
            light,
            collectingHaptics(coopSession) {
                assertNull(coopSession.sheetAction(target, SheetAction.NextChick(variant.id), now).refusal)
            },
        )
        assertTrue(
            collectingHaptics(coopSession) {
                assertNotNull(coopSession.sheetAction(target, SheetAction.NextChick("missing"), now).refusal)
            }.isEmpty(),
        )
    }

    @Test
    fun `workshop actions choose start and collect using the bucket rules`() {
        val engine = engine("grainMachine")
        val recipe = Recipe.named("grainMachine")!!
        assertEquals(
            Rejection.NeedIngredients(recipe.inputCount, recipe.input.name).message,
            perform(engine, SheetAction.StartWork).refusal,
        )
        engine.state.storage[recipe.input.id] = recipe.inputCount
        assertNull(perform(engine, SheetAction.Recipe(recipe.id)).refusal)
        assertEquals(listOf(GameEvent.WorkStarted(origin)), perform(engine, SheetAction.StartWork).events)
        assertEquals(Rejection.WorkInProgress.message, perform(engine, SheetAction.Recipe(recipe.id)).refusal)
        val collected = perform(engine, SheetAction.CollectGoods, now.plusSeconds(180))
        assertNull(collected.refusal)
        assertEquals(recipe.outputCount, engine.state.storage[recipe.output.id])
        assertTrue(engine.state.workshops.isEmpty())
    }

    @Test
    fun `full storage keeps finished goods and the sheet open`() {
        val engine = engine("grainMachine")
        engine.state.storage[Crop.starter.id] = 10
        perform(engine, SheetAction.StartWork)
        engine.state.storage[StorageItem.Stone.id] = engine.storageCapacity
        val result = perform(engine, SheetAction.CollectGoods, now.plusSeconds(180))
        assertEquals(Rejection.StorageFull.message, result.refusal)
        assertFalse(result.shouldClose)
        assertEquals(1, engine.state.workshops.size)
    }

    @Test
    fun `pet adoption cleans the name closes on success and occupied houses refuse`() {
        val engine = engine("petHouse")
        val action = SheetAction.MovePetIn(PetBreed.cat(Cat.starter), "  Mittens  ")
        assertTrue(perform(engine, action).shouldClose)
        assertEquals("Mittens", engine.state.pets[origin]?.name)
        val refused = perform(engine, action)
        assertEquals(Rejection.HouseOccupied.message, refused.refusal)
        assertFalse(refused.shouldClose)
        engine.state.pets[origin] =
            engine.state.pets
                .getValue(origin)
                .copy(position = GridPoint(30, 30))
        assertNull(perform(engine, SheetAction.Summon).refusal)
        assertEquals(GridPoint(11, 10), engine.state.pets[origin]?.position)
    }

    @Test
    fun `animal naming feeding and release go through core and pets cannot be released`() {
        val engine = engine("chickenCoop")
        val chicken = Chicken.hatched(3, ChickenVariant.all.first(), "Hen", GridPoint(20, 20), now.minusSeconds(1000))
        engine.state.coops[origin] = Coop(chickens = listOf(chicken), nextChickAt = now)
        val session = GameSession(engine)
        val animal = SheetTarget.Animal(AnimalRef.Chicken(origin, 3))
        assertNull(session.sheetAction(animal, SheetAction.Rename("  Henrietta  "), now).refusal)
        assertEquals(
            "Henrietta",
            engine.state.coops[origin]
                ?.chickens
                ?.single()
                ?.name,
        )
        val food = StorageItem.CropItem(Chicken.favoriteCrop)
        assertEquals(
            Rejection.NoneInStorage(food.name).message,
            session.sheetAction(animal, SheetAction.Feed(food), now).refusal,
        )
        engine.state.storage[food.id] = 2
        assertEquals(
            listOf(GameEvent.Fed(animal.animal)),
            session.sheetAction(animal, SheetAction.Feed(food), now).events,
        )
        assertEquals(1, engine.state.storage[food.id])
        assertEquals(Rejection.AlreadyFed.message, session.sheetAction(animal, SheetAction.Feed(food), now).refusal)
        assertEquals(1, engine.state.storage[food.id])
        assertTrue(session.sheetAction(animal, SheetAction.Release, now).shouldClose)
        assertTrue(
            engine.state.coops[origin]!!
                .chickens
                .isEmpty(),
        )
        engine.state.pets[origin] = Pet(breed = Cat.starter.id, name = "Cat", position = origin)
        assertFalse(
            session.sheetAction(SheetTarget.Animal(AnimalRef.Pet(origin)), SheetAction.Release, now).shouldClose,
        )
        assertNotNull(engine.state.pets[origin])
    }

    @Test
    fun `next chick choices change future births without changing existing chickens`() {
        val engine = engine("chickenCoop")
        val chicken = Chicken.hatched(3, ChickenVariant.all.first(), "Hen", GridPoint(20, 20), now)
        engine.state.coops[origin] = Coop(chickens = listOf(chicken), nextChickAt = now)
        val rooster = ChickenVariant.roosters.first()
        assertNull(perform(engine, SheetAction.NextChick(rooster.id)).refusal)
        assertEquals(rooster.id, engine.state.coops[origin]?.nextVariantID)
        assertEquals(
            chicken,
            engine.state.coops[origin]
                ?.chickens
                ?.single(),
        )
        perform(engine, SheetAction.NextChick(null))
        assertNull(engine.state.coops[origin]?.nextVariantID)
    }

    @Test
    fun `move leaves the building in place until the farm confirms placement`() {
        val engine = engine("petHouse")
        assertTrue(perform(engine, SheetAction.Move).shouldClose)
        assertEquals(origin, engine.buildingPlacement?.replacing?.origin)
        assertEquals("petHouse", engine.state.buildings[origin])
        engine.state.tractorRun = TractorRun(origin, Crop.starter.id, GridPoint(20, 20), facing = TractorFacing.right)
        assertEquals(Rejection.TractorBusy.message, perform(engine, SheetAction.Move).refusal)
    }

    @Test
    fun `storage demolition refuses until remaining capacity can hold the inventory`() {
        val engine = engine("shed")
        engine.state.storage[StorageItem.Log.id] = 120
        val refused = perform(engine, SheetAction.Demolish)
        assertEquals(Rejection.StorageInUse(20).message, refused.refusal)
        assertFalse(refused.shouldClose)
        assertNotNull(engine.state.buildings[origin])
        perform(engine, SheetAction.Stored(StorageItem.Log, 120, false))
        assertTrue(perform(engine, SheetAction.Demolish).shouldClose)
        assertNull(engine.state.buildings[origin])
    }

    @Test
    fun `house and animal home upgrade actions use their respective core rules`() {
        val engine = engine("house1")
        val before = engine.state.money
        assertNull(perform(engine, SheetAction.Upgrade).refusal)
        assertEquals("house2", engine.state.buildings[origin])
        assertTrue(engine.state.money < before)
        assertEquals(Rejection.AlreadyUpgraded.message, perform(engine, SheetAction.Upgrade).refusal)
        engine.state.putBuilding(origin, "chickenCoop", null)
        assertNull(perform(engine, SheetAction.Upgrade).refusal)
        assertEquals(2, engine.state.homeLevels[origin])
    }

    @Test
    fun `tractor cargo can be stored partly then sold and busy runs cannot move or demolish`() {
        val engine = engine("tractor1")
        val crop = Crop.starter
        engine.state.tractorCargo[origin] = mapOf(crop.id to 3)
        engine.state.storage[StorageItem.Stone.id] = engine.storageCapacity - 1
        assertNull(perform(engine, SheetAction.Cargo(crop.id, false)).refusal)
        assertEquals(1, engine.state.storage[crop.id])
        assertEquals(2, engine.state.tractorCargo[origin]?.get(crop.id))
        assertEquals(Rejection.StorageFull.message, perform(engine, SheetAction.Cargo(crop.id, false)).refusal)
        assertNull(perform(engine, SheetAction.Cargo(crop.id, true)).refusal)
        assertNull(engine.state.tractorCargo[origin])
        engine.state.tractorRun = TractorRun(origin, crop.id, GridPoint(20, 20), facing = TractorFacing.right)
        assertEquals(Rejection.TractorBusy.message, perform(engine, SheetAction.Demolish).refusal)
        assertTrue(perform(engine, SheetAction.CancelHarvest).shouldClose)
        assertTrue(engine.state.tractorRun!!.returning)
    }

    @Test
    fun `tractor starts only for a reachable ready field and closes on success`() {
        val engine = engine("tractor1")
        val crop = Crop.starter
        assertFalse(perform(engine, SheetAction.Harvest(crop.id)).shouldClose)
        engine.state.plots[GridPoint(10, 13)] = Plot(crop.id, now.minusSeconds(100_000))
        val result = perform(engine, SheetAction.Harvest(crop.id))
        assertNull(result.refusal)
        assertTrue(result.shouldClose)
        assertEquals(crop.id, engine.state.tractorRun?.cropID)
    }

    @Test
    fun `stale targets refuse instead of mutating another object`() {
        val result = perform(GameEngine(), SheetAction.Demolish)
        assertEquals(Rejection.NothingHere.message, result.refusal)
        assertFalse(result.shouldClose)
    }
}
