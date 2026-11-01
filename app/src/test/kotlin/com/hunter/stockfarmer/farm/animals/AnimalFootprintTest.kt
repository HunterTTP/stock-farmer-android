package com.hunter.stockfarmer.farm.animals

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class AnimalFootprintTest {
    @Test
    fun `transparent padding is excluded and y is measured from the bottom`() {
        val footprint = AnimalFootprint.measure(8, 8) { x, y -> if (x in 2..5 && y in 1..6) 255 else 0 }!!
        assertEquals(0.5f, footprint.centerX)
        assertEquals(0.125f, footprint.bottom)
        assertEquals(0.5f, footprint.width)
        assertEquals(0.5f, footprint.bodyCenterX)
        assertEquals(0.875f, footprint.bodyTop)
        assertEquals(0.5f to 0.5f, footprint.center(null, false))
    }

    @Test
    fun `alpha threshold ignores faint pixels and empty frames have no footprint`() {
        assertNull(AnimalFootprint.measure(4, 4) { _, _ -> 40 })
        val footprint = AnimalFootprint.measure(4, 4) { x, y -> if (x == 0 && y == 0) 41 else 40 }!!
        assertEquals(0.25f, footprint.width)
        assertEquals(0.125f, footprint.bodyCenterX)
    }

    @Test
    fun `water hides feet when centering the duck while its rear tail stays visible`() {
        val footprint = AnimalFootprint(0.5f, 0.05f, 0.5f, 0.6f, 0.8f)
        assertEquals(0.6f to 0.525f, footprint.center(0.75f, false))
        assertEquals(0.6f to 0.425f, footprint.center(0.77f, true))
    }
}
