package com.hunter.stockfarmer.market

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class StockPollingTest {
    private val base = Instant.parse("2026-01-01T12:00:00Z")

    @Test
    fun `waits out the rest of the pull interval`() {
        assertEquals(240.0, StockPolling.waitSeconds(base, base.plusSeconds(60), false), 1e-6)
    }

    @Test
    fun `never polls faster than once a second`() {
        assertEquals(1.0, StockPolling.waitSeconds(null, base, false), 1e-6)
        assertEquals(1.0, StockPolling.waitSeconds(base, base.plusSeconds(300), false), 1e-6)
    }

    @Test
    fun `adds idle time when nothing is owned`() {
        assertEquals(5.0, StockPolling.waitSeconds(null, base, true), 1e-6)
    }

    @Test
    fun `a pull stamped in the future does not hold polling off`() {
        assertEquals(1.0, StockPolling.waitSeconds(base.plusSeconds(100), base, false), 1e-6)
    }
}
