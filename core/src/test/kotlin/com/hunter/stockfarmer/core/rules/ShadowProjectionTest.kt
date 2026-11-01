package com.hunter.stockfarmer.core.rules

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ShadowProjectionTest {
    private val slant = 0.55

    @Test fun `the picture leans back along the shadow on the flattened ground`() {
        for ((dx, dy) in listOf(0.0 to 1.0, 0.6 to 0.8, -0.82 to 0.57, 0.3 to 0.95, -0.5 to 0.87)) {
            val projection = ShadowProjection(dx to dy, 0.8, slant)
            for ((across, up) in listOf(0.0 to 0.0, 1.0 to 0.0, 0.0 to 1.0, -2.0 to 3.0, 5.0 to 7.0)) {
                val (x, y) = projection.place(across, up)
                assertTrue(abs(x - (across + up * 0.8 * dx)) < 1e-9)
                assertTrue(abs(y - up * 0.8 * dy * slant) < 1e-9)
            }
        }
    }

    @Test fun `at midday the shadow is the picture upright and squashed behind it with its width kept`() {
        val projection = ShadowProjection(0.0 to 1.0, 0.5, slant)
        val top = projection.place(0.0, 10.0)
        val side = projection.place(4.0, 0.0)
        assertTrue(abs(top.first) < 1e-9 && abs(top.second - 10 * 0.5 * slant) < 1e-9)
        assertTrue(abs(side.first - 4) < 1e-9 && abs(side.second) < 1e-9)
    }

    @Test fun `with the sun to one side the shadow leans over without turning over`() {
        val projection = ShadowProjection(-0.8 to 0.6, 1.0, slant)
        val top = projection.place(0.0, 10.0)
        assertTrue(top.first < -7.9 && top.second > 0)
        assertTrue(projection.leanX < 0 && projection.leanY > 0)
    }
}
