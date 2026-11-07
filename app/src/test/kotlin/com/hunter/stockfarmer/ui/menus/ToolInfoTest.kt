package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.presentation.facts
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.level
import com.hunter.stockfarmer.core.rules.nextUpgrade
import com.hunter.stockfarmer.game.GameSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolInfoTest {
    @Test
    fun `an upgrade page shows the next upgrade's Core facts`() {
        val engine = GameEngine()
        val upgrade = engine.nextUpgrade(HarvestTool.axe)!!
        assertEquals(engine.facts(upgrade), ToolUpgradeState.from(engine, HarvestTool.axe).facts)
    }

    @Test
    fun `upgrade refuses a lock or insufficient funds and dismisses only after a purchase`() {
        val engine = GameEngine()
        val session = GameSession(engine)
        assertEquals(5, ToolUpgradeState.from(engine, HarvestTool.axe).requiredLevel)
        assertFalse(session.upgradeTool(HarvestTool.axe).bought)
        assertEquals(1, engine.level(HarvestTool.axe))
        engine.state.xp = LevelRules.xpToReach(5)
        engine.state.money = 399.0
        assertFalse(ToolUpgradeState.from(engine, HarvestTool.axe).affordable)
        assertFalse(session.upgradeTool(HarvestTool.axe).bought)
        assertEquals(399.0, engine.state.money)
        engine.state.money = 400.0
        val result = session.upgradeTool(HarvestTool.axe)
        assertTrue(result.bought)
        assertEquals("Axe upgraded to Level 2", result.message)
        assertEquals(0.0, engine.state.money)
        assertEquals(2, engine.level(HarvestTool.axe))
    }

    @Test
    fun `fully upgraded tool has its current level and no purchase controls`() {
        val engine = GameEngine()
        engine.state.toolLevels[HarvestTool.axe.rawValue] = 3
        val state = ToolUpgradeState.from(engine, HarvestTool.axe)
        assertEquals("Axe Level 3", state.title)
        assertNull(state.upgrade)
        assertNull(state.facts)
        assertFalse(GameSession(engine).upgradeTool(HarvestTool.axe).bought)
    }
}
