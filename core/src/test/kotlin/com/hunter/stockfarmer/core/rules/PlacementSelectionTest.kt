package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlacementSelectionTest {
    private val spot = GridPoint(20, 20)

    private fun engine(money: Double = 100_000.0) = GameEngine(stateWith(money = money, xp = lateGameXP))

    @Test
    fun `a plain tool places nothing and has nothing to refuse`() {
        val engine = engine()
        assertFalse(engine.selectionPlaces)
        assertNull(engine.cannotStartPlacingSelection)
        assertFalse(engine.beginPlacingSelection(spot))
        assertNull(engine.currentPlacementOrigin)
        assertNull(engine.currentPlacementFootprint)
    }

    @Test
    fun `build mode places the selected building`() {
        val engine = engine()
        engine.state.mode = PlayMode.build
        assertTrue(engine.selectionPlaces)
        assertEquals(engine.cannotStartPlacingSelectedBuilding, engine.cannotStartPlacingSelection)
        assertTrue(engine.beginPlacingSelection(spot))
        assertEquals(engine.buildingPlacement?.origin, engine.currentPlacementOrigin)
        assertEquals(engine.buildingPlacement?.footprint, engine.currentPlacementFootprint)
    }

    @Test
    fun `a fruit tree is placed like a building and refuses when unaffordable`() {
        val apple = Crop.named("apple")!!
        val engine = engine()
        engine.state.selectedCropID = apple.id
        assertTrue(engine.selectionPlaces)
        assertNull(engine.cannotStartPlacingSelection)
        assertTrue(engine.beginPlacingSelection(spot))
        assertEquals(engine.fruitPlacement?.origin, engine.currentPlacementOrigin)
        assertEquals(engine.fruitPlacement?.footprint, engine.currentPlacementFootprint)
        val poor = engine(money = 1.0)
        poor.state.selectedCropID = apple.id
        assertEquals(poor.cannotStartPlacingSelectedFruitTree, poor.cannotStartPlacingSelection)
    }

    @Test
    fun `a sprinkler places only in decorate mode`() {
        val engine = engine()
        engine.select(Decoration.named("sprinkler")!!)
        assertTrue(engine.selectionPlaces)
        assertNull(engine.cannotStartPlacingSelection)
        assertTrue(engine.beginPlacingSelection(spot))
        assertEquals(engine.objectPlacement?.origin, engine.currentPlacementOrigin)
        assertEquals(engine.objectPlacement?.footprint, engine.currentPlacementFootprint)
        engine.cancelPlacement()
        engine.state.mode = PlayMode.plant
        assertFalse(engine.selectionPlaces)
    }
}
