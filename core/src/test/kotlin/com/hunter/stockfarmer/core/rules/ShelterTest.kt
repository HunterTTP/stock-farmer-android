package com.hunter.stockfarmer.core.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

class ShelterTest {
    @Test fun `it rains for three hours every day from two in the afternoon`() {
        assertEquals(0.0, Weather.rainAmount(13.0 * 60 + 59))
        assertEquals(1.0, Weather.rainAmount(14.0 * 60 + 30))
        assertEquals(1.0, Weather.rainAmount(16.0 * 60))
        assertEquals(0.0, Weather.rainAmount(17.0 * 60))
        assertEquals(180, (0 until 1440).count { Weather.rainAmount(it + 0.5) > 0 })
    }

    @Test fun `rain fades in and out instead of switching on`() {
        var previous = 0.0
        var biggestJump = 0.0
        for (step in 0 until 1440 * 4) {
            val amount = Weather.rainAmount(step.toDouble() / 4)
            biggestJump = max(biggestJump, abs(amount - previous))
            previous = amount
        }
        assertTrue(biggestJump <= 0.05)
    }

    @Test fun `it follows the farms clock and repeats every day`() {
        val start = Instant.ofEpochSecond(30_000_000)
        val afternoon = DayNightCycle.offset(14.5 * 60, start)
        assertEquals(1.0, Weather.rainAmount(start, afternoon))
        assertEquals(1.0, Weather.rainAmount(start.plusSeconds(1440), afternoon))
        assertEquals(0.0, Weather.rainAmount(start, DayNightCycle.offset(9.0 * 60, start)))
    }
}
