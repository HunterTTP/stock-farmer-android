package com.hunter.stockfarmer.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GridPointTest {
    @Test
    fun `rectangle runs row by row`() {
        assertEquals(
            listOf(GridPoint(2, 3), GridPoint(2, 4), GridPoint(3, 3), GridPoint(3, 4)),
            GridPoint(2, 3).rectangle(rows = 2, cols = 2),
        )
    }

    @Test
    fun `a square stands on its bottom middle`() {
        assertEquals(GridPoint(4, 5), GridPoint(5, 5).topLeftOfSquare(side = 2))
        assertEquals(GridPoint(3, 4), GridPoint(5, 5).topLeftOfSquare(side = 3))
    }

    @Test
    fun `bottom row is the lowest row of the tiles`() {
        val tiles = GridPoint(1, 1).rectangle(rows = 2, cols = 2)
        assertEquals(listOf(GridPoint(2, 1), GridPoint(2, 2)), tiles.bottomRow)
    }
}
