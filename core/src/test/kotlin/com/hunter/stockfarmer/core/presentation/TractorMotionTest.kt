package com.hunter.stockfarmer.core.presentation

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRules
import com.hunter.stockfarmer.core.model.TractorRun
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.advanceTractor
import com.hunter.stockfarmer.core.rules.building
import com.hunter.stockfarmer.core.rules.cancelTractorHarvest
import com.hunter.stockfarmer.core.rules.containsRejection
import com.hunter.stockfarmer.core.rules.startTractorHarvest
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

class TractorMotionTest {
    private val home = GridPoint(10, 10)
    private val point = GridPoint(20, 20)
    private val now = Instant.ofEpochSecond(40_000_000)

    private fun run(
        position: GridPoint,
        facing: TractorFacing,
        next: GridPoint? = null,
        poseFacing: TractorFacing? = null,
        progress: Double = 0.0,
        remaining: Set<GridPoint> = emptySet(),
    ) = TractorRun(
        home = home,
        cropID = "wheat",
        position = position,
        nextTile = next,
        nextPoseFacing = poseFacing,
        facing = facing,
        progress = progress,
        remaining = remaining,
    )

    private fun opposite(facing: TractorFacing) =
        TractorFacing.ofDirection(GridPoint(-facing.direction.row, -facing.direction.col))

    private fun close(
        a: TilePoint,
        b: TilePoint,
        tolerance: Double = 1e-9,
    ) = abs(a.x - b.x) + abs(a.y - b.y) < tolerance

    private fun engineWith(
        run: TractorRun,
        crop: GridPoint,
    ): GameEngine {
        val engine = GameEngine()
        engine.state.putBuilding(home, "tractor1")
        engine.state.plots[crop] = Plot("wheat", now.advancedBy(-100.0))
        engine.state.tractorRun = run
        return engine
    }

    @Test
    fun `driving has the same speed in every direction and through turns`() {
        val elapsed = TractorRules.SECONDS_PER_TILE / 10
        for (facing in TractorFacing.entries) {
            for (heading in TractorFacing.entries) {
                val next = GridPoint(point.row + heading.direction.row, point.col + heading.direction.col)
                val start = TractorMotion.center(point, facing)
                val end = TractorMotion.center(next, heading)
                if (start == end) continue
                val outbound = run(point, facing, next, remaining = setOf(next))
                val reversed = run(next, heading, point, facing, remaining = setOf(point))
                for (trip in listOf(outbound, reversed)) {
                    val engine = engineWith(trip, trip.remaining.first())
                    var previous = TractorMotion.frame(trip).position
                    val travelDistance = abs(end.x - start.x) + abs(end.y - start.y)
                    repeat((travelDistance * 10).toInt() - 1) {
                        assertTrue(engine.advanceTractor(elapsed, true, now).isEmpty())
                        val frame = TractorMotion.frame(engine.state.tractorRun!!)
                        val distance = abs(frame.position.x - previous.x) + abs(frame.position.y - previous.y)
                        assertTrue(abs(distance - 0.1) < 0.000001)
                        previous = frame.position
                    }
                }
            }
        }
    }

    @Test
    fun `in place reversals complete without a full travel delay`() {
        for (facing in TractorFacing.entries) {
            val heading = opposite(facing)
            val next = GridPoint(point.row + heading.direction.row, point.col + heading.direction.col)
            val trip = run(point, facing, next, remaining = setOf(next))
            val engine = engineWith(trip, next)
            assertEquals(listOf<GameEvent>(GameEvent.TractorGathered(next)), engine.advanceTractor(1.0 / 60, true, now))
            val arrived = engine.state.tractorRun!!
            assertTrue(arrived.position == next && arrived.facing == heading)
            assertEquals(TractorMotion.frame(trip).position, TractorMotion.frame(arrived).position)
        }
    }

