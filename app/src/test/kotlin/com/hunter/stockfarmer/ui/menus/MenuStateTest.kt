package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.rules.LevelRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuStateTest {
    @Test
    fun `build lists every offered building in stable unlock order`() {
        val choices =
            MenuState
                .from(GameState(), Menu.Build)
                .sections
                .single()
                .choices
        val expected = Building.all.sortedBy { it.unlockLevel }.map { MenuSelection.Shop(ShopItem.BuildingItem(it.id)) }
        assertEquals(expected, choices.map { it.selection })
        assertEquals(choices.size, choices.map { it.id }.distinct().size)
    }

    @Test
    fun `locked buildings show their required level instead of a price`() {
        val choices =
            MenuState
                .from(GameState(), Menu.Build)
                .sections
                .single()
                .choices
        val locked = choices.filter { it.requiredLevel != null }
        assertTrue(locked.isNotEmpty())
        locked.forEach {
            assertEquals("Unlocks at Level ${it.requiredLevel}", it.subtitle)
            assertTrue(it.prices.isEmpty())
        }
    }

    @Test
    fun `building costs distinguish money logs and stones`() {
        val state =
            unlockedMenuFarm().apply {
                money = 0.0
                storage[StorageItem.Log.id] = 10_000
            }
        val building = Building.all.first { it.logCost > 0 && it.stoneCost > 0 }
        val choice = MenuState.from(state, Menu.Build).choice(MenuSelection.Shop(ShopItem.BuildingItem(building.id)))
        assertEquals(listOf(false, true, false), choice.prices.map { it.affordable })
        assertFalse(choice.affordable)
        assertTrue(choice.prices[1].text.endsWith(" logs"))
        assertTrue(choice.prices[2].text.endsWith(" stones"))
    }

    @Test
    fun `building placement counts and limit reflect the farm`() {
        val state = unlockedMenuFarm()
        val building = Building.all.first { it.placementLimit == 1 }
        state.putBuilding(GridPoint(10, 10), building.id)
        val choice = MenuState.from(state, Menu.Build).choice(MenuSelection.Shop(ShopItem.BuildingItem(building.id)))
        assertTrue(choice.title.endsWith("(1/1)"))
        assertTrue(choice.atLimit)
    }

    @Test
    fun `decorate separates sprinklers and only lists equippable stored items`() {
        val state =
            unlockedMenuFarm().apply {
                storage["wheat"] = 2
                storage["log"] = 3
            }
        val sections = MenuState.from(state, Menu.Decorate).sections
        assertEquals(listOf("Decorations", "Sprinklers", "Stored items"), sections.map { it.title })
        val decorations = sections[0].choices.map { (it.selection as MenuSelection.Shop).item }
        assertTrue(ShopItem.Fence in decorations)
        val sprinklers = sections[1].choices.map { (it.selection as MenuSelection.Shop).item }
        assertEquals(
            Decoration.shopChoices.filter { it.isSprinkler }.map { ShopItem.DecorationItem(it.id) },
            sprinklers,
        )
        assertEquals(listOf("wheat"), sections[2].choices.map { (it.selection as MenuSelection.Stored).item.id })
        assertTrue(
            sections[2]
                .choices
                .single()
                .subtitle
                .contains("2 in storage"),
        )
    }

    @Test
    fun `terrain includes ground grass water styles and every tree species`() {
        val menu = MenuState.from(unlockedMenuFarm(), Menu.Terrain)
        val ground = menu.sections.first().choices
        assertTrue(ground.any { it.selection == MenuSelection.Grass })
        GroundCover.entries.forEach { cover ->
            assertTrue(ground.any { it.selection == MenuSelection.Shop(ShopItem.GroundCoverItem(cover)) })
        }
        val water = menu.choice(MenuSelection.Shop(ShopItem.Water))
        assertEquals(WaterStyle.entries.map { MenuSelection.Water(it) }, water.variants.map { it.selection })
        assertEquals(
            TreeSpecies.all.sortedBy { it.unlockLevel }.map { MenuSelection.Shop(ShopItem.Tree(it.id)) },
            menu.sections
                .last()
                .choices
                .map { it.selection },
        )
    }

    @Test
    fun `variants only contain the sizes styles and colors offered by core`() {
        val state = unlockedMenuFarm()
        val build = MenuState.from(state, Menu.Build)
        Building.all.forEach { building ->
            val variants = build.choice(MenuSelection.Shop(ShopItem.BuildingItem(building.id))).variants
            val styles = building.styles.takeIf { it.size > 1 }.orEmpty()
            assertEquals(styles.map { MenuSelection.Style(building, it) }, variants.map { it.selection })
        }
        val decorate = MenuState.from(state, Menu.Decorate)
        Decoration.shopChoices.forEach { decoration ->
            val variants = decorate.choice(MenuSelection.Shop(ShopItem.DecorationItem(decoration.id))).variants
            val sizes = decoration.offeredSizes.takeIf { decoration.sizeNames.size > 1 && it.size > 1 }.orEmpty()
            assertEquals(sizes.map { MenuSelection.Size(decoration, it) }, variants.map { it.selection })
        }
        val terrain = MenuState.from(state, Menu.Terrain)
        TreeSpecies.all.forEach { species ->
            val variants = terrain.choice(MenuSelection.Shop(ShopItem.Tree(species.id))).variants
            assertEquals(species.colors.map { MenuSelection.Tree(species, it) }, variants.map { it.selection })
        }
    }

    @Test
    fun `storage follows iOS progression order and excludes empty or unknown entries`() {
        val state =
            GameState().apply {
                listOf("coffee", "wheat", "egg", "milk", "log", "cotton", "cloth").forEach { storage[it] = 1 }
                storage["stone"] = 0
                storage["unknown"] = 1
            }
        val menu = MenuState.from(state, Menu.Storage)
        assertEquals(listOf("wheat", "log", "egg", "cloth", "milk", "cotton", "coffee"), menu.stored.map { it.item.id })
        assertTrue(menu.sections.isEmpty())
    }

    @Test
    fun `capacity includes sheds and silos and clamps its progress`() {
        val state =
            GameState().apply {
                putBuilding(GridPoint(10, 10), "shed")
                putBuilding(GridPoint(20, 20), "silo")
                storage["wheat"] = 400
            }
        val menu = MenuState.from(state, Menu.Storage)
        assertEquals(300, menu.capacity)
        assertEquals("400 / 300", menu.capacityText)
        assertEquals(1f, menu.capacityProgress)
        assertEquals(0f, menu.copy(used = 0, capacity = 0).capacityProgress)
    }

    @Test
    fun `snapshots retain no mutable farm state`() {
        val state = GameState().apply { storage["wheat"] = 1234 }
        val menu = MenuState.from(state, Menu.Storage)
        state.storage.clear()
        state.money = 99.0
        assertEquals(1234, menu.stored.single().count)
        assertEquals("\u00d71,234", menu.stored.single().countText)
        assertEquals(GameState().money.dollars, menu.money)
    }

    @Test
    fun `reading every menu leaves the session farm unchanged`() {
        val state = unlockedMenuFarm().apply { storage["wheat"] = 3 }
        val before = GameStateJson.encode(state)
        Menu.entries.forEach { MenuState.from(state, it) }
        assertEquals(before, GameStateJson.encode(state))
    }

    @Test
    fun `quantity stays within stock including when stock disappears`() {
        assertEquals(1, MenuNumbers.quantity(-4, 10))
        assertEquals(10, MenuNumbers.quantity(Int.MAX_VALUE, 10))
        assertEquals(4, MenuNumbers.quantity(4, 10))
        assertEquals(0, MenuNumbers.quantity(4, 0))
        assertEquals(0, MenuNumbers.quantity(4, -1))
        assertEquals("1,234,567", MenuNumbers.count(1_234_567))
    }
}

internal fun unlockedMenuFarm(): GameState = GameState().apply { xp = LevelRules.xpToReach(LevelRules.maxLevel) }

internal fun MenuState.choice(selection: MenuSelection): MenuChoice =
    sections.flatMap { it.choices }.single { it.selection == selection }
