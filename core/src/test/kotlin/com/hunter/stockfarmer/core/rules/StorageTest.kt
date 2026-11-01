package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.lyingEgg
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.place
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

class StorageTest {
    private val planted = Instant.ofEpochSecond(12_000_000)
    private val ready = planted.advancedBy(30.0)
    private val tile = GridPoint(24, 24)

    /** An engine with the given storage buildings placed, and wheat ready on [tile]. */
    private fun engine(vararg storage: String): GameEngine {
        val engine = GameEngine(stateWith(mode = PlayMode.harvest, logs = 0))
        for ((index, id) in storage.withIndex()) engine.place(GridPoint(3, 6 * index), id)
        engine.state.plots[tile] = Plot(Crop.starter.id, planted)
        return engine
    }

    @Test
    fun `barns keep their placement limits but only the shed adds storage`() {
        val buildings = listOf("shed", "barn1", "barn2").map { Building.named(it) }
        assertEquals(listOf("Shed", "Sheep Barn", "Luxury Barn"), buildings.map { it?.name })
        assertEquals(listOf(50, null, null), buildings.map { it?.storageCapacity })
        assertEquals(listOf(4, 1, 1), buildings.map { it?.placementLimit })
        assertEquals(listOf(true, false, false), buildings.map { it?.isStorage })
        assertEquals(false, Building.named("house1")?.isStorage)
    }

    @Test
    fun `each storage building adds to the total capacity`() {
        assertEquals(100, engine().storageCapacity)
        assertEquals(150, engine("shed", "barn1").storageCapacity)
        assertEquals(150, engine("shed", "barn1", "barn2").storageCapacity)
    }

