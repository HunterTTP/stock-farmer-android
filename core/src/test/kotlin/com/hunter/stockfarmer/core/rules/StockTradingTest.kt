package com.hunter.stockfarmer.core.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StockTradingTest {
    @Test fun `jitter stays between the quote and point zero five percent above it`() {
        val seen = mutableSetOf<Long>()
        for (step in 0L until 2_000L) {
            val price = PriceJitter.priceCents("AAPL", 20_000, step)
            assertTrue(price in 20_000L..20_010L)
            assertEquals(price, PriceJitter.priceCents("AAPL", 20_000, step))
            seen += price
        }
        assertTrue(seen.size > 5)
    }

    @Test fun `jitter respects the percentage limit at cent boundaries`() {
        for (baseCents in listOf(1L, 100L, 1999L, 2000L, 3999L, 19999L)) {
            for (step in 0L until 200L) {
                val price = PriceJitter.priceCents("X", baseCents, step)
                assertTrue(price >= baseCents)
                assertTrue((price - baseCents) * 2000 <= baseCents)
            }
        }
    }
}