    @Test
    fun `body center is the center of the whole footprint in every facing`() {
        for (facing in TractorFacing.entries) {
            val horizontal = facing.direction.col != 0
            val head = TractorRules.headTile(home, facing)
            assertEquals(
                TilePoint(10 + (if (horizontal) 1.0 else 0.5), 10 + (if (horizontal) 0.5 else 1.0)),
                TractorMotion.center(head, facing),
            )
        }
    }

    @Test
    fun `quarter turns keep the approach facing until the body reaches the corner`() {
        for (facing in TractorFacing.entries) {
            for (heading in TractorFacing.entries) {
                val dot = facing.direction.row * heading.direction.row + facing.direction.col * heading.direction.col
                if (dot != 0) continue
                val next = GridPoint(point.row + heading.direction.row, point.col + heading.direction.col)
                val start = TractorMotion.center(point, facing)
                val end = TractorMotion.center(next, heading)
                val corner = if (heading.direction.col != 0) TilePoint(start.x, end.y) else TilePoint(end.x, start.y)
                val approach = abs(corner.x - start.x) + abs(corner.y - start.y)
                val exit = abs(end.x - corner.x) + abs(end.y - corner.y)
                val turnProgress = approach / (approach + exit)
                if (approach > 0) {
                    assertEquals(
                        facing,
                        TractorMotion.frame(run(point, facing, next, progress = turnProgress - 0.001)).facing,
                    )
                }
                val atTurn = TractorMotion.frame(run(point, facing, next, progress = turnProgress))
                assertTrue(close(atTurn.position, corner))
                assertEquals(heading, atTurn.facing)
                for (offset in listOf(-0.000001, 0.000001)) {
                    val frame =
                        TractorMotion.frame(
                            run(point, facing, next, progress = max(0.0, turnProgress + offset)),
                        )
                    assertTrue(close(frame.position, corner, 0.00001))
                }
            }
        }
    }

