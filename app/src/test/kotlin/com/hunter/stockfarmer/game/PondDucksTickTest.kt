package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.newGame
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PondDucksTickTest {
    private val start = Instant.ofEpochSecond(30_000_000)

    private class Lcg(
        private var state: Long,
    ) : RandomSource {
        override fun nextLong(): Long {
            state = state * 6364136223846793005L + 1442695040888963407L
            return state
        }
    }

    @Test
    fun `new maps put grown ducks on the water for most seeds`() {
        val withDucks =
            (1L..20L).count { seed ->
                val state = newGame(Lcg(seed), start)
                val ducks = state.herds.values.flatMap { it.animals }
                ducks.all { it.position in state.water } && ducks.isNotEmpty()
            }
        assertTrue(withDucks >= 15, "maps with ducks: $withDucks")
    }

    @Test
    fun `the first duckling of a pond appears on the water after ten minutes of ticks`() {
        val engine = GameEngine()
        for (row in 19..21) for (col in 19..21) engine.state.water[GridPoint(row, col)] = WaterStyle.deep
        val tick = GameTick(GameSession(engine), Lcg(2))
        // The first tick only sets the clock; the pond's herd is settled by the second.
        tick.tick(start)
        tick.tick(start.advancedBy(1.0))
        assertEquals(
            0,
            engine.state.herds.values
                .sumOf { it.animals.size },
        )
        tick.tick(start.advancedBy(602.0))
        val ducks =
            engine.state.herds.values
                .flatMap { it.animals }
        assertEquals(1, ducks.size)
        assertTrue(ducks.single().position in engine.state.water)
    }

    @Test
    fun `ducks keep to the water as the tick walks them`() {
        val state = newGame(Lcg(9), start)
        val engine = GameEngine(state)
        val random = Lcg(4)
        repeat(200) { second -> walkAnimals(engine, start.advancedBy(second.toDouble()), random) }
        val ducks =
            engine.state.herds.values
                .flatMap { it.animals }
        assertTrue(ducks.isNotEmpty())
        assertTrue(ducks.all { it.position in engine.state.water })
    }
}
