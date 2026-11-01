package com.hunter.stockfarmer.ui.hud

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.Rejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolPaletteTest {
    @Test
    fun `planting offers every seed and marks the held one`() {
        val state = GameState()
        GameEngine(state).pickUp(HudTool.Planting)
        val plant = ToolPalette.dropdowns(state, HudTool.Planting).single()
        assertEquals(Crop.all.size, plant.choices.size)
        assertEquals(Crop.starter.id, plant.selected?.id)
        assertNotNull(plant.choices.first().crop)
        assertTrue(plant.choices.last().requiredLevel != null)
    }

    @Test
    fun `picking a locked seed only says what unlocks it`() {
        val engine = GameEngine(GameState())
        val rejection = ToolPalette.perform(engine, PaletteAction.Seed(Crop.all.last()))
        assertTrue(rejection is Rejection.NeedLevel)
        assertEquals(Crop.starter, engine.selectedCrop)
    }

    @Test
    fun `harvest lists the implements with a store or sell dropdown for those that can sell`() {
        val state = GameState()
        val engine = GameEngine(state)
        engine.pickUp(HudTool.Harvest)
        val scythe = ToolPalette.dropdowns(state, HudTool.Harvest)
        assertEquals(listOf("Harvest", "harvestDestination"), scythe.map { it.id })
        assertEquals(HarvestTool.entries.size, scythe.first().choices.size)
        ToolPalette.perform(engine, PaletteAction.Implement(HarvestTool.axe))
        assertEquals(listOf("Harvest"), ToolPalette.dropdowns(state, HudTool.Harvest).map { it.id })
    }

    @Test
    fun `the destination dropdown toggles selling`() {
        val state = GameState()
        val engine = GameEngine(state)
        engine.pickUp(HudTool.Harvest)
        ToolPalette.perform(engine, PaletteAction.Destination(true))
        assertEquals("Sell", ToolPalette.dropdowns(state, HudTool.Harvest)[1].value)
        ToolPalette.perform(engine, PaletteAction.Destination(false))
        assertEquals("Store", ToolPalette.dropdowns(state, HudTool.Harvest)[1].value)
    }

    @Test
    fun `build and terrain come from the menu catalogs`() {
        val state = GameState()
        val engine = GameEngine(state)
        engine.pickUp(HudTool.Building)
        assertTrue(
            ToolPalette
                .dropdowns(state, HudTool.Building)
                .first()
                .choices
                .isNotEmpty(),
        )
        engine.pickUp(HudTool.Terrain)
        assertEquals("Terrain", ToolPalette.dropdowns(state, HudTool.Terrain).first().id)
    }

    @Test
    fun `fertilize holds fertilizer by default and shows no stock for it`() {
        val state = GameState()
        GameEngine(state).pickUp(HudTool.Tilling)
        val fertilize = ToolPalette.dropdowns(state, HudTool.Tilling).single()
        assertEquals("Fertilizer", fertilize.value)
        assertNull(fertilize.stock)
    }

    @Test
    fun `stock counts read like iOS`() {
        assertEquals(" (x1,200)", ToolPalette.stockSuffix(1200))
        assertEquals(" (x0)", ToolPalette.stockSuffix(0))
    }
}
