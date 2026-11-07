package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.presentation.facts
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.LevelRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemDetailStateTest {
    private val engine = GameEngine()

    @Test
    fun `an item page shows the item's name and its Core facts`() {
        val crop = Crop.starter
        val item = ShopItem.Seed(crop.id)
        val state = ItemDetailState.from(engine, item)
        assertEquals(crop.plantingName, state.name)
        assertEquals(engine.facts(item), state.facts)
        assertEquals("Planted", state.stats.single().title)
    }

    @Test
    fun `detail requirements include zero costs affordability and locked stats stay hidden`() {
        val item = ShopItem.BuildingItem("petHouse")
        val locked = ItemDetailState.from(engine, item)
        assertTrue(locked.stats.isEmpty())
        assertEquals(listOf("logs", "stones", "money"), locked.requirements.map { it.id })
        assertFalse(locked.requirements.last().enough)
        val building = Building.named("petHouse")!!
        engine.state.xp = LevelRules.xpToReach(34)
        engine.state.money = building.cost
        engine.state.storage[StorageItem.Log.id] = building.logCost
        engine.state.storage[StorageItem.Stone.id] = building.stoneCost
        val ready = ItemDetailState.from(engine, item)
        assertTrue(ready.requirements.all { it.enough })
        assertEquals("Placed", ready.stats.single().title)
        assertEquals(
            listOf("Unlocked", "Placed"),
            ItemDetailState.from(engine, ShopItem.Farmland).stats.map { it.title },
        )
        assertEquals(false, ItemDetailState.from(engine, ShopItem.DecorationItem(Decoration.SPRINKLER_ID)).fountain)
    }

    @Test
    fun `item pages do not mutate the farm`() {
        val before = GameStateJson.encode(engine.state)
        Building.all.forEach { ItemDetailState.from(engine, ShopItem.BuildingItem(it.id)) }
        Crop.all.forEach { ItemDetailState.from(engine, ShopItem.Seed(it.id)) }
        Decoration.all.forEach { ItemDetailState.from(engine, ShopItem.DecorationItem(it.id)) }
        assertEquals(before, GameStateJson.encode(engine.state))
    }
}
