package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.ToolUpgrade
import com.hunter.stockfarmer.core.rules.level
import com.hunter.stockfarmer.game.GameSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolInfoTest {
    @Test
    fun `every implement describes both upgrade levels and the rod does not promise selling`() {
        assertEquals(
            "Upgrade to Level 2 at Level 5 and to Level 3 at Level 15",
            ToolInfo.effects(HarvestTool.axe).last().text(),
        )
        assertEquals(
            "Catches go into storage",
            ToolInfo
                .effects(HarvestTool.rod)
                .dropLast(1)
                .last()
                .text(),
        )
        assertTrue(ToolInfo.effects(HarvestTool.bucket).any { it.text().contains("butterfly") })
        HarvestTool.entries.forEach { assertTrue(ToolInfo.effects(it).size >= 5) }
    }

    @Test
    fun `upgrade descriptions cover all effects and preserve decimal rod reach`() {
        val effects = ToolUpgrade.all.map { ToolInfo.upgradeEffect(it).text() }
        assertTrue(effects.contains("Takes half as many passes on everything it works on, rounded up"))
        assertTrue(effects.contains("Takes 1 pass on everything it works on"))
        assertTrue(effects.contains("The circle in fishing mode is 2.25× as wide"))
        assertTrue(effects.contains("35% chance of gathering a second item each time"))
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
        assertTrue(state.effects.isEmpty())
        assertFalse(GameSession(engine).upgradeTool(HarvestTool.axe).bought)
    }
}
