package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.ui.theme.QuantityRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StockTradeQuantityTest {
    @Test
    fun `no affordable shares keeps controls at one with increment disabled`() {
        for (maximum in listOf(0L, -1L)) {
            assertEquals(1L, QuantityRange.max(maximum))
            assertEquals(1L, QuantityRange.more(1L, maximum))
            assertFalse(QuantityRange.canIncrease(1L, maximum))
            assertFalse(QuantityRange.canDecrease(1L))
        }
    }

    @Test
    fun `share quantities above Int range retain precision and stop at maximum`() {
        val maximum = Int.MAX_VALUE.toLong() + 1000
        assertEquals(maximum, QuantityRange.max(maximum))
        assertEquals(maximum, QuantityRange.more(maximum - 1, maximum))
        assertEquals(maximum, QuantityRange.more(maximum, maximum))
        assertEquals(maximum - 1, QuantityRange.fewer(maximum))
        assertTrue(QuantityRange.canIncrease(maximum - 1, maximum))
        assertFalse(QuantityRange.canIncrease(maximum, maximum))
    }

    @Test
    fun `a reduced maximum bounds the next increment and max action`() {
        assertEquals(2L, QuantityRange.more(10L, 2L))
        assertEquals(2L, QuantityRange.max(2L))
        assertFalse(QuantityRange.canIncrease(10L, 2L))
        assertEquals(1L, QuantityRange.fewer(0L))
        assertEquals(1L, QuantityRange.fewer(1L))
    }
}
