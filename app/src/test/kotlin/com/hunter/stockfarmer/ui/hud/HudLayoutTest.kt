package com.hunter.stockfarmer.ui.hud

import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.rules.GameEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HudLayoutTest {
    @Test
    fun `the ring has six tools on the outer arc and three screens on the inner one`() {
        val slots = ToolRingLayout.slots
        assertEquals(9, slots.size)
        assertEquals(HudTool.entries, slots.take(6).map { (it.satellite as Satellite.OfTool).tool })
        assertEquals(listOf(90.0, 72.0, 54.0, 36.0, 18.0, 0.0), slots.take(6).map { it.angle })
        assertEquals(listOf(80.0, 45.0, 10.0), slots.drop(6).map { it.angle })
        assertEquals(listOf("Storage", "Settings", "Time"), slots.drop(6).map { it.satellite.title })
    }

    @Test
    fun `ring spots fan up and to the right of the circle`() {
        val top = ToolRingLayout.spot(ToolRingLayout.slots[0], 1f)
        val right = ToolRingLayout.spot(ToolRingLayout.slots[5], 1f)
        assertEquals(34f, top.first, 0.01f)
        assertEquals(290f - 34f - 222f, top.second, 0.01f)
        assertEquals(34f + 222f, right.first, 0.01f)
        assertEquals(290f - 34f, right.second, 0.01f)
        assertEquals(290f * 1.3f, ToolRingLayout.span(1.3f), 0.01f)
    }

    @Test
    fun `chips wrap into a new row when the next does not fit`() {
        assertEquals(listOf(listOf(0, 1), listOf(2)), WrappingRows.rows(listOf(100, 100, 100), 215, 10))
        assertEquals(listOf(listOf(0, 1, 2)), WrappingRows.rows(listOf(100, 100, 100), 320, 10))
        assertEquals(listOf(listOf(0), listOf(1)), WrappingRows.rows(listOf(300, 300), 200, 10))
    }

    @Test
    fun `banners sit a tenth down on a phone and centered on a tablet`() {
        assertEquals(80.dp, BannerPlacement.topOffset(800.dp, isTablet = false))
        assertEquals(0.dp, BannerPlacement.topOffset(800.dp, isTablet = true))
    }

    @Test
    fun `each mode maps to its tool and tools hold their last choice`() {
        val engine = GameEngine(GameState())
        engine.setMode(PlayMode.plant)
        assertEquals(HudTool.Planting, engine.currentTool)
        engine.pickUp(HudTool.Building)
        assertEquals(HudTool.Building, engine.currentTool)
        engine.pickUp(HudTool.Fertilizing)
        assertEquals(HudTool.Fertilizing, engine.currentTool)
        engine.pickUp(HudTool.Harvest)
        assertEquals(PlayMode.harvest, engine.state.mode)
    }

    @Test
    fun `activation pauses follow the mode`() {
        val engine = GameEngine(GameState())
        engine.pickUp(HudTool.Planting)
        assertNull(engine.activationDelayMillis)
        engine.pickUp(HudTool.Harvest)
        assertNull(engine.activationDelayMillis)
        engine.pickUp(HudTool.Building)
        assertEquals(120L, engine.activationDelayMillis)
        engine.pickUp(HudTool.Fertilizing)
        assertEquals(70L, engine.activationDelayMillis)
    }

    @Test
    fun `status text formats money with cents and the level caption`() {
        assertEquals("$1,234.50", StatusText.currency(1234.5))
        assertEquals("$25.00", StatusText.currency(25.0))
        assertEquals("120 / 1,300 XP", StatusText.levelCaption(2, 120, 1300))
        assertEquals("Max level", StatusText.levelCaption(Int.MAX_VALUE, 0, 0))
    }
}
