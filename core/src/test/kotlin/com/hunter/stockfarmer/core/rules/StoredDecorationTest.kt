package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.Rarity
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.roundedToCents
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class StoredDecorationTest {
    private val now = Instant.ofEpochSecond(50_000_000)
    private val spot = GridPoint(20, 20)
    private val hayBale = Decoration.named("hayBale")!!
    private val stored: StorageItem = StorageItem.DecorationItem(hayBale, 1)
    private val carp: StorageItem = StorageItem.Fish(FishKind.named("carp")!!)

    private fun engine(holding: HarvestTool? = null): GameEngine {
        val engine = GameEngine(stateWith(money = 1_000.0, xp = lateGameXP))
        if (holding != null) engine.select(holding)
        return engine
    }

    @Test
    fun `a decoration is a storage item per size with a refund sized price`() {
        assertEquals(stored, StorageItem.of(stored.id))
        assertTrue(stored in StorageItem.all)
        assertEquals("Hay Bale: Raw Bale Big", stored.name)
        assertEquals((hayBale.cost * GameEngine.DESTROY_REFUND_SHARE).roundedToCents, stored.sellValue)
        assertTrue(StorageItem.all.none { it is StorageItem.DecorationItem && it.decoration.isRock })
    }

    @Test
    fun `the axe fells a decoration into storage in one pass`() {
        val engine = engine(HarvestTool.axe)
        engine.state.putDecoration(spot, PlacedDecoration(hayBale.id, 1, placedAt = now))
        val beside = GridPoint(spot.row, spot.col + 1)
        val events = engine.useTool(beside, now)
        assertTrue(GameEvent.Stored(beside, stored.id, 0) in events)
        assertTrue(engine.state.decorations.isEmpty())
        assertEquals(1, engine.state.storage[stored.id])
        assertEquals(1_000.0, engine.state.money)
    }

    @Test
    fun `a full backpack leaves the decoration standing`() {
        val engine = engine(HarvestTool.axe)
        engine.state.putDecoration(spot, PlacedDecoration(hayBale.id, 1, placedAt = now))
        engine.state.storage["wheat"] = BACKPACK_CAPACITY
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.StorageFull)), engine.useTool(spot, now))
        assertNotNull(engine.state.decorations[spot])
    }

    @Test
    fun `equipping a stored decoration places it without paying and uses it up`() {
        val engine = engine()
        engine.state.storage[stored.id] = 1
        assertTrue(engine.equip(stored))
        assertTrue(
            engine.state.mode == PlayMode.decorate &&
                engine.state.selectedDecorationID == hayBale.id &&
                engine.selectedDecorationSize == 1,
        )
        val origin = engine.selectedDecoration!!.originSettingBottomLeftOn(spot)
        assertEquals(listOf<GameEvent>(GameEvent.Decorated(origin, 0.0)), engine.tap(spot, now))
        assertTrue(engine.state.money == 1_000.0 && engine.state.storage[stored.id] == null)
        assertEquals(
            listOf(1),
            engine.state.decorations.values
                .map { it.size },
        )
        assertTrue(engine.tap(GridPoint(30, 30), now.advancedBy(0.1)).isEmpty())
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NoneInStorage(stored.name))),
            engine.tap(GridPoint(30, 30), now.advancedBy(2.0)),
        )
    }

    @Test
    fun `picking the decoration from the shop buys it again`() {
        val engine = engine()
        engine.state.storage[stored.id] = 1
        engine.equip(stored)
        engine.select(1, hayBale)
        engine.tap(spot, now)
        assertTrue(engine.state.money == 1_000.0 - hayBale.cost && engine.state.storage[stored.id] == 1)
    }

    @Test
    fun `a stored sprinkler is placed from storage too`() {
        val engine = engine()
        val sprinkler = Decoration.named(Decoration.SPRINKLER_ID)!!
        val item = StorageItem.DecorationItem(sprinkler, 0)
        engine.state.storage[item.id] = 1
        assertTrue(engine.equip(item))
        engine.beginPlacingSelectedDecoration(spot)
        assertNull(engine.cannotStartPlacingSelectedDecoration)
        val events = engine.confirmPlacement(now)
        assertEquals(
            listOf<GameEvent>(
                GameEvent.Decorated(
                    engine.state.decorations.keys
                        .first(),
                    0.0,
                ),
            ),
            events,
        )
        assertTrue(engine.state.money == 1_000.0 && engine.state.storage[item.id] == null)
    }

    @Test
    fun `the tool knows how many of what it has equipped are left down to none`() {
        val engine = GameEngine(stateWith(money = 100.0, xp = lateGameXP))
        assertTrue(engine.equippedItem == null && engine.equippedStock == null)
        engine.state.storage[carp.id] = 2
        assertTrue(engine.equip(carp) && engine.equippedItem == carp && engine.equippedStock == 2)
        engine.state.storage.remove(carp.id)
        assertTrue(engine.equippedItem == carp && engine.equippedStock == 0)
        engine.state.storage["fertilizer"] = 3
        assertTrue(
            engine.equip(StorageItem.Fertilizer) && engine.equippedItem == StorageItem.Fertilizer &&
                engine.equippedStock == 3,
        )
        val rare = StorageItem.Butterfly(Butterflies.Wings.Color(Rarity.rare))
        engine.state.storage[rare.id] = 1
        assertTrue(engine.equip(rare) && engine.isEquipped(rare) && engine.equippedStock == 1)
    }

    @Test
    fun `only items with a use can be equipped`() {
        assertTrue(
            StorageItem.all.filter { it.isEquippable }.all {
                it is StorageItem.DecorationItem || it is StorageItem.Fence || it is StorageItem.Fish ||
                    it is StorageItem.CropItem || it is StorageItem.RareCrop || it is StorageItem.Fertilizer ||
                    it is StorageItem.Butterfly
            },
        )
        assertTrue(StorageItem.Log in StorageItem.all.filter { !it.isEquippable })
        assertTrue(stored.isEquippable && carp.isEquippable)
    }

    @Test
    fun `equipping anything else does nothing`() {
        val engine = engine()
        val before = engine.state.copy()
        for (item in listOf(StorageItem.Log, StorageItem.Egg, StorageItem.Milk, StorageItem.Bread, StorageItem.Stone)) {
            engine.state.storage[item.id] = 1
            assertFalse(engine.equip(item))
        }
        engine.state.storage = before.storage.toMutableMap()
        assertTrue(sameState(before, engine.state))
    }

    @Test
    fun `the choice survives saves and older saves buy as before`() {
        val engine = engine()
        engine.state.storage[stored.id] = 1
        engine.equip(stored)
        assertTrue(GameStateJson.decode(GameStateJson.encode(engine.state)).placesDecorationsFromStorage)
        assertFalse(GameStateJson.decode("{}").placesDecorationsFromStorage)
    }
}
