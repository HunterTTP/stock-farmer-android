package com.hunter.stockfarmer.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BirchTest {
    @Test fun `birchHasThreeColorsAndFourGrowingSizes`() {
        val birch = TreeSpecies.named("birch")!!
        assertEquals(listOf("green", "brown", "yellow"), birch.colors.map { it.id })
        assertEquals(listOf(TreeSize.small, TreeSize.medium, TreeSize.big, TreeSize.huge), birch.trees.map { it.size })
        assertEquals(listOf(3, 4, 5, 6), birch.trees.map { it.logs })
    }
}
