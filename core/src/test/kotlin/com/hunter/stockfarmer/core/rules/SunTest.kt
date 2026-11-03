package com.hunter.stockfarmer.core.rules

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SunTest {
    @Test fun `at midday the shadow falls straight behind and is shortest`() {
        val noon = Sun.shadow(12.0 * 60)
        assertTrue(abs(noon.dx) < 0.001)
        for (other in listOf(7.0, 9.0, 15.0, 18.0)) assertTrue(noon.length < Sun.shadow(other * 60).length)
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

    @Test fun `the shadow swings smoothly from one side to the other`() {
        val xs = (360..1080 step 10).map { Sun.shadow(it.toDouble()).dx }
        assertTrue(xs.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(xs.zipWithNext().all { (a, b) -> abs(a - b) < 0.3 })
    }
}
