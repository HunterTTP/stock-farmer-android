@file:Suppress("ktlint:standard:max-line-length")

package com.hunter.stockfarmer.core.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

class DayNightCycleTest {
    @Test fun `a real second is a minute so a day is twenty four real minutes`() {
        assertEquals(24.0 * 60, DayNightCycle.secondsPerDay)
        val start = Instant.ofEpochSecond(1_000_000 * 1440L)
        for ((seconds, minute) in listOf(0L to 0.0, 1L to 1.0, 61L to 61.0, 1439L to 1439.0, 1440L to 0.0)) {
            assertEquals(minute, DayNightCycle.minuteOfDay(start.plusSeconds(seconds), 0.0))
        }
    }

    @Test fun `night is fully dark at nine PM and half dark at five AM and daylight is clear of it`() {
        assertEquals(1.0, DayNightCycle.darkness(21.0 * 60))
        assertTrue(abs(DayNightCycle.darkness(20.5 * 60) - 0.5) < 0.001)
        assertTrue(abs(DayNightCycle.darkness(5.0 * 60) - 0.5) < 0.001)
        assertTrue((330 until 1200).all { DayNightCycle.darkness(it.toDouble()) == 0.0 })
        assertTrue((1260..1439 step 5).all { DayNightCycle.darkness(it.toDouble()) == 1.0 })
    }

    @Test fun `night falls over the hour before nine PM is full through the middle and lifts over an hour around five AM`() {
        val falling = (1200..1260).map { DayNightCycle.darkness(it.toDouble()) }
        assertTrue(falling.first() == 0.0 && falling.last() == 1.0 && falling == falling.sorted())
        assertTrue((0..270 step 10).all { DayNightCycle.darkness(it.toDouble()) == 1.0 })
        val lifting = (270..330).map { DayNightCycle.darkness(it.toDouble()) }
        assertTrue(lifting.first() == 1.0 && lifting.last() == 0.0 && lifting == lifting.sortedDescending())
        assertTrue((0 until 1440).all { DayNightCycle.darkness(it.toDouble()) in 0.0..1.0 })
    }

    @Test fun `setting the clock makes it read that time and carry on`() {
        val now = Instant.ofEpochSecond(1_234_567)
        for (minute in listOf(0.0, 300.0, 720.0, 1260.0, 1439.0)) {
            val offset = DayNightCycle.offset(minute, now)
            assertEquals(minute, DayNightCycle.minuteOfDay(now, offset), 0.0001)
            assertEquals((minute + 10) % 1440, DayNightCycle.minuteOfDay(now.plusSeconds(10), offset), 0.0001)
        }
    }

    @Test fun `the clock reads like a clock`() {
        for ((minute, text) in listOf(
            0.0 to "12:00 AM",
            5.0 to "12:05 AM",
            (9 * 60 + 7.9) to "9:07 AM",
            720.0 to "12:00 PM",
            1265.0 to "9:05 PM",
            1439.0 to "11:59 PM",
        )) {
            assertEquals(text, DayNightCycle.clockText(minute))
        }
    }
}
