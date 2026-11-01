package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

class FirefliesTest {
    private val size = GameState.GRID_SIZE
    private val center = size / 2.0

    /** A forest around the edge of the map and a few things near the middle, which is where the world puts them. */
    private val hideaways: List<GridPoint> =
        (0 until size).flatMap {
            listOf(GridPoint(it, 1), GridPoint(it, size - 2), GridPoint(1, it), GridPoint(size - 2, it))
        } + listOf(GridPoint(24, 24), GridPoint(25, 27), GridPoint(22, 25), GridPoint(28, 23))

    private fun distanceToCenter(position: Fireflies.TilePosition) = hypot(position.row - center, position.col - center)

    @Test
    fun `fireflies only come out in the deep of night and very rarely`() {
        assertEquals(0.0, Fireflies.spawnChancePerSecond(0.0))
        assertEquals(0.0, Fireflies.spawnChancePerSecond(0.5))
        val atNight = Fireflies.spawnChancePerSecond(1.0)
        assertTrue(atNight > 0 && atNight <= 0.05)
        assertTrue(Fireflies.spawnChancePerSecond(0.85) < atNight)
        assertEquals(8, Fireflies.MAX_AT_ONCE)
    }

    @Test
    fun `a fireflies light pulses between dim and bright over each period`() {
        val period = 2.0
        val samples =
            generateSequence(0.0) {
                it + 0.05
            }.takeWhile { it < 4 * period }.map { Fireflies.glow(it, period) }.toList()
        assertTrue(samples.all { it in Fireflies.DIMMEST_GLOW..1.0 })
        assertTrue(samples.min() < Fireflies.DIMMEST_GLOW + 0.05 && samples.max() > 0.95)
        assertTrue(abs(Fireflies.glow(0.3, period) - Fireflies.glow(0.3 + period, period)) < 0.0001)
    }

    @Test
    fun `a flight sets off from behind something at the edge and ends nearer the middle`() {
        val generator = SeededRandom(5)
        repeat(200) {
            val flight = Fireflies.flight(hideaways, generator)!!
            val start = flight.first()
            val end = flight.last()
            val startTile = GridPoint(start.row.toInt(), start.col.toInt())
            val endTile = GridPoint(end.row.toInt(), end.col.toInt())
            assertTrue(startTile in hideaways && endTile in hideaways)
            assertTrue(
                min(min(startTile.row, startTile.col), min(size - 1 - startTile.row, size - 1 - startTile.col)) <
                    Fireflies.EDGE_WIDTH,
            )
            assertTrue(distanceToCenter(end) < distanceToCenter(start) / 2)
            assertTrue(flight.all { it.row in 0.0..size.toDouble() && it.col in 0.0..size.toDouble() })
        }
    }

    @Test
    fun `a flight works its way inward in gentle steps`() {
        val generator = SeededRandom(8)
        repeat(100) {
            val flight = Fireflies.flight(hideaways, generator)!!
            val legs = flight.zipWithNext().map { (a, b) -> hypot(b.row - a.row, b.col - a.col) }
            assertTrue(legs.dropLast(1).all { it <= 4 })
            assertTrue(flight.size >= 4)
            assertTrue(distanceToCenter(flight[flight.size / 2]) < distanceToCenter(flight[0]))
        }
    }

    @Test
    fun `a flight takes a long gentle time at a walking pace`() {
        val flight = Fireflies.flight(hideaways, SeededRandom(3))!!
        val seconds = Fireflies.duration(flight)
        assertTrue(Fireflies.SPEED > 0.2 && Fireflies.SPEED < 0.6)
        assertTrue(seconds > 30 && seconds < 240)
        assertEquals(0.0, Fireflies.duration(listOf(flight[0])))
    }

    @Test
    fun `with nothing to hide behind there is no flight`() {
        assertNull(Fireflies.flight(emptyList(), SeededRandom(1)))
    }

    @Test
    fun `with nothing at the edge a flight starts from the hideaway farthest from the middle`() {
        val flight =
            Fireflies.flight(listOf(GridPoint(24, 24), GridPoint(15, 15), GridPoint(26, 26)), SeededRandom(2))
        assertEquals(Fireflies.TilePosition(15.5, 15.5), flight?.first())
    }

    @Test
    fun `fireflies hide behind trees crops rocks decorations and buildings but not the backdrop trees`() {
        val state = GameState()
        val tree = Tree.starter
        val now = Instant.ofEpochSecond(1_000)
        state.putTree(GridPoint(10, 10), PlacedTree(tree, TreeColor.green, now))
        state.putTree(GridPoint(GameState.BACKDROP_ROW, 3), PlacedTree(tree, TreeColor.green, now))
        state.plots[GridPoint(20, 20)] = Plot("wheat", now)
        state.rocks[GridPoint(30, 30)] = RockSize.small
        state.putBuilding(GridPoint(40, 40), "petHouse")
        val found = Fireflies.hideaways(state).toSet()
        assertTrue(tree.footprint(GridPoint(10, 10)).toSet().all { it in found })
        assertTrue(GridPoint(20, 20) in found && GridPoint(30, 30) in found && GridPoint(40, 40) in found)
        assertFalse(GridPoint(GameState.BACKDROP_ROW, 3) in found)
        assertFalse(GridPoint(12, 20) in found)
    }
}
