package com.hunter.stockfarmer.core.feedback

import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RandomMomentTriggerTest {
    @Test fun `fires once in every period at a changing moment`() {
        var trigger = RandomMomentTrigger(120.0)
        val generator = SeededRandom(5)
        val firedAt =
            (0 until 120 * 50).filter {
                val (next, due) = trigger.isDue(it.toDouble(), generator)
                trigger = next
                due
            }
        assertTrue(firedAt.size in 49..50)
        assertEquals(firedAt.size, firedAt.map { it / 120 }.toSet().size)
        assertTrue(firedAt.map { it % 120 }.toSet().size > 20)
    }

    @Test fun `checking often or rarely does not change the count`() {
        var trigger = RandomMomentTrigger(120.0)
        val generator = SeededRandom(6)
        val fired =
            (0 until 120 * 30 step 7).count {
                val (next, due) = trigger.isDue(it.toDouble(), generator)
                trigger = next
                due
            }
        assertTrue(fired in 28..30)
    }
}
