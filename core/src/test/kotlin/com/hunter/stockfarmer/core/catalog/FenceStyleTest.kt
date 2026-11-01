package com.hunter.stockfarmer.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FenceStyleTest {
    @Test fun `onlyTheOriginalWoodFenceIsOffered`() {
        assertEquals(listOf("wood"), FenceColor.all.map { it.id })
        assertEquals(2.0, FenceColor.price)
    }
}
