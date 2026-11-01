package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GridPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToolDragTest {
    @Test
    fun `staying on a tile gives just that tile`() {
        assertEquals(listOf(GridPoint(5, 5)), ToolDrag.tilesBetween(GridPoint(5, 5), GridPoint(5, 5)))
    }

    @Test
    fun `a quick drag along a row skips no tile`() {
        assertEquals(
            listOf(GridPoint(5, 6), GridPoint(5, 7), GridPoint(5, 8)),
            ToolDrag.tilesBetween(GridPoint(5, 5), GridPoint(5, 8)),
        )
    }

    @Test
    fun `a diagonal drag steps one tile each way`() {
        assertEquals(
            listOf(GridPoint(1, 1), GridPoint(2, 2), GridPoint(3, 3)),
            ToolDrag.tilesBetween(GridPoint(0, 0), GridPoint(3, 3)),
        )
    }

    @Test
    fun `a drag ends on the tile the finger is over`() {
        assertEquals(GridPoint(2, 9), ToolDrag.tilesBetween(GridPoint(8, 1), GridPoint(2, 9)).last())
    }
}
