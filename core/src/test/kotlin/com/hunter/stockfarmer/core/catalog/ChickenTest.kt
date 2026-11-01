package com.hunter.stockfarmer.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChickenTest {
    @Test fun `theDefaultHatchingPoolKeepsItsSevenHens`() {
        assertEquals(7, ChickenVariant.all.size)
        assertTrue(ChickenVariant.all.all { it.id.startsWith("hen-") })
        assertEquals(
            7,
            ChickenVariant.all
                .map { it.id }
                .toSet()
                .size,
        )
    }
}
