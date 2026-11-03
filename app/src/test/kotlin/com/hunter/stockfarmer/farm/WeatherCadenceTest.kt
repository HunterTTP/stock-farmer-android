package com.hunter.stockfarmer.farm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeatherCadenceTest {
    private val cadence = WeatherCadence()

    @Test
    fun `the first frame samples and never sleeps first`() {
        assertFalse(cadence.canSleep(showing = false, amount = 0.0))
        assertTrue(cadence.advance(0.0))
    }

    @Test
    fun `a dry sky sleeps between samples and the frame after waking samples again`() {
        cadence.advance(0.016)
        assertTrue(cadence.canSleep(showing = false, amount = 0.0))
        cadence.slept()
        assertFalse(cadence.canSleep(showing = false, amount = 0.0))
        assertTrue(cadence.advance(0.0))
        assertTrue(cadence.canSleep(showing = false, amount = 0.0))
    }

    @Test
    fun `rain or leftover drops keep it waking every frame`() {
        cadence.advance(0.016)
        assertFalse(cadence.canSleep(showing = false, amount = 0.2))
        assertFalse(cadence.canSleep(showing = true, amount = 0.0))
    }

    @Test
    fun `while awake it samples four times a second at 60 frames a second`() {
        var samples = 0
        repeat(600) { if (cadence.advance(1.0 / 60)) samples++ }
        assertEquals(10.0 * 4, samples.toDouble(), 2.0)
    }
}
