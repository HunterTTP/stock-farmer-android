package com.hunter.stockfarmer.farm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VisualTimingTest {
    @Test
    fun `grid stays visible through sixty percent zoom then fades to zero`() {
        assertEquals(1f, VisualTiming.gridAlpha(1f, 10f))
        assertEquals(1f, VisualTiming.gridAlpha(6f, 10f))
        assertEquals(0.5f, VisualTiming.gridAlpha(8f, 10f))
        assertEquals(0f, VisualTiming.gridAlpha(10f, 10f))
    }

    @Test
    fun `tool flash holds for 350ms and fades over the next 350ms`() {
        assertEquals(1f, VisualTiming.previewAlpha(0.35))
        assertEquals(0.5f, VisualTiming.previewAlpha(0.525), 0.00001f)
        assertEquals(0f, VisualTiming.previewAlpha(0.7))
    }

    @Test
    fun `new growth fades for 1 point 4 seconds with a seven second glow`() {
        assertEquals(0f, VisualTiming.growthAlpha(0.0))
        assertEquals(0.5f, VisualTiming.growthAlpha(0.7))
        assertEquals(1f, VisualTiming.growthAlpha(1.4))
        assertEquals(0f, VisualTiming.growthGlow(0.0))
        assertEquals(0.5f, VisualTiming.growthGlow(0.6))
        assertEquals(0.5f, VisualTiming.growthGlow(4.6))
        assertTrue(VisualTiming.growthGlow(5.8) in 0f..0.5f)
        assertEquals(0f, VisualTiming.growthGlow(7.0))
    }

    @Test
    fun `produce floats six units up and back every 1 point 2 seconds`() {
        assertEquals(0f, VisualTiming.bob(0.0))
        assertEquals(6f, VisualTiming.bob(0.6))
        assertEquals(3f, VisualTiming.bob(0.9))
        assertEquals(0f, VisualTiming.bob(1.2))
    }

    @Test
    fun `cobblestone has dark mortar clipped corners staggered stones and grain`() {
        assertEquals(0.25, CobblestonePath.shade(0, 0))
        assertEquals(0.25, CobblestonePath.shade(1, 1))
        assertEquals(0.45, CobblestonePath.shade(5, 5))
        assertEquals(0.45, CobblestonePath.shade(5, 1))
        assertEquals(0.57, CobblestonePath.shade(5, 2), 1e-9)
        assertEquals(0.52, CobblestonePath.shade(12, 17), 1e-9)
        assertTrue(
            (0 until 48).all { x ->
                (0 until 48).all { y ->
                    CobblestonePath.pixel(x, y, 0.5) and 255 == 255
                }
            },
        )
        assertTrue(CobblestonePath.pixel(5, 5, 1.0) ushr 24 > CobblestonePath.pixel(5, 5, 0.0) ushr 24)
    }
}
