package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolMemoryTest {
    private fun engine() = GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP))

    @Test
    fun `terrain starts on farmland and remembers farmland water or grass`() {
        val engine = GameEngine()
        engine.selectTerrain()
        assertEquals(LandscapeTool.farmland, engine.state.selectedLandscape)
        engine.select(LandscapeTool.grass)
        engine.selectFertilizer()
        engine.selectTerrain()
        assertEquals(LandscapeTool.grass, engine.state.selectedLandscape)
        engine.select(LandscapeTool.water)
        engine.setMode(PlayMode.plant)
        engine.selectTerrain()
        assertTrue(engine.state.selectedLandscape == LandscapeTool.water && engine.state.mode == PlayMode.landscape)
    }

    @Test
    fun `fertilize starts on fertilizer and remembers sprinklers`() {
        val engine = engine()
        engine.selectFertilize()
        assertEquals(PlayMode.fertilize, engine.state.mode)
        engine.select(Decoration.named("sprinkler")!!)
        engine.select(LandscapeTool.water)
        engine.selectFertilize()
        assertTrue(engine.state.mode == PlayMode.decorate && engine.selectedDecoration?.isSprinkler == true)
        engine.selectFertilizer()
        engine.select(LandscapeTool.water)
        engine.selectFertilize()
        assertEquals(PlayMode.fertilize, engine.state.mode)
    }

    @Test
    fun `decorate remembers fences or a decoration separately from terrain trees`() {
        val engine = engine()
        engine.selectDecorating()
        assertTrue(engine.state.mode == PlayMode.decorate && engine.selectedDecoration == null)
        engine.select(TreeSpecies.oak)
        engine.selectDecorating()
        assertTrue(engine.state.mode == PlayMode.decorate && engine.selectedDecoration == null)
        engine.select(Decoration.named("scarecrow")!!)
        engine.select(TreeSpecies.pine)
        engine.selectDecorating()
        assertEquals("scarecrow", engine.selectedDecoration?.id)
        engine.selectTerrain()
        assertTrue(
            engine.state.selectedLandscape == LandscapeTool.tree && engine.selectedTree.species == TreeSpecies.pine,
        )
    }

    @Test
    fun `a sprinkler never becomes what the decorate tool remembers`() {
        val engine = engine()
        engine.select(Decoration.named("well")!!)
        engine.select(Decoration.named("sprinkler")!!)
        engine.selectDecorating()
        assertEquals("well", engine.selectedDecoration?.id)
    }

    @Test
    fun `terrain remembers trees and their color across tools and saving`() {
        val engine = engine()
        engine.select(LandscapeTool.grass)
        engine.select(TreeSpecies.pine)
        val color = TreeSpecies.pine.colors.last()
        engine.select(color)
        engine.selectFertilizer()
        engine.selectTerrain()
        assertTrue(
            engine.state.selectedLandscape == LandscapeTool.tree && engine.state.selectedTerrain == LandscapeTool.tree,
        )
        val restored = GameEngine(GameStateJson.decode(GameStateJson.encode(engine.state)))
        restored.selectDecorating()
        restored.selectTerrain()
        assertTrue(restored.state.selectedLandscape == LandscapeTool.tree && restored.selectedTreeColor == color)
        assertNull(restored.selectedDecoration)
    }
}
