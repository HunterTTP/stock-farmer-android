package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.farm.effects.WaterDrops
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WaterAnimationTest {
    @Test
    fun `sideways fish turn vertically while remembering which side they face`() {
        assertEquals(FishOrientation(true, 0f), fishOrientation("carp", Facing.RIGHT, false))
        assertEquals(FishOrientation(false, 0f), fishOrientation("carp", Facing.LEFT, true))
        assertEquals(FishOrientation(true, 90f), fishOrientation("carp", Facing.UP, true))
        assertEquals(FishOrientation(false, -90f), fishOrientation("carp", Facing.UP, false))
        assertEquals(FishOrientation(false, 90f), fishOrientation("carp", Facing.DOWN, false))
    }

    @Test
    fun `up facing fish rotate instead of mirroring`() {
        for (kind in listOf("jellyfish", "lobster", "octopus", "squid")) {
            assertEquals(FishOrientation(false, 90f), fishOrientation(kind, Facing.LEFT, true))
            assertEquals(FishOrientation(false, -90f), fishOrientation(kind, Facing.RIGHT, true))
            assertEquals(FishOrientation(false, 180f), fishOrientation(kind, Facing.DOWN, true))
            assertEquals(FishOrientation(false, 0f), fishOrientation(kind, Facing.UP, true))
        }
    }

    @Test
    fun `fish turn over six tenths of a second along the shortest arc`() {
        assertEquals(170f, turnDegrees(170f, -170f, 0.0))
        assertEquals(180f, turnDegrees(170f, -170f, 0.3))
        assertEquals(190f, turnDegrees(170f, -170f, 0.6))
        assertEquals(190f, turnDegrees(170f, -170f, 3.0))
    }

    @Test
    fun `fish breathing follows the two scale durations`() {
        assertEquals(1f, fishBreathing(0.0))
        assertEquals(1.04f, fishBreathing(1.1), 0.000001f)
        assertEquals(0.97f, fishBreathing(2.4), 0.000001f)
        assertEquals(1.04f, fishBreathing(3.5), 0.000001f)
    }

    @Test
    fun `duck wake emits six pixels per interval and disappears after seven tenths`() {
        val wake = DuckWake(42).apply { halfWidth = 29.12f }
        assertTrue(wake.update(0.0, 0f, 0f, Facing.RIGHT, false).isEmpty())
        val first = wake.update(0.0, 0f, 0f, Facing.RIGHT, true)
        assertEquals(6, first.size)
        assertEquals(6, wake.update(0.06, 0f, 0f, Facing.RIGHT, true).size)
        val second = wake.update(0.08, 1f, 0f, Facing.RIGHT, true)
        assertEquals(12, second.size)
        assertTrue(second.first().alpha < first.first().alpha)
        assertTrue(wake.update(0.79, 1f, 0f, Facing.RIGHT, false).isEmpty())
    }

    @Test
    fun `seeded wakes repeat and clock resets discard old foam`() {
        val first = DuckWake(42).apply { halfWidth = 29.12f }
        val second = DuckWake(42).apply { halfWidth = 29.12f }
        assertEquals(first.update(10.0, 0f, 0f, Facing.UP, true), second.update(10.0, 0f, 0f, Facing.UP, true))
        assertFalse(first.update(10.1, 0f, 0f, Facing.UP, false).isEmpty())
        assertTrue(first.update(0.0, 0f, 0f, Facing.UP, false).isEmpty())
    }

    @Test
    fun `duck wake pixels are the size of a water drop in the foam and spray colors`() {
        val wake = DuckWake(7).apply { halfWidth = 29.12f }
        val pixels = (0..20).flatMap { wake.update(it * 0.07, 0f, 0f, Facing.LEFT, true) }
        assertTrue(pixels.all { it.size == WaterDrops.SIZE.toFloat() })
        assertTrue(pixels.all { it.tint in WaterDrops.wake.indices })
        assertEquals(WaterDrops.foam.size + WaterDrops.spray.size, WaterDrops.wake.size)
        assertEquals(WaterDrops.wake.size * 3, WaterDrops.wakeRgb.size)
        assertEquals(WaterDrops.foam[0].red / 255f, WaterDrops.wakeRgb[0])
        assertTrue(pixels.map { it.tint }.toSet().size > 3)
    }
}
