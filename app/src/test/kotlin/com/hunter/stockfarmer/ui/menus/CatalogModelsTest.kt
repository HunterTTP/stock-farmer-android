package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.rules.GameEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CatalogModelsTest {
    private val engine = GameEngine()

    @Test
    fun `detail pages omit building styles and decoration sizes`() {
        Building.all.forEach { building ->
            assertTrue(ItemDetailState.from(engine, ShopItem.BuildingItem(building.id)).colors.isEmpty())
        }
        Decoration.shopChoices.forEach { decoration ->
            assertTrue(ItemDetailState.from(engine, ShopItem.DecorationItem(decoration.id)).colors.isEmpty())
        }
    }

    @Test
    fun `water detail lists colors and selecting one updates the preference`() {
        val colors = ItemDetailState.from(engine, ShopItem.Water).colors
        assertEquals(WaterStyle.entries.size, colors.size)
        val selection = colors.last().selection as MenuSelection.Water
        selectItemDetailColor(engine, selection)
        val updated = ItemDetailState.from(engine, ShopItem.Water)
        assertEquals(selection.style, engine.state.selectedWaterStyle)
        assertEquals(selection, updated.colors.single { it.selected }.selection)
    }

    @Test
    fun `tree detail shows its preferred color even when another species is held`() {
        val species = TreeSpecies.all.first { it.smallest.id != engine.state.selectedTreeID && it.colors.size > 1 }
        val preferred = species.colors.last()
        engine.state.selectedTreeColorID = preferred.id
        val colors = ItemDetailState.from(engine, ShopItem.Tree(species.id)).colors
        assertEquals(preferred.id, (colors.single { it.selected }.selection as MenuSelection.Tree).color.id)
    }

    @Test
    fun `changing a detail tree color leaves the held species alone`() {
        val species = TreeSpecies.all.first { it.smallest.id != engine.state.selectedTreeID }
        val held = engine.state.selectedTreeID
        val selection = itemDetailColors(engine, ShopItem.Tree(species.id)).last().selection
        selectItemDetailColor(engine, selection)
        assertEquals(held, engine.state.selectedTreeID)
        assertEquals((selection as MenuSelection.Tree).color.id, engine.state.selectedTreeColorID)
    }

    @Test
    fun `tree detail falls back to its own first color when the preference is unavailable`() {
        engine.state.selectedTreeColorID = "unknown-color"
        TreeSpecies.all.forEach { species ->
            val colors = itemDetailColors(engine, ShopItem.Tree(species.id))
            assertEquals(
                species.colors.first().id,
                (colors.single { it.selected }.selection as MenuSelection.Tree).color.id,
            )
        }
    }
}
