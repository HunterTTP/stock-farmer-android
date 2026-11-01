package com.hunter.stockfarmer.ui.hud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToolRingLayoutTest {
    @Test
    fun `tool ring layout defines 6 tools and 3 system satellites in clockwise arc`() {
        assertEquals(9, ToolRingLayout.slots.size)

        val toolSlots = ToolRingLayout.slots.filter { it.satellite is Satellite.OfTool }
        assertEquals(6, toolSlots.size)
        assertEquals(listOf(90.0, 72.0, 54.0, 36.0, 18.0, 0.0), toolSlots.map { it.angle })
        toolSlots.forEach {
            assertEquals(ToolRingLayout.TOOL_RADIUS, it.radius)
            assertEquals(ToolRingLayout.TOOL_SIZE, it.size)
        }

        val otherSlots = ToolRingLayout.slots.filter { it.satellite !is Satellite.OfTool }
        assertEquals(listOf(Satellite.Storage, Satellite.Settings, Satellite.Time), otherSlots.map { it.satellite })
        assertEquals(listOf(80.0, 45.0, 10.0), otherSlots.map { it.angle })
        otherSlots.forEach {
            assertEquals(ToolRingLayout.OTHER_RADIUS, it.radius)
            assertEquals(ToolRingLayout.OTHER_SIZE, it.size)
        }
    }

    @Test
    fun `span and inset match iOS geometric proportions`() {
        assertEquals(290f, ToolRingLayout.span(1f))
        assertEquals(34f, ToolRingLayout.inset(1f))

        assertEquals(290f * 1.3f, ToolRingLayout.span(1.3f), 1e-4f)
        assertEquals(34f * 1.3f, ToolRingLayout.inset(1.3f), 1e-4f)
    }

    @Test
    fun `button center spots place 90 degrees straight up and 0 degrees straight right`() {
        val scale = 1f
        val inset = ToolRingLayout.inset(scale)
        val span = ToolRingLayout.span(scale)
        val radius = ToolRingLayout.TOOL_RADIUS

        val topSlot = ToolRingLayout.slots.first { it.angle == 90.0 }
        val (topX, topY) = ToolRingLayout.spot(topSlot, scale)
        assertEquals(inset, topX, 1e-4f)
        assertEquals(span - inset - radius, topY, 1e-4f)

        val rightSlot = ToolRingLayout.slots.first { it.angle == 0.0 }
        val (rightX, rightY) = ToolRingLayout.spot(rightSlot, scale)
        assertEquals(inset + radius, rightX, 1e-4f)
        assertEquals(span - inset, rightY, 1e-4f)
    }
}
