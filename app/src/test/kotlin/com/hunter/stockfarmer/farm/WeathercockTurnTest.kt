package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

internal class WeathercockTurnTest {
    private val origin = GridPoint(10, 0)
    private val start = Instant.ofEpochSecond(1_000)

    @Test
    fun `a repeated wind direction does not turn a settled weathercock`() {
        val turn = WeathercockTurn()
        turn.scaleX(origin, 0f, start)
        turn.gust(true, start)
        for (millis in listOf(-1L, 0L, 250L, 500L, 2000L)) {
            assertEquals(1f, turn.scaleX(origin, 0f, start.plusMillis(millis)))
        }
    }

    @Test
    fun `a reversing gust starts from the interrupted turn and waits for its sweep`() {
        val width = GameState.GRID_SIZE * FarmRenderer.TILE
        val turn = WeathercockTurn()
        turn.scaleX(origin, width, start)
        turn.gust(false, start)
        assertEquals(0f, turn.scaleX(origin, width, start.plusMillis(250)), 1e-6f)
        turn.gust(true, start.plusMillis(250))
        assertEquals(0f, turn.scaleX(origin, width, start.plusMillis(1750)), 1e-6f)
        assertEquals(0.5f, turn.scaleX(origin, width, start.plusMillis(2000)), 1e-6f)
        assertEquals(1f, turn.scaleX(origin, width, start.plusMillis(2250)))
    }

    @Test
    fun `weathercocks at opposite edges turn independently as the gust reaches them`() {
        val width = GameState.GRID_SIZE * FarmRenderer.TILE
        val other = GridPoint(10, GameState.GRID_SIZE - 1)
        val turn = WeathercockTurn()
        turn.scaleX(origin, 0f, start)
        turn.scaleX(other, width, start)
        turn.gust(false, start)
        assertEquals(1f, turn.scaleX(origin, 0f, start.plusMillis(250)))
        assertEquals(0f, turn.scaleX(other, width, start.plusMillis(250)), 1e-6f)
        assertEquals(-1f, turn.scaleX(other, width, start.plusMillis(1750)))
        assertEquals(0f, turn.scaleX(origin, 0f, start.plusMillis(1750)), 1e-6f)
    }

    @Test
    fun `a weathercock is drawn as is until a gust has blown`() {
        assertEquals(1f, WeathercockTurn().scaleX(origin, 0f, start))
    }

    @Test
    fun `a gust sweeps across the map the way it pushes`() {
        val width = GameState.GRID_SIZE * FarmRenderer.TILE
        assertEquals(0.0, WeathercockTurn.gustDelay(0f, true), 1e-9)
        assertEquals(1.5, WeathercockTurn.gustDelay(width, true), 1e-9)
        assertEquals(1.5, WeathercockTurn.gustDelay(0f, false), 1e-9)
        assertEquals(0.75, WeathercockTurn.gustDelay(width / 2, false), 1e-9)
    }

    @Test
    fun `a gust from the right turns the weathercock over half a second once the gust reaches it`() {
        val turn = WeathercockTurn()
        turn.scaleX(origin, 0f, start)
        turn.gust(false, start)
        // At the left edge a leftward gust takes the whole 1.5 second sweep to arrive.
        assertEquals(1f, turn.scaleX(origin, 0f, start))
        assertEquals(1f, turn.scaleX(origin, 0f, start.plusMillis(1_400)))
        assertEquals(0f, turn.scaleX(origin, 0f, start.plusMillis(1_750)), 0.001f)
        assertEquals(-1f, turn.scaleX(origin, 0f, start.plusMillis(2_000)))
        assertEquals(-1f, turn.scaleX(origin, 0f, start.plusSeconds(60)))
    }

    @Test
    fun `a weathercock set down after a gust faces the way the latest gust left it`() {
        val turn = WeathercockTurn()
        turn.gust(false, start)
        assertEquals(-1f, turn.scaleX(origin, 0f, start.plusSeconds(5)))
    }

    @Test
    fun `a later gust the other way turns it back`() {
        val turn = WeathercockTurn()
        turn.scaleX(origin, 0f, start)
        turn.gust(false, start)
        turn.scaleX(origin, 0f, start.plusSeconds(10))
        val later = start.plusSeconds(30)
        turn.gust(true, later)
        // Pushing right reaches the left edge at once.
        assertEquals(-1f, turn.scaleX(origin, 0f, later))
        assertEquals(0f, turn.scaleX(origin, 0f, later.plusMillis(250)), 0.001f)
        assertEquals(1f, turn.scaleX(origin, 0f, later.plusMillis(500)))
    }
}
