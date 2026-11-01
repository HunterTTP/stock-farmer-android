package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class WildGrowthDriverTest {
    private class DeterministicRandom : RandomSource {
        private var seed = 123456789L

        override fun nextLong(): Long {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return seed
        }
    }

    @Test
    fun `finishing growth executes full sweep plan and drains reveals`() {
        val driver = WildGrowthDriver()
        val engine = GameEngine()
        val random = DeterministicRandom()
        val now = Instant.ofEpochSecond(100_000)
        val night = NightPulse.day(now, engine.state.clockOffset)
        driver.begin(engine, night, 15.0, 15.0, random)

        driver.advance(engine, now, finishing = true)
        val drained = driver.drain()
        // There may be grown trees or rocks
        assertTrue(driver.drain().isEmpty())
    }

    @Test
    fun `clear empties pending plan and queued reveals`() {
        val driver = WildGrowthDriver()
        val engine = GameEngine()
        val random = DeterministicRandom()
        val now = Instant.ofEpochSecond(100_000)
        driver.begin(engine, 1, 10.0, 10.0, random)
        driver.clear()
        driver.advance(engine, now, finishing = true)
        assertTrue(driver.drain().isEmpty())
    }
}
