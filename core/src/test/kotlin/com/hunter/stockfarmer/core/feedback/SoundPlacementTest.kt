package com.hunter.stockfarmer.core.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SoundPlacementTest {
    private fun placement(
        zoom: Double = 0.0,
        x: Double = 0.0,
        y: Double = 0.0,
    ) = SoundPlacement(zoom, x, y, 400.0, 800.0)

    @Test fun `a sound with no place is heard in the middle at full volume`() {
        assertEquals(SoundPlacement(1.0, 0.0, true), SoundPlacement.centered)
        assertEquals(SoundPlacement.centered, placement())
    }

    @Test fun `a sound to the right is panned right and one to the left left`() {
        assertTrue(placement(x = 100.0).pan > 0)
        assertTrue(placement(x = -100.0).pan < 0)
    }

    @Test fun `the pan is gentle and never beyond its limit`() {
        val far = placement(x = 5000.0)
        assertEquals(placement(x = 200.0).pan, far.pan)
        assertTrue(abs(far.pan) < 1)
    }

    @Test fun `a sound is close up only when it is in view with the camera well in`() {
        assertTrue(placement(0.2, 200.0, 400.0).isCloseUp)
        assertFalse(placement(0.5).isCloseUp)
        assertFalse(placement(0.2, 900.0).isCloseUp)
    }
}
