package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRun
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

internal class TractorMovementTest {
    private val start = Instant.ofEpochSecond(2_000)
    private val driving =
        TractorRun(
            home = GridPoint(10, 10),
            cropID = "wheat",
            position = GridPoint(20, 20),
            nextTile = GridPoint(20, 21),
            facing = TractorFacing.right,
        )

    @Test
    fun `wheels cycle through six frames in the time it takes to drive a tile`() {
        val movement = TractorMovement()
        movement.advance(driving, start)
        assertEquals(0, movement.wheelFrame())
        val frames = mutableListOf<Int>()
        for (step in 1..14) {
            movement.advance(driving.copy(progress = step / 14.0), start.plusMillis(step * 90L))
            frames += movement.wheelFrame()
        }
        // Each frame lasts a fifth of a second, and the cycle starts again after 1.2 seconds.
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 4, 5, 5, 0), frames)
    }

    @Test
    fun `a Tractor held still keeps its wheel frame`() {
        val movement = TractorMovement()
        movement.advance(driving, start)
        movement.advance(driving.copy(progress = 0.5), start.plusMillis(100))
        movement.advance(driving.copy(progress = 0.9), start.plusMillis(200))
        val frame = movement.wheelFrame()
        movement.advance(driving.copy(progress = 0.9), start.plusMillis(300))
        movement.advance(driving.copy(progress = 0.9), start.plusMillis(400))
        assertEquals(frame, movement.wheelFrame())
    }

    @Test
    fun `a run that is already under way starts partway round`() {
        val movement = TractorMovement()
        movement.advance(driving.copy(progress = 0.5), start)
        // Half of a 1.2 second tile is 0.6 seconds, which is the fourth frame.
        assertEquals(3, movement.wheelFrame())
    }

    @Test
    fun `a long pause between frames moves the clock on by at most a tenth of a second`() {
        val movement = TractorMovement()
        movement.advance(driving, start)
        movement.advance(driving.copy(progress = 0.5), start.plusSeconds(30))
        assertEquals(0, movement.wheelFrame())
    }

    @Test
    fun `the body rises by the bobbing height as it drives and not when it has nowhere to go`() {
        val movement = TractorMovement()
        movement.advance(driving, start)
        assertEquals(0.0, movement.lift(driving), 1e-12)
        // A quarter of the tile's time is half a bob at the default speed of two bobs a tile.
        for (step in 1..3) movement.advance(driving.copy(progress = step / 12.0), start.plusMillis(step * 100L))
        assertEquals(0.005, movement.lift(driving), 1e-12)
        assertEquals(0.0, movement.lift(driving.copy(nextTile = null)), 0.0)
    }

    @Test
    fun `stopping the run resets the clock`() {
        val movement = TractorMovement()
        movement.advance(driving, start)
        for (step in 1..5) movement.advance(driving.copy(progress = step / 12.0), start.plusMillis(step * 100L))
        movement.advance(null, start.plusMillis(600))
        assertEquals(0, movement.wheelFrame())
    }
}
