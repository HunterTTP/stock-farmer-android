package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.rules.NightPulse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class NightPulsePlaybackTest {
    @Test fun `a loaded midnight stays silent and a repeated Midnight preset starts at the new camera`() {
        val state = GameState()
        val now = Instant.ofEpochSecond(1_440_003)
        val night = NightPulse.day(now, 0.0)
        val motion = PulseMotion()
        val firstCamera = Point(10.0, 12.0)
        val secondCamera = Point(35.0, 38.0)

        motion.sync(null, 0)
        assertEquals(0, motion.tiles(state, now, firstCamera).size)
        motion.sync(night, 1)
        assertTrue(motion.tiles(state, now, firstCamera).size > 0)
        assertEquals(firstCamera, motion.origin)
        motion.sync(night, 1)
        motion.tiles(state, now, secondCamera)
        assertEquals(firstCamera, motion.origin)
        motion.sync(null, 2)
        assertEquals(0, motion.tiles(state, now, secondCamera).size)
        motion.sync(night, 3)
        assertTrue(motion.tiles(state, now, secondCamera).size > 0)
        assertEquals(secondCamera, motion.origin)
    }
}
