package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.hypot

class NightPulseTest {
    private fun midnightOf(day: Int) = Instant.ofEpochSecond((day * DayNightCycle.secondsPerDay).toLong())

    @Test
    fun `a day is counted from midnight on the farms clock`() {
        val midnight = midnightOf(1_000)
        assertEquals(1_000, NightPulse.day(midnight, 0.0))
        assertEquals(1_000, NightPulse.day(midnight.advancedBy(DayNightCycle.secondsPerDay - 1), 0.0))
        assertEquals(1_001, NightPulse.day(midnight.advancedBy(DayNightCycle.secondsPerDay), 0.0))
        assertEquals(999, NightPulse.day(midnight, -1.0))
    }

    @Test
    fun `the summary of the day starts coming in while the chime is ringing`() {
        assertTrue(NightPulse.SUMMARY_MINUTE > 0)
        assertTrue(NightPulse.SUMMARY_MINUTE < NightPulse.CHIME_MINUTES)
    }

    @Test
    fun `the wave rolls out at midnight and only then`() {
        assertEquals(0.0, NightPulse.progress(0.0))
        assertEquals(0.5, NightPulse.progress(NightPulse.LENGTH_MINUTES / 2) ?: 0.0)
        assertNull(NightPulse.progress(NightPulse.LENGTH_MINUTES))
        assertNull(NightPulse.progress(1_439.0))
        val during = (0 until 1_440).filter { NightPulse.progress(it.toDouble()) != null }
        assertEquals((0 until NightPulse.LENGTH_MINUTES.toInt()).toList(), during)
        assertTrue(during.all { DayNightCycle.darkness(it.toDouble()) == 1.0 })
    }

    @Test
    fun `the wave is brightest at its front and fades on both sides and at the end`() {
        val progress = 0.5
        val reach = 60.0
        val front = NightPulse.radius(progress, reach)

        fun intensity(
            distance: Double,
            at: Double = progress,
        ) = NightPulse.intensity(distance, at, reach)
        assertTrue(intensity(front) > 0.5 && intensity(front) <= 1)
        assertTrue(intensity(front + 1) < intensity(front) && intensity(front - 1) < intensity(front))
        assertTrue(intensity(front + NightPulse.BAND_WIDTH) == 0.0 && intensity(front - NightPulse.BAND_WIDTH) == 0.0)
        assertEquals(0.0, intensity(0.0, 0.0))
        val fadeIn =
            generateSequence(0.0) { it + 0.005 }
                .takeWhile { it <= NightPulse.FADE_IN_PROGRESS + 1e-9 }
                .map { intensity(NightPulse.radius(it, reach), it) }
                .toList()
        assertEquals(fadeIn.sorted(), fadeIn)
        assertTrue(fadeIn.last() > 0.9)
        assertTrue(fadeIn.zipWithNext().all { (a, b) -> b - a < 0.1 })
        assertTrue(intensity(NightPulse.radius(0.99, reach), 0.99) < 0.05)
    }

    @Test
    fun `the wave reaches the far corner of the map from wherever it starts`() {
        val size = GameState.GRID_SIZE.toDouble()
        for ((row, column) in listOf(0.0 to 0.0, 25.0 to 25.0, size to 3.0, 10.5 to 40.5)) {
            val reach = NightPulse.reach(row, column)
            val farthest =
                listOf(0.0 to 0.0, 0.0 to size, size to 0.0, size to size).maxOf {
                    hypot(
                        it.first - row,
                        it.second - column,
                    )
                }
            assertTrue(reach - NightPulse.BAND_WIDTH >= farthest - 0.0001)
        }
        assertTrue(NightPulse.reach(0.0, 0.0) > NightPulse.reach(25.0, 25.0))
    }

    @Test
    fun `the wave at a moment knows its night and progress`() {
        val midnight = midnightOf(1_001)
        val wave = NightPulse.wave(midnight.advancedBy(5.0), 0.0)
        assertEquals(1_001, wave?.day)
        assertTrue(abs((wave?.progress ?: 0.0) - 5 / NightPulse.LENGTH_MINUTES) < 0.0001)
        assertNull(NightPulse.wave(midnight.advancedBy(NightPulse.LENGTH_MINUTES), 0.0))
        assertNull(NightPulse.wave(midnight.advancedBy(-1.0), 0.0))
    }

    @Test
    fun `the chime starts with the wave and the music fades out as it does`() {
        assertTrue(NightPulse.isChimeDue(0.0) && NightPulse.isChimeDue(NightPulse.LENGTH_MINUTES - 0.1))
        assertFalse(NightPulse.isChimeDue(1_439.9))
        assertFalse(NightPulse.isChimeDue(NightPulse.LENGTH_MINUTES))
        assertTrue(NightPulse.isMusicHushed(0.0))
        assertFalse(NightPulse.isMusicHushed(1_439.9))
        assertFalse(NightPulse.isMusicHushed(720.0))
    }

    @Test
    fun `the music comes back when the chime has rung`() {
        assertTrue(NightPulse.isMusicHushed(NightPulse.CHIME_MINUTES - 0.1))
        assertFalse(NightPulse.isMusicHushed(NightPulse.CHIME_MINUTES))
    }
}
