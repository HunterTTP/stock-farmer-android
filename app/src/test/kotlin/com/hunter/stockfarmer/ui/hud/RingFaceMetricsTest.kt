package com.hunter.stockfarmer.ui.hud

import com.hunter.stockfarmer.ui.theme.HudScale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class RingFaceMetricsTest {
    private val widths = listOf(320, 360, 411, 480, 599, 600, 800, 1280)
    private val fontScales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 2f, 3f)

    private fun diameter(widthDp: Int) = ToolRingLayout.TOOL_SIZE * HudScale.forWidth(widthDp)

    @Test
    fun `a phone at the default font scale keeps the original sizes`() {
        val metrics = RingFaceMetrics.of(68f, 1f)
        assertEquals(36f, metrics.iconDp, 0.001f)
        assertEquals(30f, metrics.glyphDp, 0.001f)
        assertEquals(12f, metrics.maxFontSp, 0.001f)
        assertEquals(7.2f, metrics.minFontSp, 0.001f)
        assertEquals(1f, metrics.gapDp, 0.001f)
    }

    @Test
    fun `sizes are fractions of the diameter at every screen width`() {
        for (width in widths) {
            val d = diameter(width)
            val metrics = RingFaceMetrics.of(d, 1f)
            assertEquals(36f / 68f, metrics.iconDp / d, 0.0001f)
            assertEquals(12f / 68f, metrics.fontDp / d, 0.0001f)
        }
    }

    @Test
    fun `the type measures the same in dp whatever the system font scale`() {
        for (width in widths) {
            for (fontScale in fontScales) {
                val metrics = RingFaceMetrics.of(diameter(width), fontScale)
                assertEquals(metrics.fontDp, metrics.maxFontSp * fontScale, 0.001f)
                assertEquals(metrics.fontDp * 0.6f, metrics.minFontSp * fontScale, 0.001f)
            }
        }
    }

    @Test
    fun `picture, gap and a line of text fit in the circle and the name fits its row`() {
        for (width in widths) {
            for (fontScale in fontScales) {
                val d = diameter(width)
                val metrics = RingFaceMetrics.of(d, fontScale)
                val contentHeight = metrics.iconDp + metrics.gapDp + metrics.fontDp * RingFaceMetrics.LINE_HEIGHT_EM
                val half = contentHeight / 2
                assertTrue(contentHeight < d * 0.85f)
                // The lower corners of the name's row sit inside the circle.
                val corner = sqrt(half * half + (metrics.textWidthDp / 2) * (metrics.textWidthDp / 2))
                assertTrue(corner <= d / 2 + 0.001f, "width $width font $fontScale")
                assertTrue(metrics.glyphDp <= metrics.iconDp && metrics.iconDp <= d)
            }
        }
    }
}
