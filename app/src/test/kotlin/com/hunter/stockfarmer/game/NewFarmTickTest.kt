package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.persistence.FarmStartup
import com.hunter.stockfarmer.core.persistence.SaveSlots
import com.hunter.stockfarmer.core.rules.newGame
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

class NewFarmTickTest {
    @TempDir
    lateinit var directory: Path

    private class Lcg(
        private var state: Long,
    ) : RandomSource {
        override fun nextLong(): Long {
            state = state * 6364136223846793005L + 1442695040888963407L
            return state
        }
    }

    @Test
    @Timeout(120)
    fun `a new farm opened from its save keeps ticking through a whole farm day`() {
        val slots = SaveSlots(directory)
        for (seed in 1L..12L) {
            val start = Instant.ofEpochSecond(1_700_000_000L + seed * 7_919L)
            slots.create(0, "Farm", newGame(Lcg(seed), start), start)
            slots.setActive(0)
            val engine = FarmStartup(slots, start, Lcg(seed + 500)).engine
            val session = GameSession(engine)
            session.view.snapshot = ViewSnapshot(2500f, 2500f, 1f, 1080, 2340, 0.5f)
            val tick = GameTick(session, Lcg(seed + 1000))
            repeat(1500) { second -> tick.tick(start.advancedBy(second.toDouble())) }
            val fish =
                engine.state.fishPonds.values
                    .mapNotNull { it.fish?.position }
            assertTrue(fish.all { it in engine.state.water }, "seed $seed")
        }
    }
}
