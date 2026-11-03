package com.hunter.stockfarmer.ui.hud

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.equip
import com.hunter.stockfarmer.core.rules.isPlacingFromStorage
import com.hunter.stockfarmer.core.rules.selectFertilizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `a tool setting things down from storage lists what is in storage instead of its usual choices`() {
        val state = GameState().apply { storage[StorageItem.Fertilizer.id] = 3 }
        val engine = GameEngine(state)
        engine.pickUp(HudTool.Tilling)
        val usual = ToolPalette.dropdowns(state, HudTool.Tilling).single()
        assertTrue(usual.choices.size > 1 && usual.choices.none { it.id.startsWith("stored-") })
        assertTrue(engine.equip(StorageItem.Fertilizer))
        assertTrue(engine.isPlacingFromStorage)
        val stored = ToolPalette.dropdowns(state, HudTool.Tilling).single()
        assertEquals(listOf("stored-${StorageItem.Fertilizer.id}"), stored.choices.map { it.id })
        assertEquals(3, stored.stock)
        assertTrue(stored.choices.single().isSelected)
        // Another tool, which is not the one in hand, still lists its own choices.
        assertTrue(
            ToolPalette
                .dropdowns(state, HudTool.Planting)
                .single()
                .choices
                .none { it.id.startsWith("stored-") },
        )
        engine.selectFertilizer()
        assertEquals(
            usual.choices.map { it.id },
            ToolPalette
                .dropdowns(state, HudTool.Tilling)
                .single()
                .choices
                .map { it.id },
        )
    }

    @Test
    fun `decorate lists decorations, and only what is stored once it sets down from storage`() {
        val fence = StorageItem.Fence(FenceColor.starter)
        val state = GameState().apply { storage[fence.id] = 2 }
        val engine = GameEngine(state)
        engine.pickUp(HudTool.Decorating)
        val usual = ToolPalette.dropdowns(state, HudTool.Decorating).first()
        assertTrue(usual.choices.isNotEmpty() && usual.choices.none { it.id.startsWith("stored-") })
        assertTrue(engine.equip(fence))
        val stored = ToolPalette.dropdowns(state, HudTool.Decorating).first()
        assertEquals(listOf("stored-${fence.id}"), stored.choices.map { it.id })
        assertEquals(2, stored.stock)
        assertEquals(fence.name, stored.value)
    }

    @Test
    fun `stock counts read as owned amounts`() {
        assertEquals(" (x1,200)", ToolPalette.stockSuffix(1200))
        assertEquals(" (x0)", ToolPalette.stockSuffix(0))
    }

    @Test
    fun `palette choices carry detail and upgrade routes and mark seeds seen when picked`() {
        val state =
            GameState().apply {
                xp = LevelRules.xpToReach(8)
                lessonsLearned.addAll(Lesson.entries)
            }
        val engine = GameEngine(state)
        val plant = ToolPalette.dropdowns(state, HudTool.Planting).single()
        assertTrue(plant.hasNew)
        val wheat = plant.choices.first()
        assertEquals(ToolRoute.Item(ShopItem.Seed(Crop.starter.id)), wheat.detail)
        assertTrue(wheat.isNew)
        ToolPalette.perform(engine, PaletteAction.Seed(Crop.starter))
        val afterPick = ToolPalette.dropdowns(state, HudTool.Planting).single()
        assertFalse(afterPick.choices.first().isNew)

        val harvest = ToolPalette.dropdowns(state, HudTool.Harvest).first()
        val scythe = harvest.choices.first()
        assertEquals(ToolRoute.HarvestToolDetail(HarvestTool.scythe), scythe.detail)
        assertEquals(ToolRoute.ToolUpgrade(HarvestTool.scythe), scythe.upgrade)

        val terrain = ToolPalette.dropdowns(state, HudTool.Terrain).first()
        assertEquals(ToolRoute.Eraser, terrain.choices.first { it.id == "grass" }.detail)
    }
}
