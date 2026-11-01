package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class StorageSourceTest {
    private val now = Instant.ofEpochSecond(40_000_000)
    private val origin = GridPoint(15, 15)

    @Test
    fun `only sheds and silos add storage and barn limits stay the same`() {
        assertEquals(
            setOf("shed", "silo"),
            Building.all
                .filter { it.isStorage }
                .map { it.id }
                .toSet(),
        )
        assertEquals(1, Building.named("barn1")?.placementLimit)
        assertEquals(1, Building.named("barn2")?.placementLimit)
        val engine = GameEngine(stateWith(money = 10_000.0, xp = lateGameXP, logs = 0))
        engine.state.putBuilding(origin, "barn1")
        engine.state.putBuilding(GridPoint(30, 20), "barn2")
        engine.state.putDecoration(GridPoint(5, 5), PlacedDecoration("trunk"))
        val donkey =
            Livestock(0, LivestockSpecies.donkey.variants[0].id, "Donkey", origin, bornAt = now.advancedBy(-10_000.0))
        engine.state.herds[GridPoint(40, 20)] = Herd(listOf(donkey), now.advancedBy(1_000.0))
        assertEquals(100, engine.storageCapacity)
        engine.state.putBuilding(GridPoint(1, 1), "shed")
        engine.state.putBuilding(GridPoint(1, 10), "silo")
        assertEquals(300, engine.storageCapacity)
    }

    @Test
    fun `saved overflow is kept and can be sold until there is room again`() {
        val state = stateWith(money = 100.0, xp = lateGameXP, logs = 0)
        state.putBuilding(origin, "barn1")
        state.storage = mutableMapOf("wheat" to 500)
        val tile = GridPoint(24, 24)
        state.plots[tile] = Plot("wheat", now.advancedBy(-100.0))
        val engine = GameEngine(GameStateJson.decode(GameStateJson.encode(state)))
        engine.select(HarvestTool.scythe)
        assertTrue(engine.storageCapacity == 100 && engine.storedCount == 500)
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.StorageFull)), engine.useTool(tile, now))
        assertEquals(mapOf("wheat" to 500), engine.state.storage)
        engine.sellStored(StorageItem.CropItem(Crop.starter), 401)
        assertEquals(99, engine.storedCount)
        assertEquals(listOf<GameEvent>(GameEvent.Stored(tile, "wheat", 0)), engine.useTool(tile, now))
        assertEquals(100, engine.storedCount)
    }

    @Test
    fun `sprinklers spray on the shared schedule until told to run constantly`() {
        assertFalse(GameState().sprinklersRunConstantly)
    }

    @Test
    fun `the sprinkler choice survives saves and older saves without it load`() {
        val state = GameState()
        state.sprinklersRunConstantly = true
        assertTrue(GameStateJson.decode(GameStateJson.encode(state)).sprinklersRunConstantly)
        assertFalse(GameStateJson.decode("{}").sprinklersRunConstantly)
    }

    @Test
    fun `running constantly does not change where water reaches`() {
        val state = GameState()
        state.putDecoration(GridPoint(20, 20), PlacedDecoration(Decoration.SPRINKLER_ID))
        state.farmland.add(GridPoint(21, 21))
        val before = state.saturatedFarmland
        state.sprinklersRunConstantly = true
        assertEquals(before, state.saturatedFarmland)
    }
}
