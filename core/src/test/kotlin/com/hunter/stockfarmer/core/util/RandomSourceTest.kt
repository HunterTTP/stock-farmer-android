package com.hunter.stockfarmer.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RandomSourceTest {
    @Test
    fun `a seed gives the same numbers every time`() {
        assertEquals(SeededRandom(1).nextLong(), SeededRandom(1).nextLong())
    }

    @Test
    fun `bounded integers stay in range and cover it`() {
        val random = SeededRandom(7)
        val drawn = List(2_000) { random.nextInt(3..6) }
        assertTrue(drawn.all { it in 3..6 })
        assertEquals(setOf(3, 4, 5, 6), drawn.toSet())
    }

    @Test
    fun `unit doubles stay below one`() {
        val random = SeededRandom(3)
        assertTrue(List(2_000) { random.nextUnit() }.all { it >= 0.0 && it < 1.0 })
    }

    @Test
    fun `shuffling keeps every element`() {
        assertEquals((1..10).toList(), (1..10).toList().shuffled(SeededRandom(5)).sorted())
    }
}
