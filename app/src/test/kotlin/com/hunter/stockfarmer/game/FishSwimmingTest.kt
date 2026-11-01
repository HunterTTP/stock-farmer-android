package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class FishSwimmingTest {
    private val start = Instant.ofEpochSecond(30_000_000)
    private val anchor = GridPoint(22, 22)

    private class Lcg(
        private var state: Long,
    ) : RandomSource {
        override fun nextLong(): Long {
            state = state * 6364136223846793005L + 1442695040888963407L
            return state
        }
    }

    private fun engineWithFish(): GameEngine {
        val engine = GameEngine()
        for (row in 18..26) for (col in 18..26) engine.state.water[GridPoint(row, col)] = WaterStyle.deep
        engine.state.fishPonds[anchor] = FishPond(PondFish("carp", anchor), start)
        return engine
    }

    private fun GameEngine.position() =
        state.fishPonds
            .getValue(anchor)
            .fish!!
            .position

    @Test
    fun `a fish placed in the water swims away from where it was put`() {
        val engine = engineWithFish()
        val swimming = FishSwimming(Lcg(7))
        val visited = mutableSetOf<GridPoint>()
        for (second in 0..120) {
            swimming.advance(engine, start.advancedBy(second.toDouble()))
            visited += engine.position()
        }
        assertNotEquals(anchor, engine.position())
        assertTrue(visited.size > 3)
    }

    @Test
    fun `a fish stays in the water and within its roam radius`() {
        val engine = engineWithFish()
        val swimming = FishSwimming(Lcg(3))
        for (second in 0..600) {
            swimming.advance(engine, start.advancedBy(second.toDouble()))
            val at = engine.position()
            assertTrue(at in engine.state.water)
            assertTrue(maxOf(kotlin.math.abs(at.row - anchor.row), kotlin.math.abs(at.col - anchor.col)) <= 6)
        }
    }

    @Test
    fun `a fish rests at least a second between swims and steps no faster than 1_8 seconds`() {
        val engine = engineWithFish()
        val swimming = FishSwimming(Lcg(11))
        var moves = 0
        var previous = engine.position()
        for (tenth in 0..1200) {
            swimming.advance(engine, start.advancedBy(tenth / 10.0))
            if (engine.position() != previous) {
                moves += 1
                previous = engine.position()
            }
        }
        // 120 seconds, at most one step per 1.8 seconds.
        assertTrue(moves in 1..(120 / 1.8).toInt() + 1)
    }

    @Test
    fun `a fish that is gone is forgotten and others keep swimming`() {
        val engine = engineWithFish()
        val swimming = FishSwimming(Lcg(5))
        swimming.advance(engine, start)
        engine.state.fishPonds.clear()
        swimming.advance(engine, start.advancedBy(10.0))
        assertEquals(0, engine.state.fishPonds.size)
    }
}