    @Test
    fun `up to four sheds can be placed and each adds room`() {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build))
        engine.state.selectedBuildingID = "shed"
        engine.state.storage = mutableMapOf(StorageItem.Log.id to 400, StorageItem.Stone.id to 40)
        for (index in 0 until 4) assertEquals(1, engine.build(GridPoint(10, 6 * index + 5), planted).size)
        assertEquals(4, engine.state.buildings.size)
        assertEquals(300, engine.storageCapacity)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.BuildingLimit("Shed", 4))),
            engine.build(GridPoint(30, 30), planted),
        )
    }

    @Test
    fun `up to four silos can be placed and each adds room whatever its look`() {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build))
        engine.state.selectedBuildingID = "silo"
        engine.state.storage = mutableMapOf(StorageItem.Log.id to 400, StorageItem.Stone.id to 40)
        val silo = Building.named("silo")!!
        assertTrue(silo.storageCapacity == 150 && silo.placementLimit == 4 && silo.isStorage)
        engine.select(silo.styles.last(), silo)
        for (index in 0 until 4) assertEquals(1, engine.build(GridPoint(10, 6 * index + 5), planted).size)
        assertEquals(BACKPACK_CAPACITY + 600, engine.storageCapacity)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.BuildingLimit("Silo", 4))),
            engine.build(GridPoint(30, 30), planted),
        )
    }

    @Test
    fun `only one luxury barn can be placed`() {
        val engine = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build))
        engine.state.selectedBuildingID = "barn2"
        engine.state.storage = mutableMapOf(StorageItem.Log.id to 200, StorageItem.Stone.id to 40)
        assertEquals(GameEvent.Built(GridPoint(8, 6), 24000.0), engine.build(GridPoint(10, 10), planted).first())
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.BuildingLimit("Luxury Barn", 1))),
            engine.build(GridPoint(30, 30), planted),
        )
        assertEquals("You can only place one Luxury Barn", Rejection.BuildingLimit("Luxury Barn", 1).message)
    }

    @Test
    fun `everyone starts with a backpack`() {
        assertEquals(BACKPACK_CAPACITY, GameEngine().storageCapacity)
        assertEquals(100, BACKPACK_CAPACITY)
    }

    @Test
    fun `picking a harvest implement switches to the harvest tool`() {
        val engine = engine()
        engine.setMode(PlayMode.plant)
        engine.select(HarvestTool.axe)
        assertEquals(PlayMode.harvest, engine.state.mode)
        assertEquals(HarvestTool.axe, engine.state.harvestTool)
        assertEquals(HarvestTool.scythe, GameState().harvestTool)
    }

    @Test
    fun `harvesting puts the crop in storage and still pays XP`() {
        val engine = engine()
        val xp = engine.state.xp
        val events = engine.useTool(tile, ready)
        assertEquals(listOf<GameEvent>(GameEvent.Stored(tile, Crop.starter.id, 2)), events)
        assertEquals(mapOf(Crop.starter.id to 1), engine.state.storage)
        assertEquals(0.0, engine.state.money)
        assertEquals(xp + 2, engine.state.xp)
        assertNull(engine.state.plots[tile])
    }

    @Test
    fun `a full storage leaves the crop growing`() {
        val engine = engine()
        engine.state.storage = mutableMapOf("carrot" to 50, "wheat" to 50)
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.StorageFull)), engine.useTool(tile, ready))
        assertNotNull(engine.state.plots[tile])
        assertEquals(100, engine.storedCount)
    }

    @Test
    fun `storage holds many kinds up to the total`() {
        val engine = engine("silo", "silo")
        engine.state.storage = mutableMapOf("wheat" to 250, "carrot" to 149)
        assertEquals(1, engine.useTool(tile, ready).size)
        assertEquals(400, engine.storedCount)
        assertEquals(listOf(251, 149), engine.storedItems.map { it.second })
    }

    @Test
    fun `the bucket moves eggs into storage`() {
        val engine = engine()
        engine.state.harvestTool = HarvestTool.bucket
        val spot = GridPoint(22, 20)
        engine.state.eggs = listOf(lyingEgg(spot, 0.1, -0.1), lyingEgg(spot, -0.2, 0.2))
        val xp = LevelRules.produceXP(StorageItem.Egg, engine.state.level)
        assertEquals(
            listOf<GameEvent>(
                GameEvent.Stored(spot, StorageItem.Egg.id, xp),
                GameEvent.Stored(spot, StorageItem.Egg.id, xp),
            ),
            engine.useTool(spot, ready),
        )
        assertTrue(engine.state.eggs.isEmpty())
        assertEquals(mapOf(StorageItem.Egg.id to 2), engine.state.storage)
        assertEquals(0.0, engine.state.money)
    }

    @Test
    fun `a full storage leaves the eggs where they are`() {
        val engine = engine()
        engine.state.harvestTool = HarvestTool.bucket
        engine.state.storage = mutableMapOf("wheat" to 100)
        val spot = GridPoint(22, 20)
        engine.state.eggs = listOf(lyingEgg(spot, 0.0, 0.0))
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.StorageFull)), engine.useTool(spot, ready))
        assertEquals(1, engine.state.eggs.size)
    }

    @Test
    fun `stored eggs sell for the egg price`() {
        val engine = engine()
        engine.state.storage = mutableMapOf(StorageItem.Egg.id to 3)
        assertEquals(listOf<StorageItem>(StorageItem.Egg), engine.storedItems.map { it.first })
        assertEquals(
            listOf<GameEvent>(GameEvent.StorageSold(StorageItem.Egg.id, 2, 2 * Egg.SALE_PRICE)),
            engine.sellStored(StorageItem.Egg, 2),
        )
    }

    @Test
    fun `stored chickens sell for the grown price`() {
        val engine = engine()
        val chicken = StorageItem.ChickenItem(ChickenVariant.all[0])
        engine.state.storage = mutableMapOf(ChickenVariant.all[0].id to 3)
        assertEquals(listOf<StorageItem>(chicken), engine.storedItems.map { it.first })
        assertEquals(100.0, engine.saleValue(chicken, 2))
        assertEquals(
            listOf<GameEvent>(GameEvent.StorageSold(ChickenVariant.all[0].id, 2, 100.0)),
            engine.sellStored(chicken, 2),
        )
        assertEquals(mapOf(ChickenVariant.all[0].id to 1), engine.state.storage)
    }

    @Test
    fun `a storage building cannot come down while its space is in use`() {
        val engine = engine("shed", "silo")
        engine.state.storage = mutableMapOf("wheat" to 160)
        val shed = engine.building(GridPoint(3, 0))!!
        val barn = engine.building(GridPoint(3, 6))!!
        assertNull(engine.demolishRejection(shed))
        assertEquals(Rejection.StorageInUse(10), engine.demolishRejection(barn))
        engine.state.storage = mutableMapOf("wheat" to 290)
        assertEquals(Rejection.StorageInUse(40), engine.demolishRejection(shed))
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.StorageInUse(40))), engine.demolish(shed))
        assertNotNull(engine.building(GridPoint(3, 0)))
        engine.state.storage = mutableMapOf("wheat" to 250)
        assertEquals(1, engine.demolish(shed).size)
    }

    @Test
    fun `selling stored items pays the usual price for the quantity`() {
        val engine = engine()
        engine.state.storage = mutableMapOf("wheat" to 10, "carrot" to 5)
        val wheat = StorageItem.CropItem(Crop.named("wheat")!!)
        assertEquals(1.2, engine.saleValue(wheat, 4))
        assertEquals(listOf<GameEvent>(GameEvent.StorageSold("wheat", 4, 1.2)), engine.sellStored(wheat, 4))
        assertEquals(6, engine.state.storage["wheat"])
        assertTrue(abs(engine.state.money - 1.2) < 1e-9)
        assertEquals(1, engine.sellStored(wheat, 6).size)
        assertNull(engine.state.storage["wheat"])
        assertEquals(mapOf("carrot" to 5), engine.state.storage)
    }

    @Test
    fun `selling more than is stored or nothing does nothing`() {
        val engine = engine()
        engine.state.storage = mutableMapOf("wheat" to 3)
        val wheat = StorageItem.CropItem(Crop.named("wheat")!!)
        assertTrue(engine.sellStored(wheat, 4).isEmpty())
        assertTrue(engine.sellStored(wheat, 0).isEmpty())
        assertEquals(3, engine.state.storage["wheat"])
        assertFalse(engine.sellStored(wheat, 3).isEmpty())
    }
}

