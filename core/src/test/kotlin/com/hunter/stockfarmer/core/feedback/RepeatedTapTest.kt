package com.hunter.stockfarmer.core.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class RepeatedTapTest {
    private val start = Instant.ofEpochSecond(1_800_000_000)

    private fun at(seconds: Double) = start.plusMillis((seconds * 1000).toLong())

    @Test fun `the required taps in a row complete`() {
        val taps = RepeatedTap(required = 7, window = Duration.ofSeconds(2))
        val results = (0 until 7).map { taps.register(at(it * 0.5)) }
        assertEquals(listOf(false, false, false, false, false, false, true), results)
    }

    @Test fun `a pause starts the count again`() {
        val taps = RepeatedTap(required = 3, window = Duration.ofSeconds(2))
        taps.register(at(0.0))
        taps.register(at(1.0))
        assertFalse(taps.register(at(4.0)))
        assertFalse(taps.register(at(5.0)))
        assertTrue(taps.register(at(6.0)))
    }

    @Test fun `completing starts a fresh count`() {
        val taps = RepeatedTap(required = 2, window = Duration.ofSeconds(2))
        taps.register(at(0.0))
        assertTrue(taps.register(at(1.0)))
        assertFalse(taps.register(at(2.0)))
        assertTrue(taps.register(at(3.0)))
    }
}
