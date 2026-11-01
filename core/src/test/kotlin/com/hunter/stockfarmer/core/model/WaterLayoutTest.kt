package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.catalog.WaterStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaterLayoutTest {
    @Test fun `bodies connect on four sides and interiors need all eight neighbors`() {
        val pond = GridPoint(0, 0).rectangle(3, 3)
        val separate = GridPoint(3, 3)
        val layout = WaterLayout((pond.reversed() + separate).associateWith { WaterStyle.deep })
        assertEquals(2, layout.bodies.size)
        assertEquals(pond, layout.bodies[0].tiles)
        assertEquals(listOf(GridPoint(1, 1)), layout.bodies[0].interior)
        assertEquals(setOf(GridPoint(1, 1)), layout.interior)
        assertEquals(listOf(separate), layout.bodies[1].tiles)
        assertEquals(1, layout.bodyIndexByTile[separate])
        assertTrue(WaterLayout(emptyMap()).bodies.isEmpty())
    }

    @Test fun `the cache compares water values and snapshots mutable inputs`() {
        val cache = WaterLayoutCache()
        val water = mutableMapOf(GridPoint(0, 0) to WaterStyle.deep)
        val first = cache.layout(water)
        assertSame(first, cache.layout(water.toMap()))
        water[GridPoint(0, 1)] = WaterStyle.shallow
        val second = cache.layout(water)
        assertNotSame(first, second)
        assertEquals(1, first.bodies[0].tiles.size)
        assertEquals(2, second.bodies[0].tiles.size)
        assertEquals(cache, WaterLayoutCache())
    }
}
