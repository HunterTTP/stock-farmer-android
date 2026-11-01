package com.hunter.stockfarmer.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MoneyTest {
    @Test
    fun `rounds to cents with halves away from zero`() {
        assertEquals(0.13, 0.125.roundedToCents, 0.0)
        assertEquals(-0.13, (-0.125).roundedToCents, 0.0)
    }

    @Test
    fun `formats whole dollars and cents`() {
        assertEquals("$25", 25.0.dollars)
        assertEquals("$1,000", 1000.0.dollars)
        assertEquals("$0.50", 0.5.dollars)
    }

    @Test
    fun `formats compact dollars`() {
        assertEquals("$25", 25.0.compactDollars)
        assertEquals("$14K", 14_000.0.compactDollars)
        assertEquals("$1.5K", 1_500.0.compactDollars)
        assertEquals("$2M", 2_000_000.0.compactDollars)
    }
}
