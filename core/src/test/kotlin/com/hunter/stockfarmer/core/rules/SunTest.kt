package com.hunter.stockfarmer.core.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SunTest {
    @Test fun `at midday the shadow falls straight behind and is shortest and strongest`() {
        val noon = Sun.shadow(12.0 * 60)
        assertTrue(abs(noon.dx) < 0.001 && noon.dy > 0.999)
        for (other in listOf(7.0, 9.0, 15.0, 18.0)) {
            val later = Sun.shadow(other * 60)
            assertTrue(noon.length < later.length && noon.strength > later.strength)
        }
    }

    @Test fun `the shadow falls away from the sun to the left in the morning and the right in the evening`() {
        assertTrue(Sun.shadow(8.0 * 60).dx < -0.7)
        assertTrue(Sun.shadow(10.0 * 60).dx < -0.55)
        assertTrue(Sun.shadow(14.0 * 60).dx > 0.55)
        assertTrue(Sun.shadow(17.5 * 60).dx > 0.7)
    }

    @Test fun `at midday the sun is low in front so the shadow still reaches well back`() {
        assertTrue(Sun.shadow(12.0 * 60).length >= 1.2)
    }

    @Test fun `the shadow always falls behind what casts it so it never lies flat on its side`() {
        for (minute in 300..1140 step 10) assertTrue(Sun.shadow(minute.toDouble()).dy > 0.55)
    }

    @Test fun `the shadow swings smoothly from one side to the other`() {
        val xs = (360..1080 step 10).map { Sun.shadow(it.toDouble()).dx }
        assertTrue(xs.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(xs.zipWithNext().all { (a, b) -> abs(a - b) < 0.3 })
    }

    @Test fun `shadows are gone at night and fade as it falls and returns at dawn`() {
        assertTrue(listOf(21.0, 23.0, 0.0, 2.0, 4.0).all { Sun.shadow(it * 60).strength == 0.0 })
        assertTrue(Sun.shadow(20.5 * 60).strength < Sun.shadow(19.9 * 60).strength)
        assertTrue(Sun.shadow(7.0 * 60).strength > 0)
    }

    @Test fun `the sun moves in ten minute steps`() {
        assertEquals(0, Sun.step(0.0))
        assertEquals(0, Sun.step(9.99))
        assertEquals(1, Sun.step(10.0))
        assertEquals(143, Sun.step(24 * 60 - 0.01))
    }
}
