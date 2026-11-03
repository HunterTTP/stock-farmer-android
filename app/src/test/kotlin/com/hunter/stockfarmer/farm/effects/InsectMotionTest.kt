package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.catalog.Rarity
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.ReleasedButterfly
import com.hunter.stockfarmer.core.rules.Butterflies
import com.hunter.stockfarmer.core.rules.Fireflies.TilePosition
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random
import java.util.UUID

internal fun seeded(seed: Long): RandomSource {
    val generator = Random(seed)
    return RandomSource { generator.nextLong() }
}

internal class InsectMotionTest {
    private val flight = listOf(TilePosition(10.0, 10.0), TilePosition(10.0, 11.0), TilePosition(10.0, 12.0))
    private val wings = Butterflies.Wings.Color(Rarity.rare)

    @Test
    fun `flight duration follows tile distance and smooth drift has a fixed endpoint`() {
        val path = FlightPath(flight, 0.5)
        assertEquals(4.0, path.lifespan)
        assertEquals(TilePosition(10.0, 10.5), path.position(1.0))
        assertEquals(flight.last(), path.position(100.0, eased = true))
        assertEquals(flight.first(), path.position(-10.0))
        assertEquals(flight.first(), FlightPath(listOf(flight.first(), flight.first()), 1.0).position(0.0))
    }

    @Test
    fun `firefly pixels fade faster than halos and both vanish at the end`() {
        val fly = FireflyMotion(flight, seeded(3))
        assertEquals(0.0, fly.pixelAlpha)
        assertEquals(0.0, fly.haloAlpha)
        fly.advance(0.8)
        assertEquals(1.0, fly.pixelAlpha)
        assertTrue(fly.haloAlpha < 0.25)
        fly.advance(100.0)
        assertTrue(fly.done)
        assertEquals(0.0, fly.pixelAlpha)
        assertEquals(0.0, fly.haloAlpha)
    }

    @Test
    fun `fireflies fade with dawn and cannot appear without hideaways`() {
        val population = FireflyPopulation(seeded(42))
        val state = GameState()
        repeat(1000) { population.update(state, 0.1, 1.0) }
        assertTrue(population.flies.isEmpty())
        assertEquals(0.0, population.visibility(0.25))
        assertEquals(0.5, population.visibility(0.425), 1e-9)
        assertEquals(1.0, population.visibility(0.6))
        state.rocks[GridPoint(1, 1)] = com.hunter.stockfarmer.core.catalog.RockSize.big
        state.rocks[GridPoint(24, 24)] = com.hunter.stockfarmer.core.catalog.RockSize.big
        repeat(1000) {
            population.update(state, 0.1, 1.0)
            assertTrue(population.flies.size <= 8)
        }
        assertTrue(population.flies.isNotEmpty())
        population.update(state, 0.1, 0.0)
        assertTrue(population.flies.isEmpty())
    }

    @Test
    fun `butterfly stops exactly on a landing until its rest finishes`() {
        val fly = ButterflyMotion(flight, listOf(flight[1]), wings, null, seeded(3))
        fly.advance(1.0, true)
        val arrival = fly.travel
        val point = fly.point
        fly.advance(1.0, true)
        assertEquals(arrival, fly.travel)
        assertEquals(point, fly.point)
        fly.advance(5.0, true)
        fly.advance(0.1, true)
        assertTrue(fly.travel > arrival)
    }

    @Test
    fun `wild butterflies fade in bad weather but released ones settle and return`() {
        val wild = ButterflyMotion(flight, emptyList(), wings, null, seeded(3))
        wild.advance(0.9, false)
        assertTrue(wild.done)
        val released = ButterflyMotion(flight, emptyList(), wings, UUID(1, 2), seeded(3))
        val attractions = listOf(Butterflies.Attraction(GridPoint(10, 11)))
        released.guide(false, { attractions }, { emptyList() })
        repeat(100) { released.advance(0.1, false) }
        val settled = released.point
        repeat(100) { released.advance(0.1, false) }
        assertFalse(released.done)
        assertEquals(settled.x, released.point.x, 1e-6)
        assertEquals(settled.y, released.point.y, 1e-6)
        released.guide(true, { attractions }, { emptyList() })
        released.advance(1.0, true)
        assertTrue(released.point != settled)
    }

    @Test
    fun `released butterflies sync identity and catching removes only the caught one`() {
        val state = GameState().apply { season = Season.winter }
        val id = UUID(1, 2)
        state.releasedButterflies = listOf(ReleasedButterfly(id, wings, GridPoint(4, 4)))
        val population = ButterflyPopulation(seeded(10))
        repeat(5) { population.update(state, 0.1, 1.0, 1.0) }
        assertEquals(listOf(id), population.flies.map { it.releasedID })
        state.releasedButterflies = emptyList()
        population.update(state, 0.1, 1.0, 1.0)
        assertTrue(population.flies.isEmpty())
    }

    @Test
    fun `wild population respects the core cap and clears in winter`() {
        val state = GameState()
        val population = ButterflyPopulation(seeded(20))
        repeat(5000) {
            population.update(state, 0.1, 0.0, 0.0)
            assertTrue(population.flies.size <= Butterflies.MAX_AT_ONCE)
        }
        state.season = Season.winter
        repeat(10) { population.update(state, 0.1, 0.0, 0.0) }
        assertTrue(population.flies.isEmpty())
    }

    private class CountingRandom(
        seed: Long,
    ) : RandomSource {
        private val generator = Random(seed)
        var draws = 0
            private set

        override fun nextLong(): Long {
            draws++
            return generator.nextLong()
        }
    }

    private fun busyFarm() =
        GameState().apply {
            for (index in 0 until 40) rocks[GridPoint(2 + index % 20, 3 + index * 7 % 19)] = RockSize.big
            releasedButterflies = listOf(ReleasedButterfly(UUID(7, 7), wings, GridPoint(8, 8)))
        }

    private fun darknessAt(step: Int) = if (step / 300 % 2 == 0) 0.0 else 1.0

    @Test
    fun `attractions are built only when a butterfly needs them and the result is unchanged`() {
        val state = busyFarm()
        val lazyRandom = CountingRandom(31)
        val eagerRandom = CountingRandom(31)
        var built = 0
        val lazyPopulation =
            ButterflyPopulation(lazyRandom) { s ->
                built++
                Butterflies.attractions(s)
            }
        val eager = Butterflies.attractions(state)
        val eagerPopulation = ButterflyPopulation(eagerRandom) { eager }
        val updates = 3000
        for (step in 0 until updates) {
            val darkness = darknessAt(step)
            lazyPopulation.update(state, 0.1, darkness, 0.0)
            eagerPopulation.update(state, 0.1, darkness, 0.0)
            assertEquals(eagerPopulation.flies.size, lazyPopulation.flies.size)
            for ((a, b) in eagerPopulation.flies.zip(lazyPopulation.flies)) {
                assertEquals(a.point, b.point)
                assertEquals(a.age, b.age)
                assertEquals(a.releasedID, b.releasedID)
            }
        }
        assertEquals(eagerRandom.draws, lazyRandom.draws)
        assertTrue(built > 0)
        assertTrue(built < updates / 10, "built $built times in $updates updates")
    }

    @Test
    fun `a quiet frame builds nothing and draws only the spawn roll`() {
        val state = GameState().apply { season = Season.winter }
        val random = CountingRandom(5)
        var built = 0
        val population =
            ButterflyPopulation(random) {
                built++
                emptyList()
            }
        repeat(100) { population.update(state, 0.1, 0.0, 0.0) }
        assertEquals(0, built)
        assertEquals(100, random.draws)
    }
}
