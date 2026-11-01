package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.catalog.FenceConnections
import com.hunter.stockfarmer.core.catalog.FencePiece
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class StandingLayoutTest {
    private val tile = FarmRenderer.TILE

    private fun rowBottom(row: Int) = (GameState.GRID_SIZE - 1 - row) * tile

    @Test
    fun `a tree is as wide as its ground and rests on the bottom edge of its last row`() {
        // 96 pixels to a tile: a two tile tree picture of 192 by 288 pixels.
        val box = StandingLayout.standing(GridPoint(3, 4), 2, 2, 192f, 288f, StandingLayout.LARGE_PIXELS_PER_TILE)
        assertEquals(2 * tile, box.width, 0.001f)
        assertEquals(3 * tile, box.height, 0.001f)
        assertEquals(4 * tile, box.x, 0.001f)
        assertEquals(rowBottom(4), box.y, 0.001f)
    }

    @Test
    fun `a rock is centered on its tile and drawn larger by its sprite scale`() {
        val box = StandingLayout.standing(GridPoint(7, 2), 1, 1, 96f, 96f, StandingLayout.LARGE_PIXELS_PER_TILE, 1.5f)
        assertEquals(1.5f * tile, box.width, 0.001f)
        assertEquals(2.5f * tile - 0.75f * tile, box.x, 0.001f)
        assertEquals(rowBottom(7), box.y, 0.001f)
    }

    @Test
    fun `a decoration stands on its ground while a sprinkler lies centered on it`() {
        val standing = StandingLayout.standing(GridPoint(5, 5), 2, 1, 96f, 96f, StandingLayout.PIXELS_PER_TILE, 1f)
        assertEquals(2 * tile, standing.width, 0.001f)
        assertEquals(5 * tile, standing.x, 0.001f)
        assertEquals(rowBottom(5), standing.y, 0.001f)

        val lying = StandingLayout.lying(GridPoint(5, 5), 1, 1, 48f, 48f, 1f)
        assertEquals(tile, lying.width, 0.001f)
        assertEquals(rowBottom(5), lying.y, 0.001f)
        val tall = StandingLayout.lying(GridPoint(5, 5), 1, 1, 48f, 96f, 1f)
        assertEquals(rowBottom(5) + tile / 2 - tall.height / 2, tall.y, 0.001f)
    }

    @Test
    fun `a building is fitted inside its footprint and centered on it`() {
        val box = StandingLayout.building(GridPoint(10, 12), 3, 2, 300f, 100f)
        assertEquals(3 * tile, box.width, 0.001f)
        assertEquals(tile, box.height, 0.001f)
        assertEquals(12 * tile, box.x, 0.001f)
        // The footprint spans rows 10 and 11, so its bottom is the bottom of row 11 and the picture floats half a tile above it.
        assertEquals(rowBottom(11) + tile / 2, box.y, 0.001f)

        val narrow = StandingLayout.building(GridPoint(10, 12), 3, 2, 100f, 200f)
        assertEquals(tile, narrow.width, 0.001f)
        assertEquals(2 * tile, narrow.height, 0.001f)
        assertEquals(13 * tile, narrow.x, 0.001f)
        assertEquals(rowBottom(11), narrow.y, 0.001f)
    }

    @Test
    fun `a fruit tree fills ninety five percent of its square at most`() {
        val box = StandingLayout.fruitTree(GridPoint(4, 6), 2, 100f, 200f)
        assertEquals(2 * tile * 0.95f, box.height, 0.001f)
        assertEquals(2 * tile * 0.95f / 2, box.width, 0.001f)
        assertEquals((6 + 1) * tile - box.width / 2, box.x, 0.001f)
        assertEquals(rowBottom(5), box.y, 0.001f)
    }

    @Test
    fun `a crop is four half tile plants from the top left across and down`() {
        val point = GridPoint(8, 9)
        val expected =
            listOf(
                9 * tile to rowBottom(8) + tile / 2,
                9.5f * tile to rowBottom(8) + tile / 2,
                9 * tile to rowBottom(8),
                9.5f * tile to rowBottom(8),
            )
        for (quadrant in 0 until 4) {
            val box = StandingLayout.quadrant(point, quadrant)
            assertEquals(tile / 2, box.width, 0.001f)
            assertEquals(tile / 2, box.height, 0.001f)
            assertEquals(expected[quadrant].first, box.x, 0.001f)
            assertEquals(expected[quadrant].second, box.y, 0.001f)
        }
    }

    @Test
    fun `fence pictures are a third of a tile apart and half as wide again`() {
        assertEquals(tile / 3 * 1.5f, StandingLayout.fencePictureSize, 0.001f)
    }

    @Test
    fun `fence pictures lower on the tile are drawn in front and the middle is in front of its own line`() {
        val junction = FenceConnections(north = true, east = true, south = true, west = true)
        val order = StandingLayout.fenceDrawOrder(junction)
        assertEquals(listOf(-1, 0, 0, 0, 0, 1), order.map { it.row })
        assertEquals(listOf(0, -1, 1, 0, 0, 0), order.map { it.column })
        assertEquals(
            listOf(FencePiece.middleHorizontal, FencePiece.middleVertical),
            order.filter { it.column == 0 && it.row == 0 }.map { it.piece },
        )
    }

    @Test
    fun `a post with only a fence below it draws the post first and the arm over it`() {
        val order =
            StandingLayout.fenceDrawOrder(
                FenceConnections(north = false, east = false, south = true, west = false),
            )
        assertEquals(listOf(0, 1), order.map { it.row })
    }
}
