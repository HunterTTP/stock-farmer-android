package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.Rarity
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

class ButterfliesTest {
    private val size = GameState.GRID_SIZE
    private val planted = Instant.ofEpochSecond(1_000)

    private fun isAtTheEdge(position: Fireflies.TilePosition) =
        min(min(position.row, position.col), min(size - position.row, size - position.col)) <= 1

    private fun onTheMap(position: Fireflies.TilePosition) =
        position.row in 0.0..size.toDouble() && position.col in 0.0..size.toDouble()

    @Test
    fun `butterflies only come out by day in dry weather outside winter`() {
        assertTrue(Butterflies.spawnChancePerSecond(0.0, 0.0, Season.spring) > 0)
        assertTrue(Butterflies.spawnChancePerSecond(0.0, 0.0, Season.fall) > 0)
        assertEquals(0.0, Butterflies.spawnChancePerSecond(0.5, 0.0, Season.spring))
        assertEquals(0.0, Butterflies.spawnChancePerSecond(0.0, 0.3, Season.spring))
        assertEquals(0.0, Butterflies.spawnChancePerSecond(0.0, 0.0, Season.winter))
        assertTrue(Butterflies.MAX_AT_ONCE in 1..8)
    }

    @Test
    fun `a flight comes in from the edge visits something and leaves by the edge`() {
        val attraction = Butterflies.Attraction(GridPoint(24, 24))
        val generator = SeededRandom(4)
        repeat(100) {
            val flight = Butterflies.flight(listOf(attraction), generator)
            assertTrue(isAtTheEdge(flight.first()) && isAtTheEdge(flight.last()))
            assertTrue(flight.count { it.row == 24.5 && it.col == 24.5 } >= 2)
            assertTrue(flight.all(::onTheMap))
            assertTrue(flight.size <= Butterflies.LONGEST_FLIGHT)
        }
    }

    @Test
    fun `a flight flutters in gentle steps at a lively pace`() {
        val flight =
            Butterflies.flight(
                listOf(Butterflies.Attraction(GridPoint(10, 30)), Butterflies.Attraction(GridPoint(35, 12))),
                SeededRandom(9),
            )
        val legs = flight.zipWithNext().map { (a, b) -> hypot(b.row - a.row, b.col - a.col) }
        assertTrue(legs.all { it <= Butterflies.stepLength.endInclusive + 0.001 })
        val seconds = Butterflies.duration(flight)
        assertTrue(Butterflies.SPEED > Fireflies.SPEED)
        assertTrue(seconds > 10 && seconds < 180)
    }

    @Test
    fun `with nothing to visit a flight still crosses the farm`() {
        val flight = Butterflies.flight(emptyList(), SeededRandom(2))
        assertTrue(flight.size >= 3)
        assertTrue(isAtTheEdge(flight[0]))
    }

    @Test
    fun `butterflies visit rocks crops and decorations but not bare ground`() {
        val state = GameState()
        state.plots[GridPoint(20, 20)] = Plot("wheat", planted)
        state.putDecoration(GridPoint(30, 30), PlacedDecoration(Decoration.named("pumpkin")!!.id))
        state.rocks[GridPoint(10, 10)] = RockSize.big
        val visited = Butterflies.attractions(state).map { it.tile }.toSet()
        assertEquals(setOf(GridPoint(20, 20), GridPoint(30, 30), GridPoint(10, 10)), visited)
    }

    @Test
    fun `crop landings visit all four sprite centers instead of the tile center`() {
        val tile = GridPoint(24, 24)
        val state = GameState()
        state.plots[tile] = Plot("wheat", planted)
        val attractions = Butterflies.attractions(state)
        val expected =
            listOf(
                Fireflies.TilePosition(24.25, 24.25),
                Fireflies.TilePosition(24.25, 24.75),
                Fireflies.TilePosition(24.75, 24.25),
                Fireflies.TilePosition(24.75, 24.75),
            )
        assertEquals(1, attractions.size)
        assertEquals(expected, attractions.first().landingPoints)
        val generator = SeededRandom(4)
        val visits = IntArray(expected.size)
        repeat(100) {
            val flight = Butterflies.flight(attractions, generator)
            assertFalse(Fireflies.TilePosition.middleOf(tile) in flight)
            assertEquals(2, flight.count { it in expected })
            for ((index, point) in expected.withIndex()) visits[index] += flight.count { it == point }
        }
        assertTrue(visits.all { it > 20 && it < 80 })
    }

    @Test
    fun `other attractions keep their tile center landing`() {
        val state = GameState()
        state.plots[GridPoint(20, 20)] = Plot("apple", planted)
        state.rocks[GridPoint(10, 10)] = RockSize.big
        state.putDecoration(GridPoint(30, 30), PlacedDecoration(Decoration.named("pumpkin")!!.id))
        for (attraction in Butterflies.attractions(state)) {
            assertEquals(listOf(Fireflies.TilePosition.middleOf(attraction.tile)), attraction.landingPoints)
        }
    }

    @Test
    fun `every rarity color is equally likely and color changing wings are about one percent`() {
        val generator = SeededRandom(5)
        val trials = 100_000
        val wings = List(trials) { Butterflies.wings(generator) }
        val shifting = wings.count { it == Butterflies.Wings.Shifting }.toDouble() / trials
        assertTrue(abs(shifting - Butterflies.SHIFTING_CHANCE) < 0.003 && Butterflies.SHIFTING_CHANCE == 0.01)
        val each = (1 - Butterflies.SHIFTING_CHANCE) / Rarity.entries.size
        for (rarity in Rarity.entries) {
            val share = wings.count { it == Butterflies.Wings.Color(rarity) }.toDouble() / trials
            assertTrue(abs(share - each) < 0.01, "$rarity")
        }
    }

    @Test
    fun `a wander starts where it is stays on the map and has no edge entry`() {
        val attraction = Butterflies.Attraction(GridPoint(24, 24))
        val start = Fireflies.TilePosition(12.5, 30.5)
        val generator = SeededRandom(6)
        repeat(100) {
            val wander = Butterflies.wander(start, listOf(attraction), generator)
            assertTrue(wander.first() == start && wander.size in 3..Butterflies.LONGEST_FLIGHT)
            assertTrue(wander.all(::onTheMap))
            assertTrue(wander.count { it.row == 24.5 && it.col == 24.5 } >= 1)
        }
        val bare = Butterflies.wander(start, emptyList(), generator)
        assertTrue(bare.first() == start && bare.size >= 3)
    }

    @Test
    fun `a resting spot is the nearest landing place or where it already is`() {
        val near = Butterflies.Attraction(GridPoint(10, 10))
        val far = Butterflies.Attraction(GridPoint(40, 40))
        val here = Fireflies.TilePosition(12.0, 12.0)
        assertEquals(
            Fireflies.TilePosition.middleOf(GridPoint(10, 10)),
            Butterflies.restingSpot(here, listOf(far, near)),
        )
        assertEquals(here, Butterflies.restingSpot(here, emptyList()))
    }
}
