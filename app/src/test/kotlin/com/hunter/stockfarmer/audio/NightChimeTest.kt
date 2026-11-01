package com.hunter.stockfarmer.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NightChimeTest {
    @Test
    fun `retry before any night begins never rings`() {
        var attempts = 0
        val chime =
            NightChime {
                attempts++
                true
            }
        chime.retry(0.0)
        chime.begin(null)
        chime.retry(9.999)
        assertEquals(0, attempts)
        assertFalse(chime.hushes(0))
    }

    @Test
    fun `hush boundary abandons pending sound exactly at ten minutes`() {
        var attempts = 0
        val chime =
            NightChime {
                attempts++
                false
            }
        chime.begin(7)
        chime.retry(9.999)
        assertEquals(2, attempts)
        chime.retry(10.0)
        chime.retry(0.0)
        assertEquals(2, attempts)
        assertTrue(chime.hushes(7))
    }

    @Test
    fun `a new night replaces the previous hush and receives its own retry budget`() {
        var attempts = 0
        val chime = NightChime { ++attempts == 3 }
        chime.begin(7)
        chime.retry(10.0)
        chime.begin(8)
        assertFalse(chime.hushes(7))
        assertTrue(chime.hushes(8))
        chime.retry(0.0)
        chime.retry(1.0)
        assertEquals(3, attempts)
    }

    @Test fun `ending the night abandons retries and hush until the next day begins`() {
        var attempts = 0
        val chime =
            NightChime {
                attempts++
                false
            }
        chime.begin(7)
        chime.begin(null)
        assertFalse(chime.hushes(7))
        chime.retry(2.0)
        assertEquals(1, attempts)
        chime.begin(7)
        assertTrue(chime.hushes(7))
        assertEquals(2, attempts)
    }

    @Test fun `a chime that starts is not tried again`() {
        var rung = 0
        val chime = NightChime { rung++ >= 0 }
        chime.begin(7)
        chime.retry(1.0)
        assertEquals(1, rung)
    }

    @Test fun `a dropped chime is tried again while the music is hushed and then given up`() {
        var attempts = 0
        val chime = NightChime { ++attempts == 3 }
        chime.begin(7)
        assertEquals(1, attempts)
        chime.retry(2.0)
        assertEquals(2, attempts)
        chime.retry(3.0)
        assertEquals(3, attempts)
        chime.retry(4.0)
        assertEquals(3, attempts)
    }

    @Test fun `a dropped chime is abandoned once the hush is over`() {
        var attempts = 0
        val chime =
            NightChime {
                attempts++
                false
            }
        chime.begin(7)
        chime.retry(11.0)
        chime.retry(1.0)
        assertEquals(1, attempts)
    }

    @Test fun `only the night this session rang hushes the music`() {
        val chime = NightChime { true }
        assertFalse(chime.hushes(7))
        chime.begin(7)
        assertTrue(chime.hushes(7))
        assertFalse(chime.hushes(8))
    }
}
