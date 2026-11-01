package com.hunter.stockfarmer.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MoneyTest {
    @Test
    fun `keeps cents beyond 32 bits`() {
        assertEquals(100_000_000_000L, Money(100_000_000_000L).cents)
    }
}
