package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.game.TILE
import com.hunter.stockfarmer.game.WORLD
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ViewBoundsTest {
    private val tile = TILE

    @Test
    fun `everything shows every tile of the map`() {
        val all = ViewBounds.EVERYTHING
        assertEquals(0..GameState.GRID_SIZE - 1, all.columns)
        assertEquals(0..GameState.GRID_SIZE - 1, all.rows)
        assertTrue(all.seesTile(0, 0))
        assertTrue(all.seesTile(GameState.GRID_SIZE - 1, GameState.GRID_SIZE - 1))
        assertTrue(all.sees(-1e9f, -1e9f, 1f, 1f))
    }

    @Test
    fun `columns and rows cover the tiles a view touches`() {
        // World y counts up while rows count down: world tile 10 up from the bottom is row 39.
        val view = ViewBounds.around(10.5f * tile, 10.5f * tile, 2 * tile, 2 * tile)
        assertEquals(9..11, view.columns)
        assertEquals(38..40, view.rows)
    }

    @Test
    fun `a view past the map edge is clamped to the grid`() {
        val view = ViewBounds.around(0f, WORLD, 10 * tile, 10 * tile)
        assertEquals(0..5, view.columns)
        assertEquals(0..4, view.rows)
        val far = ViewBounds.around(WORLD, 0f, 10 * tile, 10 * tile)
        assertEquals(GameState.GRID_SIZE - 5..GameState.GRID_SIZE - 1, far.columns)
        assertEquals(GameState.GRID_SIZE - 6..GameState.GRID_SIZE - 1, far.rows)
    }

    @Test
    fun `a tile outside the view is not seen and one touching it is`() {
        val view = ViewBounds.around(5 * tile, 5 * tile, 2 * tile, 2 * tile)
        val row = GameState.GRID_SIZE - 1 - 5
        assertTrue(view.seesTile(row, 5))
        assertTrue(view.seesTile(row, 4))
        assertFalse(view.seesTile(row, 7))
        assertFalse(view.seesTile(row - 3, 5))
    }

    @Test
    fun `reach lets overhang into the view`() {
        val view = ViewBounds.around(5 * tile, 5 * tile, 2 * tile, 2 * tile)
        val x = 8 * tile
        assertFalse(view.sees(x, 5 * tile, tile, tile))
        assertFalse(view.sees(x, 5 * tile, tile, tile, reach = tile))
        assertTrue(view.sees(x, 5 * tile, tile, tile, reach = 2 * tile))
    }

    @Test
    fun `padding grows the view on every side`() {
        val padded = ViewBounds.around(5 * tile, 5 * tile, 2 * tile, 2 * tile).padded(tile)
        assertEquals(3 * tile, padded.left)
        assertEquals(7 * tile, padded.right)
        assertEquals(3 * tile, padded.bottom)
        assertEquals(7 * tile, padded.top)
    }
}