    @Test
    fun `every moving segment faces its actual direction including canceled steps`() {
        for (facing in TractorFacing.entries) {
            for (heading in TractorFacing.entries) {
                val next = GridPoint(point.row + heading.direction.row, point.col + heading.direction.col)
                val steps = listOf(run(point, facing, next), run(next, heading, point, facing))
                for (step in steps) {
                    for (progress in listOf(0.1, 0.3, 0.45, 0.6, 0.85, 0.95)) {
                        val frame = TractorMotion.frame(step.copy(progress = progress))
                        val after = TractorMotion.frame(step.copy(progress = progress + 0.001))
                        val dx = after.position.x - frame.position.x
                        val dy = after.position.y - frame.position.y
                        if (dx != 0.0 || dy != 0.0) {
                            val direction = frame.facing.direction
                            assertTrue(dx * direction.col + dy * direction.row > 0)
                            assertTrue(abs(dx * direction.row - dy * direction.col) < 1e-12)
                            assertEquals(frame.facing, after.facing)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `turning keeps the current body center and moves only along grid axes`() {
        for (facing in TractorFacing.entries) {
            for (heading in TractorFacing.entries) {
                val next = GridPoint(point.row + heading.direction.row, point.col + heading.direction.col)
                val base = run(point, facing, next)
                assertEquals(TractorMotion.center(point, facing), TractorMotion.frame(base).position)
                for (progress in listOf(0.1, 0.3, 0.45, 0.6, 0.85, 0.95)) {
                    val position = TractorMotion.frame(base.copy(progress = progress)).position
                    val after = TractorMotion.frame(base.copy(progress = progress + 0.001)).position
                    assertTrue(position.x == after.x || position.y == after.y)
                }
                assertTrue(
                    close(TractorMotion.frame(base.copy(progress = 1.0)).position, TractorMotion.center(next, heading)),
                )
            }
        }
    }

    @Test
    fun `reversing direction turns in place around the overall center`() {
        for (facing in TractorFacing.entries) {
            val heading = opposite(facing)
            val next = GridPoint(point.row + heading.direction.row, point.col + heading.direction.col)
            val center = TractorMotion.center(point, facing)
            for (progress in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
                val frame = TractorMotion.frame(run(point, facing, next, progress = progress))
                assertTrue(close(frame.position, center))
            }
        }
    }

    @Test
    fun `reversing partial turns retraces the same centered path`() {
        for (facing in TractorFacing.entries) {
            for (heading in TractorFacing.entries) {
                for (progress in listOf(0.25, 0.75)) {
                    val next = GridPoint(point.row + heading.direction.row, point.col + heading.direction.col)
                    val outbound = run(point, facing, next, progress = progress)
                    val returning = run(next, heading, point, facing, 1 - progress)
                    assertTrue(close(TractorMotion.frame(returning).position, TractorMotion.frame(outbound).position))
                    val state = GameState()
                    state.putBuilding(home, "tractor1")
                    state.tractorRun = returning
                    assertTrue(sameState(GameStateJson.decode(GameStateJson.encode(state)), state))
                }
            }
        }
    }

    @Test
    fun `cancellation preserves the center and the saved reverse trajectory`() {
        val engine = GameEngine()
        engine.state.putBuilding(home, "tractor1")
        val crop = GridPoint(10, 20)
        engine.state.plots[crop] = Plot("wheat", now.advancedBy(-100.0))
        assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
        engine.advanceTractor(TractorRules.SECONDS_PER_TILE / 4, true, now)
        val before = TractorMotion.frame(engine.state.tractorRun!!).position
        assertTrue(engine.cancelTractorHarvest(home).isEmpty())
        val returning = engine.state.tractorRun!!
        assertTrue(close(TractorMotion.frame(returning).position, before))
        assertEquals(TractorFacing.right, returning.nextPoseFacing)
        assertTrue(sameState(GameStateJson.decode(GameStateJson.encode(engine.state)), engine.state))
        assertNotNull(returning.nextPoseFacing)
        assertNull(run(point, TractorFacing.right).nextPoseFacing)
    }

    @Test
    fun `returning matches the whole parked footprint from either side`() {
        for (style in listOf("right", "left", "front", "back")) {
            for (crop in listOf(GridPoint(10, 20), GridPoint(10, 2), GridPoint(2, 10), GridPoint(20, 10))) {
                val engine = GameEngine()
                engine.state.putBuilding(home, "tractor1", style)
                engine.state.plots[crop] = Plot("wheat", now.advancedBy(-100.0))
                assertTrue(engine.startTractorHarvest(home, "wheat", now).isEmpty())
                val parked = engine.building(home)!!.footprint.toSet()
                var steps = 0
                while (engine.state.tractorRun != null && steps++ < 200) {
                    val run = engine.state.tractorRun!!
                    val events = engine.advanceTractor(TractorRules.SECONDS_PER_TILE, true, now)
                    assertFalse(events.containsRejection)
                    if (engine.state.tractorRun == null) {
                        assertEquals(
                            parked,
                            TractorRules.footprint(run.nextTile ?: run.position, run.destinationFacing).toSet(),
                        )
                    }
                }
                assertTrue(engine.state.tractorRun == null && engine.state.plots[crop] == null)
            }
        }
    }

    @Test
    fun `bobbing has adjustable height and speed without resetting at tile boundaries`() {
        val normal = TractorBobbing()
        assertTrue(normal.height == 0.005 && normal.speed == 2.0)
        assertTrue(abs(normal.offset(0.0)) < 0.000001)
        assertTrue(abs(normal.offset(TractorRules.SECONDS_PER_TILE / (2 * normal.speed)) - normal.height) < 0.000001)
        val fast = TractorBobbing(0.04, 2.0)
        assertTrue(abs(fast.offset(TractorRules.SECONDS_PER_TILE / 4) - 0.04) < 0.000001)
        val slow = TractorBobbing(0.04, 0.5)
        assertTrue(abs(slow.offset(TractorRules.SECONDS_PER_TILE) - 0.04) < 0.000001)
        assertEquals(0.0, TractorBobbing(0.0).offset(1.0))
        assertEquals(TractorBobbing(0.0, 4.0), TractorBobbing(-1.0, 100.0))
        assertEquals(normal, TractorBobbing(Double.NaN, Double.POSITIVE_INFINITY))
    }
}
