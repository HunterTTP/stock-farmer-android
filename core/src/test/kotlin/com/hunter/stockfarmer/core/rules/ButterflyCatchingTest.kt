package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.Rarity
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ButterflyCatchingTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val spot = GridPoint(20, 20)
    private val rare = Butterflies.Wings.Color(Rarity.rare)
    private val rareItem = StorageItem.Butterfly(rare)

    private fun engine(xp: Int = LevelRules.xpToReach(10)) = GameEngine(stateWith(money = 100.0, xp = xp))

    private fun seen(
        engine: GameEngine,
        wings: Butterflies.Wings,
    ) = engine.hasSeenEnvironment(CareerRecord.environmentID(wings))

    @Test
    fun `wings have stable ids and survive saves`() {
        assertEquals(
            listOf("common", "uncommon", "rare", "epic", "legendary", "shifting"),
            Butterflies.Wings.all.map { it.id },
        )
        for (wings in Butterflies.Wings.all) assertEquals(wings, Butterflies.Wings.of(wings.id))
        assertNull(Butterflies.Wings.of("nonsense"))
    }

    @Test
    fun `a butterfly is a storage item that cannot be sold but can be equipped`() {
        assertTrue(rareItem.id == "butterfly-rare" && rareItem.name == "Rare Butterfly")
        assertEquals(rareItem, StorageItem.of(rareItem.id))
        assertTrue(!rareItem.isSellable && rareItem.isEquippable && rareItem.unlockLevel == 1)
        assertEquals(Butterflies.Wings.all.size, StorageItem.all.count { it is StorageItem.Butterfly })
        assertTrue(rareItem.discardTitle == "Release" && StorageItem.Egg.discardTitle == "Trash")
        assertEquals("Release", StorageItem.Fish(FishKind.all[0]).discardTitle)
        val engine = engine()
        engine.state.storage[rareItem.id] = 1
        assertTrue(engine.sellStored(rareItem, 1).isEmpty())
        assertTrue(engine.equip(rareItem) && engine.state.heldItemID == "butterfly-rare")
        assertEquals(PlayMode.decorate, engine.state.mode)
    }

    @Test
    fun `releasing from storage deletes it like trashing`() {
        val engine = engine()
        engine.state.storage["butterfly-rare"] = 2
        assertEquals(
            listOf<GameEvent>(GameEvent.StorageTrashed("butterfly-rare", 2)),
            engine.trashStored(rareItem, 2),
        )
        assertNull(engine.state.storage["butterfly-rare"])
    }

    @Test
    fun `catching a wild one stores it earns XP and credits the career`() {
        val engine = engine()
        val xpBefore = engine.state.xp
        val events = engine.catchButterfly(rare, null, spot, now)
        val xp = LevelRules.butterflyXP(rare, engine.state.level)
        assertTrue(xp > 0 && events.first() == GameEvent.Stored(spot, "butterfly-rare", xp))
        assertTrue(engine.state.storage["butterfly-rare"] == 1 && engine.state.xp == xpBefore + xp)
        assertTrue(seen(engine, rare))
    }

    @Test
    fun `catching never sells even when the tool sells`() {
        val engine = engine()
        engine.state.sellingTools = mutableSetOf(HarvestTool.bucket)
        engine.state.harvestTool = HarvestTool.bucket
        val money = engine.state.money
        engine.catchButterfly(rare, null, spot, now)
        assertTrue(engine.state.money == money && engine.state.storage["butterfly-rare"] == 1)
    }

    @Test
    fun `a full storage refuses and nothing changes`() {
        val engine = engine()
        engine.state.storage[StorageItem.Log.id] = engine.storageCapacity
        val before = engine.state.copy()
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.StorageFull)),
            engine.catchButterfly(rare, null, spot, now),
        )
        assertTrue(sameState(before, engine.state))
    }

    @Test
    fun `xp grows with rarity and the level and stops at the last level`() {
        val xp = Butterflies.Wings.all.map { LevelRules.butterflyXP(it, 10) }
        assertTrue(xp == xp.sorted() && xp.first() >= 1 && xp.last() > 10 * xp.first())
        assertTrue(LevelRules.butterflyXP(rare, 20) > LevelRules.butterflyXP(rare, 5))
        assertEquals(0, LevelRules.butterflyXP(Butterflies.Wings.Shifting, LevelRules.maxLevel))
        val carp = FishKind.named("carp")!!
        val fish = LevelRules.fishXP(carp, carp.weightRange.start, 10)
        assertTrue(LevelRules.butterflyXP(Butterflies.Wings.Color(Rarity.common), 10) <= fish)
    }

    @Test
    fun `releasing puts one on the map from storage`() {
        val engine = engine()
        engine.state.storage["butterfly-rare"] = 2
        engine.equip(rareItem)
        assertEquals(listOf<GameEvent>(GameEvent.Placed(spot)), engine.useTool(spot, now))
        assertEquals(1, engine.state.storage["butterfly-rare"])
        assertEquals(listOf(rare), engine.state.releasedButterflies.map { it.wings })
        assertEquals(listOf(spot), engine.state.releasedButterflies.map { it.home })
    }

    @Test
    fun `at most ten can be released at once`() {
        val engine = engine()
        engine.state.storage["butterfly-rare"] = 12
        engine.equip(rareItem)
        for (column in 0 until Butterflies.MOST_RELEASED) {
            val tile = GridPoint(20, column)
            assertEquals(listOf<GameEvent>(GameEvent.Placed(tile)), engine.useTool(tile, now))
        }
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.TooManyButterflies)), engine.useTool(spot, now))
        assertTrue(engine.state.releasedButterflies.size == 10 && engine.state.storage["butterfly-rare"] == 2)
        assertFalse(Rejection.TooManyButterflies.isTileSpecific)
    }

    @Test
    fun `catching a released one gives no XP no career credit and makes room for another`() {
        val engine = engine()
        engine.state.storage["butterfly-rare"] = 1
        engine.equip(rareItem)
        engine.useTool(spot, now)
        val id =
            engine.state.releasedButterflies
                .first()
                .id
        val xp = engine.state.xp
        assertEquals(
            listOf<GameEvent>(GameEvent.Stored(spot, "butterfly-rare", 0)),
            engine.catchButterfly(rare, id, spot, now),
        )
        assertTrue(engine.state.xp == xp && engine.state.releasedButterflies.isEmpty())
        assertEquals(1, engine.state.storage["butterfly-rare"])
        assertFalse(seen(engine, rare))
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NothingHere)),
            engine.catchButterfly(rare, id, spot, now),
        )
    }

    @Test
    fun `released butterflies survive saves and older saves have none`() {
        val engine = engine()
        engine.state.storage["butterfly-epic"] = 1
        engine.equip(StorageItem.Butterfly(Butterflies.Wings.Color(Rarity.epic)))
        engine.useTool(spot, now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertTrue(
            loaded.releasedButterflies == engine.state.releasedButterflies && loaded.releasedButterflies.size == 1,
        )
        assertTrue(GameStateJson.decode("{}").releasedButterflies.isEmpty())
    }

    @Test
    fun `every building has a night light except the tractor`() {
        assertEquals(listOf("tractor1"), Building.all.filter { !it.glowsAtNight }.map { it.id })
    }
}