class TrashTest {
    private fun engine(): GameEngine {
        val engine = GameEngine(stateWith(logs = 0))
        engine.state.storage = mutableMapOf("wheat" to 5, StorageItem.Log.id to 3)
        return engine
    }

    @Test
    fun `trashing throws items away for nothing`() {
        val engine = engine()
        val wheat = StorageItem.CropItem(Crop.starter)
        assertEquals(listOf<GameEvent>(GameEvent.StorageTrashed("wheat", 2)), engine.trashStored(wheat, 2))
        assertEquals(mapOf("wheat" to 3, StorageItem.Log.id to 3), engine.state.storage)
        assertEquals(0.0, engine.state.money)
    }

    @Test
    fun `trashing everything removes the item`() {
        val engine = engine()
        engine.trashStored(StorageItem.Log, 3)
        assertEquals(mapOf("wheat" to 5), engine.state.storage)
    }

    @Test
    fun `trashing more than is stored or nothing does nothing`() {
        val engine = engine()
        assertTrue(engine.trashStored(StorageItem.Log, 4).isEmpty())
        assertTrue(engine.trashStored(StorageItem.Log, 0).isEmpty())
        assertTrue(engine.trashStored(StorageItem.Egg, 1).isEmpty())
        assertEquals(mapOf("wheat" to 5, StorageItem.Log.id to 3), engine.state.storage)
    }
}
