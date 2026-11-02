package com.hunter.stockfarmer.ui.hud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HudTopSpacingTest {
    @Test
    fun `a normal status bar gets the minimum gap below it`() {
        assertEquals(8f, HudTopSpacing.extraTopPaddingDp(24f, 1f), 0.001f)
        assertEquals(8f, HudTopSpacing.extraTopPaddingDp(48f, 1f), 0.001f)
    }

    @Test
    fun `a missing or zero inset still leaves the panels well below the edge`() {
        assertEquals(28f, HudTopSpacing.extraTopPaddingDp(0f, 1f), 0.001f)
        assertEquals(28f, HudTopSpacing.extraTopPaddingDp(-5f, 1f), 0.001f)
        assertEquals(12f, HudTopSpacing.extraTopPaddingDp(16f, 1f), 0.001f)
    }

    @Test
    fun `the gap grows with the hud scale but the inset does not`() {
        assertEquals(10.4f, HudTopSpacing.extraTopPaddingDp(48f, 1.3f), 0.001f)
        assertEquals(36.4f, HudTopSpacing.extraTopPaddingDp(0f, 1.3f), 0.001f)
        assertEquals(12.4f, HudTopSpacing.extraTopPaddingDp(24f, 1.3f), 0.001f)
    }

    @Test
    fun `the panels are never closer than the minimum from the edge or the inset`() {
        for (scale in listOf(1f, 1.3f)) {
            for (inset in listOf(0f, 8f, 24f, 28f, 36f, 48f, 80f)) {
                val extra = HudTopSpacing.extraTopPaddingDp(inset, scale)
                assertTrue(extra >= HudTopSpacing.MIN_GAP_DP * scale)
                assertTrue(inset + extra >= HudTopSpacing.MIN_FROM_EDGE_DP * scale - 0.001f)
            }
        }
    }
}
