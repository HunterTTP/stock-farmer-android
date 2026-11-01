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

    @Test fun `small amounts are written out`() {
        assertEquals("$25", 25.0.compactDollars)
        assertEquals("$999", 999.0.compactDollars)
    }

    @Test fun `thousands and millions drop their zeros`() {
        for ((amount, text) in listOf(
            14_000.0 to "$14K",
            16_000.0 to "$16K",
            1500.0 to "$1.5K",
            2_000_000.0 to "$2M",
            999_999.0 to "$999.9K",
        )) {
            assertEquals(text, amount.compactDollars)
        }
    }
}
