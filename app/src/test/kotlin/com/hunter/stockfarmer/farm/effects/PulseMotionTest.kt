package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.farm.FarmRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class PulseMotionTest {
    private data class Glow(
        val row: Int,
        val col: Int,
        val intensity: Double,
        val twinkle: Double,
        val pattern: Int,
    )

    private val midnight = 1_440_000L

    private fun at(progress: Double): Instant =
        Instant.ofEpochSecond(midnight).plusNanos((progress * NightPulse.LENGTH_MINUTES * 1e9).toLong())

    private fun farm() =
        GameState().apply {
            for (col in 20 until 30) farmland += GridPoint(24, col)
            for (row in 5 until 9) water[GridPoint(row, 40)] = WaterStyle.deep
            putBuilding(GridPoint(10, 10), "house2", null)
            putBuilding(GridPoint(30, 33), "marketStand", null)
        }

    /** The wave as it was worked out before: every tile of the map, then the tiles the camera shows. */
    private fun everyTile(
        state: GameState,
        origin: Point,
        progress: Double,
        view: View?,
    ): List<Glow> {
        val covered = state.farmland + state.water.keys + state.placedBuildings.flatMap { it.footprint }
        val reach = NightPulse.reach(origin.y, origin.x)
        return (0 until GameState.GRID_SIZE).flatMap { row ->
            (0 until GameState.GRID_SIZE).mapNotNull { col ->
                if (GridPoint(row, col) in covered) return@mapNotNull null
                val intensity = NightPulse.intensity(hypot(row + 0.5 - origin.y, col + 0.5 - origin.x), progress, reach)
                if (intensity <= 0.01) return@mapNotNull null
                val x = col * TILE
                val y = FarmRenderer.worldY(row).toDouble()
                if (view != null &&
                    (x + TILE < view.left || x > view.right || y + TILE < view.bottom || y > view.top)
                ) {
                    return@mapNotNull null
                }
                val hash = kotlin.math.abs(((row * 73856093) xor (col * 19349663)).toLong())
                Glow(row, col, intensity, 0.7 + 0.3 * sin(progress * 60 + hash % 628 / 100.0), (hash % 6).toInt())
            }
        }
    }

    private fun PulseTiles.glows() =
        (0 until size).map { Glow(row(it), col(it), intensity(it), twinkle(it), pattern(it)) }

    @Test fun `the band lights what a sweep of the whole map does, wherever the wave starts and the camera looks`() {
        val state = farm()
        val random = Random(7)
        val origins = listOf(Point(25.0, 25.0), Point(0.0, 0.0), Point(50.0, 3.5), Point(12.3, 41.7))
        val views =
            listOf(
                null,
                View(0.0, 0.0, WORLD, WORLD),
                View(1_234.5, 2_000.0, 1_080.0, 2_340.0),
                View(-300.0, 3_900.0, 900.0, 1_600.0),
            )
        var lit = 0
        for (origin in origins) {
            for (view in views) {
                val motion = PulseMotion()
                val steps = List(40) { random.nextDouble() } + listOf(0.0, 0.05, 0.5, 0.95)
                for (step in steps) {
                    val progress = NightPulse.wave(at(step), 0.0)!!.progress
                    val tiles = motion.tiles(state, at(step), origin, view).glows()
                    assertEquals(everyTile(state, origin, progress, view), tiles, "from $origin in $view at $progress")
                    lit += tiles.size
                }
            }
        }
        assertTrue(lit > 0)
    }

    @Test fun `every frame refills the same tiles and the wave is dark outside its minutes`() {
        val state = GameState()
        val motion = PulseMotion()
        val first = motion.tiles(state, at(0.4), Point(25.0, 25.0))
        assertTrue(first.size > 0)
        assertSame(first, motion.tiles(state, at(0.5), Point(25.0, 25.0)))
        assertEquals(0, motion.tiles(state, Instant.ofEpochSecond(midnight - 1), Point(25.0, 25.0)).size)
    }

    @Test fun `the pattern strip shows each pattern as its own texture did`() {
        for (pattern in 0 until PulsePatterns.COUNT) {
            val lit = (0 until 144).shuffled(Random(pattern)).take(7).toSet()
            for (row in 0 until PulsePatterns.SIZE) {
                for (col in 0 until PulsePatterns.SIZE) {
                    val expected = if (row * 12 + col in lit) -1 else 0
                    assertEquals(expected, PulsePatterns.stripPixel(PulsePatterns.left(pattern) + col, row))
                }
            }
        }
        val patterns =
            (0 until PulsePatterns.COUNT).flatMap { pattern ->
                (0 until 12).map {
                    PulsePatterns.left(pattern) +
                        it
                }
            }
        val gaps = (0 until PulsePatterns.STRIP_WIDTH) - patterns.toSet()
        assertEquals(PulsePatterns.COUNT, gaps.size)
        assertTrue(gaps.all { x -> (0 until PulsePatterns.SIZE).all { PulsePatterns.stripPixel(x, it) == 0 } })
    }
}
