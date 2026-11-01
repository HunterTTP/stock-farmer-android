package com.hunter.stockfarmer.core.feedback

import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoiceLimiterTest {
    private class Harness(
        voices: Int = 3,
        cooldown: Double = 0.1,
        copies: Int = 2,
    ) {
        var limiter = VoiceLimiter<String>(voices, cooldown, copies)

        fun request(
            effect: String,
            duration: Double,
            at: Double,
        ): Int? {
            val (next, voice) = limiter.request(effect, duration, at)
            limiter = next
            return voice
        }
    }

    @Test fun `a sound takes a free voice`() {
        val limiter = Harness()
        assertEquals(0, limiter.request("plant", 1.0, 0.0))
        assertEquals(1, limiter.request("sell", 1.0, 0.0))
    }

    @Test fun `the same sound again within the cooldown is dropped`() {
        val limiter = Harness()
        assertNotNull(limiter.request("plant", 1.0, 0.0))
        assertNull(limiter.request("plant", 1.0, 0.05))
        assertNotNull(limiter.request("plant", 1.0, 0.1))
    }

    @Test fun `a dropped sound does not restart the cooldown`() {
        val limiter = Harness()
        limiter.request("plant", 1.0, 0.0)
        limiter.request("plant", 1.0, 0.09)
        assertNotNull(limiter.request("plant", 1.0, 0.1))
    }

    @Test fun `no more copies of one sound than allowed play at once`() {
        val limiter = Harness(4, 0.0, 2)
        assertNotNull(limiter.request("plant", 1.0, 0.0))
        assertNotNull(limiter.request("plant", 1.0, 0.2))
        assertNull(limiter.request("plant", 1.0, 0.4))
        assertNotNull(limiter.request("plant", 1.0, 1.0))
    }

    @Test fun `a voice is free once its sound has ended`() {
        val limiter = Harness(1, 0.0, 5)
        assertEquals(0, limiter.request("a", 0.5, 0.0))
        assertNull(limiter.request("b", 0.5, 0.4))
        assertEquals(0, limiter.request("b", 0.5, 0.5))
    }

    @Test fun `a sound never cuts off one that is still playing`() {
        val limiter = Harness(2, 0.0, 5)
        limiter.request("sell", 5.0, 0.0)
        limiter.request("denied", 5.0, 0.1)
        assertNull(limiter.request("plant", 1.0, 0.2))
        assertEquals(0, limiter.request("plant", 1.0, 5.0))
    }

    @Test fun `a burst of one harvest sound never uses more than its share of voices`() {
        val limiter = Harness(16, 0.03, 6)
        val played = (0 until 100).count { limiter.request("harvest", 0.6, it * 0.01) != null }
        assertTrue(played <= 6 * 2)
        assertTrue(played >= 6)
        val steady = Harness().also { it.limiter = limiter.limiter }
        val heard = (0 until 20).count { steady.request("harvest", 0.3, 10 + it * 0.05) != null }
        assertTrue(heard >= 18)
    }

    @Test fun `resetting frees every voice and forgets the cooldowns`() {
        val limiter = Harness(1)
        assertEquals(0, limiter.request("plant", 10.0, 0.0))
        assertNull(limiter.request("sell", 10.0, 0.01))
        limiter.limiter = limiter.limiter.reset()
        assertEquals(0, limiter.request("plant", 10.0, 0.02))
    }

    @Test fun `never the same variant twice in a row when there is a choice`() {
        var picker = VariantPicker<String>()
        val generator = SeededRandom(7)
        var previous: Int? = null
        repeat(500) {
            val (next, choice) = picker.pick("plant", 3, generator)
            picker = next
            assertTrue(choice in 0 until 3)
            assertNotEquals(previous, choice)
            previous = choice
        }
    }

    @Test fun `every variant gets used`() {
        var picker = VariantPicker<String>()
        val generator = SeededRandom(11)
        val used =
            (0 until 200)
                .map {
                    val (next, choice) = picker.pick("plant", 4, generator)
                    picker = next
                    choice
                }.toSet()
        assertEquals(setOf(0, 1, 2, 3), used)
    }

    @Test fun `a sound with one variant always plays it`() {
        var picker = VariantPicker<String>()
        val generator = SeededRandom(3)
        repeat(5) {
            val (next, choice) = picker.pick("denied", 1, generator)
            picker = next
            assertEquals(0, choice)
        }
    }

    @Test fun `each sound remembers its own last variant`() {
        val generator = SeededRandom(5)
        val (firstPicker, first) = VariantPicker<String>().pick("a", 2, generator)
        val (secondPicker, _) = firstPicker.pick("b", 2, generator)
        assertNotEquals(first, secondPicker.pick("a", 2, generator).second)
    }
}
