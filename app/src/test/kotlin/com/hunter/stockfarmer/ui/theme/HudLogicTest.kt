package com.hunter.stockfarmer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.hunter.stockfarmer.core.catalog.Rarity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HudLogicTest {
    @Test
    fun `tablets draw the HUD 1_3 times larger from 600dp wide`() {
        assertEquals(1f, HudScale.forWidth(599))
        assertEquals(1.3f, HudScale.forWidth(600))
        assertFalse(HudScale.isTablet(1f))
        assertTrue(HudScale.isTablet(HudScale.forWidth(900)))
    }

    @Test
    fun `tool level text is white then purple then orange`() {
        assertEquals(Theme.textPrimary, Theme.toolLevel(0))
        assertEquals(Theme.textPrimary, Theme.toolLevel(1))
        assertEquals(Theme.xp, Theme.toolLevel(2))
        assertEquals(Theme.toolLevel3, Theme.toolLevel(3))
        assertEquals(Theme.toolLevel3, Theme.toolLevel(9))
    }

    @Test
    fun `palette matches the iOS values that existing screens used`() {
        assertEquals(0xFF87FF85.toInt(), Theme.accent.toArgb())
        assertEquals(0xFFA873FA.toInt(), Theme.xp.toArgb())
        assertEquals(0xFFFF5959.toInt(), Theme.spend.toArgb())
        assertEquals(0xFFFFCC33.toInt(), Theme.gold.toArgb())
        assertEquals(0xFF242424.toInt(), Theme.launchBackground.toArgb())
    }

    @Test
    fun `rarity colors come from the catalog`() {
        assertEquals(Color(0.2f, 0.5f, 1f), Rarity.rare.hudColor)
        assertEquals(Color(1f, 1f, 1f), Rarity.common.hudColor)
    }

    @Test
    fun `the pulse swells from nothing to full and back once every 1_6 seconds`() {
        assertEquals(0.0, Pulse.swell(0.0), 1e-9)
        assertEquals(0.5, Pulse.swell(0.4), 1e-9)
        assertEquals(1.0, Pulse.swell(0.8), 1e-9)
        assertEquals(0.0, Pulse.swell(1.6), 1e-9)
    }

    @Test
    fun `scroll bar appears only when the content is more than a point taller`() {
        assertFalse(ScrollMetrics(0f, 100f, 100f).overflows)
        assertFalse(ScrollMetrics(0f, 101f, 100f).overflows)
        assertTrue(ScrollMetrics(0f, 101.5f, 100f).overflows)
    }

    @Test
    fun `scroll bar is proportional, at least 36 long, and travels with the offset`() {
        val (length, top) = ScrollMetrics(0f, 400f, 200f).bar(36f)
        assertEquals(100f, length)
        assertEquals(0f, top)
        val (_, end) = ScrollMetrics(200f, 400f, 200f).bar(36f)
        assertEquals(100f, end)
        val (middle, _) = ScrollMetrics(0f, 20000f, 200f).bar(36f)
        assertEquals(36f, middle)
        assertEquals(50f, ScrollMetrics(100f, 400f, 200f).bar(36f).second)
    }

    @Test
    fun `quantity stays between one and the maximum`() {
        assertEquals(1, QuantityRange.fewer(1))
        assertEquals(4, QuantityRange.fewer(5))
        assertEquals(5, QuantityRange.more(5, 5))
        assertEquals(3, QuantityRange.more(2, 5))
        assertEquals(1, QuantityRange.max(0))
        assertEquals(12, QuantityRange.max(12))
        assertFalse(QuantityRange.canDecrease(1))
        assertTrue(QuantityRange.canIncrease(1, 2))
        assertFalse(QuantityRange.canIncrease(2, 2))

        assertEquals(1L, QuantityRange.fewer(1L))
        assertEquals(4L, QuantityRange.fewer(5L))
        assertEquals(5L, QuantityRange.more(5L, 5L))
        assertEquals(3L, QuantityRange.more(2L, 5L))
        assertEquals(1L, QuantityRange.max(0L))
        assertEquals(12L, QuantityRange.max(12L))
        assertFalse(QuantityRange.canDecrease(1L))
        assertTrue(QuantityRange.canIncrease(1L, 2L))
        assertFalse(QuantityRange.canIncrease(2L, 2L))
        assertFalse(QuantityRange.canIncrease(0L, 0L))
    }

    @Test
    fun `choice rows leave room for the info and upgrade buttons`() {
        assertEquals(14, ChoiceMetrics.trailingRoom(reservesInfoRoom = false, hasUpgrade = false))
        assertEquals(60, ChoiceMetrics.trailingRoom(reservesInfoRoom = true, hasUpgrade = false))
        assertEquals(144, ChoiceMetrics.trailingRoom(reservesInfoRoom = true, hasUpgrade = true))
        assertEquals(98, ChoiceMetrics.trailingRoom(reservesInfoRoom = false, hasUpgrade = true))
    }

    @Test
    fun `the selected choice scrolls to the middle but never above the top`() {
        assertEquals(0, ChoiceMetrics.centeredOffset(rowTop = 20, rowHeight = 40, viewport = 200))
        assertEquals(260, ChoiceMetrics.centeredOffset(rowTop = 300, rowHeight = 40, viewport = 120))
    }

    @Test
    fun `detail stats are green only while positive`() {
        assertEquals(Theme.accent, DetailStat("Owned", 3).color)
        assertEquals(Color.White, DetailStat("Owned", 0).color)
        assertEquals("1,200", DetailStat("Owned", 1200).display)
    }

    @Test
    fun `pill button colors match role specifications`() {
        assertEquals(Theme.accent, pillContainerColor(PillRole.earn))
        assertEquals(Theme.onAccent, pillContentColor(PillRole.earn))
        assertEquals(null, pillBorderStroke(PillRole.earn))

        assertEquals(Theme.accent, pillContainerColor(PillRole.accent))
        assertEquals(Theme.onAccent, pillContentColor(PillRole.accent))
        assertEquals(null, pillBorderStroke(PillRole.accent))

        assertEquals(Theme.danger, pillContainerColor(PillRole.destructive))
        assertEquals(Theme.onDanger, pillContentColor(PillRole.destructive))
        assertEquals(null, pillBorderStroke(PillRole.destructive))

        assertEquals(Theme.glassFill, pillContainerColor(PillRole.neutral))
        assertEquals(Theme.textPrimary, pillContentColor(PillRole.neutral))
        assertNotNull(pillBorderStroke(PillRole.neutral))
    }

    @Test
    fun `pill roles match HUD palette for fill and text`() {
        assertEquals(Theme.accent, PillRole.accent.fill)
        assertEquals(Theme.onAccent, PillRole.accent.text)
        assertEquals(Theme.danger, PillRole.destructive.fill)
        assertEquals(Theme.onDanger, PillRole.destructive.text)
        assertEquals(Color.White.copy(alpha = 0.16f), PillRole.neutral.fill)
        assertEquals(Theme.onNeutral, PillRole.neutral.text)
    }
}
