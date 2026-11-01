package com.hunter.stockfarmer.core.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoundFalloffTest {
    private fun gain(
        zoom: Double,
        x: Double = 0.0,
        y: Double = 0.0,
        width: Double = 390.0,
        height: Double = 844.0,
    ) = SoundFalloff.gain(zoom, x, y, width, height)

    @Test fun `the whole screen is fully loud while the camera is zoomed well in`() {
        for (zoom in listOf(-1.0, 0.0, 0.1, 0.2)) {
            for ((width, height) in listOf(390.0 to 844.0, 1024.0 to 1366.0)) {
                for (x in listOf(-width / 2, 0.0, width / 2)) {
                    for (y in listOf(-height / 2, 0.0, height / 2)) {
                        assertEquals(1.0, gain(zoom, x, y, width, height))
                    }
                }
            }
        }
    }

    @Test fun `zooming further out fades smoothly from the fifth of the range`() {
        assertTrue(gain(0.20001) < 1)
        assertTrue(gain(0.20001) > 0.999)
        assertTrue(gain(0.4) > gain(0.7))
        assertTrue(gain(0.7) > gain(1.0))
        assertEquals(0.4, gain(1.0))
    }

    @Test fun `distance starts quieting a sound only after it leaves the screen`() {
        assertEquals(1.0, gain(0.2, x = 195.0))
        assertTrue(gain(0.2, x = 195.01) > 0.999)
        assertTrue(gain(0.2, x = 195.0 + 844) < gain(0.2, x = 195.0 + 422))
        assertTrue(gain(0.2, x = 195.0 + 844 * 2.5) < gain(0.2, x = 195.0 + 844))
    }

    @Test fun `equal distances past any screen edge have the same volume`() {
        for (distance in listOf(1.0, 422.0, 844.0, 2532.0)) {
            val right = gain(0.2, x = 195 + distance)
            assertEquals(right, gain(0.2, x = -195 - distance))
            assertEquals(right, gain(0.2, y = 422 + distance))
            assertEquals(right, gain(0.2, y = -422 - distance))
        }
    }

    @Test fun `offscreen distance scales with the visible farm`() {
        assertEquals(gain(0.2, x = 195.0 + 844), gain(0.2, x = 390.0 + 1688, width = 780.0, height = 1688.0))
    }

    @Test fun `gain stays between silence and full`() {
        for (zoom in listOf(-1.0, 0.0, 0.5, 1.0, 2.0)) {
            for (offset in listOf(-100_000.0, -1000.0, 0.0, 1000.0, 100_000.0)) {
                assertTrue(gain(zoom, offset, offset) in 0.0..1.0)
            }
        }
    }

    @Test fun `far off the screen is quiet but never silent`() {
        assertEquals(0.1, gain(0.0, x = 195 + 844 * 2.5))
        assertEquals(0.1, gain(1.0, x = 10_000.0))
    }
}
