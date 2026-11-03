package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.secondsSince
import com.hunter.stockfarmer.game.TILE
import java.time.Instant

/**
 * Weathercocks face into the wind: their picture faces left, which is where a wind blowing to the right comes from. As each gust
 * reaches one, after the time it takes to sweep across the map, it turns by flipping over half a second.
 */
class WeathercockTurn {
    private class Turn(
        val gust: Int,
        val from: Float,
        val to: Float,
        val start: Instant,
    )

    private val turns = HashMap<GridPoint, Turn>()
    private var gusts = 0
    private var pushesRight = true
    private var gustAt = Instant.EPOCH

    /** A gust has set off, blowing towards the right of the map or not; every weathercock it reaches turns to face into it. */
    fun gust(
        pushesRight: Boolean,
        at: Instant,
    ) {
        this.pushesRight = pushesRight
        gustAt = at
        gusts++
    }

    /** The horizontal scale of the weathercock at [origin], standing at world [x]: 1 as drawn, -1 flipped, and between while it turns. */
    fun scaleX(
        origin: GridPoint,
        x: Float,
        now: Instant,
    ): Float {
        val facing = facing(pushesRight)
        var turn = turns[origin]
        if (turn == null) {
            turn = Turn(gusts, facing, facing, gustAt)
            turns[origin] = turn
        } else if (turn.gust != gusts) {
            val start = gustAt.plusMillis((gustDelay(x, pushesRight) * 1000).toLong())
            turn = Turn(gusts, progress(turn, gustAt), facing, start)
            turns[origin] = turn
        }
        return progress(turn, now)
    }

    private fun progress(
        turn: Turn,
        now: Instant,
    ): Float {
        val done = (now.secondsSince(turn.start) / TURN_SECONDS).coerceIn(0.0, 1.0).toFloat()
        return turn.from + (turn.to - turn.from) * done
    }

    companion object {
        private const val TURN_SECONDS = 0.5
        private const val SWEEP_SECONDS = 1.5

        private fun facing(pushesRight: Boolean) = if (pushesRight) 1f else -1f

        /** How long after a gust sets off it reaches something standing at world [x], since it travels the way it pushes. */
        fun gustDelay(
            x: Float,
            pushesRight: Boolean,
        ): Double {
            val along = x / (GameState.GRID_SIZE * TILE)
            return SWEEP_SECONDS * (if (pushesRight) along else 1 - along)
        }
    }
}
