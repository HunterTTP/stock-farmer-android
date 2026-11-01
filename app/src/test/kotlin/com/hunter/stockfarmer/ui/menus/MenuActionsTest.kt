package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.rules.Butterflies
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.isEquipped
import com.hunter.stockfarmer.core.rules.selectedBuilding
import com.hunter.stockfarmer.core.rules.selectedDecorationSize
import com.hunter.stockfarmer.core.rules.selectedTreeColor
import com.hunter.stockfarmer.game.GameSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuActionsTest {
    @Test
    fun `locked item and variant selection returns core refusal without changing selection`() {
        val engine = GameEngine()
        val building = Building.all.first { it.unlockLevel > 1 && it.styles.size > 1 }
        val decoration = Decoration.shopChoices.first { it.unlockLevel > 1 }
        val selections =
            listOf(
                MenuSelection.Shop(ShopItem.BuildingItem(building.id)) to building.unlockLevel,
                MenuSelection.Style(building, building.styles.last()) to building.unlockLevel,
                MenuSelection.Size(decoration, decoration.offeredSizes.last()) to decoration.unlockLevel,
            )
        val before = GameStateJson.encode(engine.state)
        selections.forEach { (selection, level) ->
            assertEquals(Rejection.NeedLevel(level), MenuActions.choose(engine, selection))
            assertEquals(before, GameStateJson.encode(engine.state))
        }
    }

    @Test
    fun `every unlocked catalog selection only picks a tool without spending or placing`() {
        val engine = GameEngine(unlockedMenuFarm().apply { money = 0.0 })
        listOf(Menu.Build, Menu.Decorate, Menu.Terrain).forEach { menu ->
            MenuState.from(engine.state, menu).sections.flatMap { it.choices }.forEach { choice ->
                assertNull(MenuActions.choose(engine, choice.selection), choice.title)
                if (choice.selection is MenuSelection.Shop) {
                    assertTrue(engine.isSelected(choice.selection.item), choice.title)
                }
                assertEquals(0.0, engine.state.money)
                assertTrue(engine.state.buildings.isEmpty())
                assertTrue(engine.state.decorations.isEmpty())
                assertTrue(engine.state.trees.isEmpty())
            }
        }
    }

    @Test
    fun `variant selection remembers the chosen building size tree and water artwork`() {
        val engine = GameEngine(unlockedMenuFarm())
        val building = Building.all.first { it.styles.size > 1 }
        val style = building.styles.last()
        assertNull(MenuActions.choose(engine, MenuSelection.Style(building, style)))
        assertEquals(style, engine.selectedBuilding.style)
        val choice =
            MenuState
                .from(engine.state, Menu.Build)
                .choice(MenuSelection.Shop(ShopItem.BuildingItem(building.id)))
        assertEquals(MenuPictures.building(building.styled(style.id)), choice.picture)
        assertTrue(choice.selected)
        assertTrue(choice.variants.single { it.id == style.id }.selected)
        val decoration = Decoration.shopChoices.first { it.offeredSizes.size > 1 && it.sizeNames.size > 1 }
        val size = decoration.offeredSizes.last()
        assertNull(MenuActions.choose(engine, MenuSelection.Size(decoration, size)))
        assertEquals(size, engine.selectedDecorationSize)
        val species = TreeSpecies.all.last()
        assertNull(MenuActions.choose(engine, MenuSelection.Tree(species, species.colors.last())))
        assertEquals(species.smallest.id, engine.state.selectedTreeID)
        assertEquals(species.colors.last(), engine.selectedTreeColor)
        assertNull(MenuActions.choose(engine, MenuSelection.Water(WaterStyle.shallow)))
        assertEquals(WaterStyle.shallow, engine.state.selectedWaterStyle)
        assertTrue(MenuState.from(engine.state, Menu.Terrain).choice(MenuSelection.Shop(ShopItem.Water)).selected)
    }

    @Test
    fun `all equippable storage kinds are equipped without consuming stock`() {
        val engine = GameEngine(unlockedMenuFarm())
        StorageItem.all.filter { it.isEquippable }.forEach { item ->
            engine.state.storage[item.id] = 2
            assertNull(MenuActions.choose(engine, MenuSelection.Stored(item)), item.id)
            assertTrue(engine.isEquipped(item), item.id)
            assertEquals(2, engine.state.storage[item.id])
            assertTrue(
                MenuState
                    .from(engine.state, Menu.Storage)
                    .stored
                    .single { it.item == item }
                    .selected,
            )
        }
    }

    @Test
    fun `stored selection refuses exhausted stock and locked decorations`() {
        val engine = GameEngine()
        val item = StorageItem.Fence(FenceColor.starter)
        val refusal = Rejection.NoneInStorage(item.name)
        assertEquals(refusal, MenuActions.choose(engine, MenuSelection.Stored(item)))
        assertEquals(refusal.message, MenuActions.choose(engine, MenuSelection.Stored(item))?.message)
        val decoration = Decoration.shopChoices.first { it.unlockLevel > 1 }
        val stored = StorageItem.DecorationItem(decoration, decoration.offeredSizes.first())
        engine.state.storage[stored.id] = 1
        assertEquals(
            Rejection.NeedLevel(decoration.unlockLevel),
            MenuActions.choose(engine, MenuSelection.Stored(stored)),
        )
        assertFalse(engine.state.placesDecorationsFromStorage)
    }

    @Test
    fun `buying selection after equipping storage clears the equipped marker`() {
        val engine = GameEngine(unlockedMenuFarm())
        val item = StorageItem.Fence(FenceColor.starter)
        engine.state.storage[item.id] = 1
        MenuActions.choose(engine, MenuSelection.Stored(item))
        val stored = MenuState.from(engine.state, Menu.Decorate)
        assertFalse(stored.choice(MenuSelection.Shop(ShopItem.Fence)).selected)
        assertTrue(stored.choice(MenuSelection.Stored(item)).selected)
        MenuActions.choose(engine, MenuSelection.Shop(ShopItem.Fence))
        assertFalse(engine.state.placesDecorationsFromStorage)
        assertTrue(MenuState.from(engine.state, Menu.Decorate).choice(MenuSelection.Shop(ShopItem.Fence)).selected)
    }

    @Test
    fun `sale through session credits core price and reports actual quantity`() {
        val engine =
            GameEngine(
                GameState().apply {
                    money = 0.0
                    storage[StorageItem.Egg.id] = 5
                },
            )
        val session = GameSession(engine)
        val message = session.act { MenuActions.transact(it, StorageItem.Egg, 3, sell = true) }
        val expected = StorageItem.Egg.sellValue * 3
        assertEquals("Sold 3 Egg for ${expected.dollars}", message)
        assertEquals(expected, session.read { it.money })
        assertEquals(2, session.read { it.storage[StorageItem.Egg.id] })
    }

    @Test
    fun `trash and release remove stock without payment`() {
        val butterfly = StorageItem.Butterfly(Butterflies.Wings.Shifting)
        val engine =
            GameEngine(
                GameState().apply {
                    money = 0.0
                    storage[StorageItem.Log.id] = 3
                    storage[butterfly.id] = 2
                },
            )
        assertEquals("Trashed 3 Log", MenuActions.transact(engine, StorageItem.Log, 3, sell = false))
        assertEquals("Released 2 Shifting Butterfly", MenuActions.transact(engine, butterfly, 2, sell = false))
        assertTrue(engine.state.storage.isEmpty())
        assertEquals(0.0, engine.state.money)
        assertEquals("Release", butterfly.discardTitle)
        assertFalse(butterfly.isSellable)
        assertFalse(StorageItem.Log.isSellable)
    }

    @Test
    fun `stale quantities and unsellable items leave storage and money alone`() {
        val engine = GameEngine(GameState().apply { storage[StorageItem.Log.id] = 2 })
        val before = GameStateJson.encode(engine.state)
        assertNull(MenuActions.transact(engine, StorageItem.Log, 1, sell = true))
        listOf(-1, 0, 3).forEach { quantity ->
            assertNull(MenuActions.transact(engine, StorageItem.Log, quantity, sell = false))
        }
        assertEquals(before, GameStateJson.encode(engine.state))
        assertEquals(PlayMode.plant, engine.state.mode)
    }
}
