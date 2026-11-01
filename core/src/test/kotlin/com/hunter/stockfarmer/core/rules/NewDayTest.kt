package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

class NewDayTest {
    private val night = 1_001
    private val midnight get() = Instant.ofEpochSecond((night * DayNightCycle.secondsPerDay).toLong())

    @Test
    fun `a new day begins once in the midnight wave and not again that night`() {
        val engine = GameEngine()
        val day = engine.state.day
        assertNotNull(engine.beginNewDayIfDue(midnight.advancedBy(1.0)))
        assertEquals(day + 1, engine.state.day)
        assertNull(engine.beginNewDayIfDue(midnight.advancedBy(2.0)))
        assertEquals(day + 1, engine.state.day)
    }

    @Test
    fun `no day begins outside the wave`() {
        val engine = GameEngine()
        assertNull(engine.beginNewDayIfDue(midnight.advancedBy(-1.0)))
        assertNull(engine.beginNewDayIfDue(midnight.advancedBy(NightPulse.LENGTH_MINUTES)))
        assertEquals(1, engine.state.day)
    }

    @Test
    fun `the next night begins its own day`() {
        val engine = GameEngine()
        engine.beginNewDayIfDue(midnight.advancedBy(1.0))
        assertNotNull(engine.beginNewDayIfDue(midnight.advancedBy(DayNightCycle.secondsPerDay + 1)))
        assertEquals(3, engine.state.day)
    }

    @Test
    fun `leaving and coming back inside the wave does not begin the day again`() {
        val engine = GameEngine()
        val left = midnight.advancedBy(3 * DayNightCycle.secondsPerDay / DayNightCycle.minutesPerDay)
        engine.beginNewDayIfDue(left)
        val day = engine.state.day
        val restored = GameStateJson.decode(GameStateJson.encode(engine.state))
        restored.lastSavedAt = left
        val reopened = GameEngine(restored)
        val returned = left.advancedBy(3_600.0)
        reopened.reopen(returned, SeededRandom(1))
        assertNull(reopened.beginNewDayIfDue(returned))
        assertEquals(day, reopened.state.day)
    }

    @Test
    fun `the clock being wound back into the wave does not begin the day again`() {
        val engine = GameEngine()
        val left = midnight.advancedBy(3 * DayNightCycle.secondsPerDay / DayNightCycle.minutesPerDay)
        engine.beginNewDayIfDue(left)
        val day = engine.state.day
        val returned = left.advancedBy(600.0)
        assertNull(engine.beginNewDayIfDue(returned))
        engine.holdClockWhileAway(left, returned)
        assertNull(engine.beginNewDayIfDue(returned))
        assertEquals(day, engine.state.day)
    }

    @Test
    fun `a game opened in the middle of the wave does not begin the day`() {
        val state =
            com.hunter.stockfarmer.core.model
                .GameState()
        state.lastSavedAt = midnight.advancedBy(5.0)
        val engine = GameEngine(state)
        engine.reopen(midnight.advancedBy(5.0), SeededRandom(1))
        assertNull(engine.beginNewDayIfDue(midnight.advancedBy(6.0)))
        assertEquals(1, engine.state.day)
    }

    @Test
    fun `the last night begun is saved`() {
        val engine = GameEngine()
        engine.beginNewDayIfDue(midnight.advancedBy(1.0))
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(night, loaded.lastNewDayNight)
        assertNull(GameStateJson.decode("{}").lastNewDayNight)
    }

    @Test
    fun `the midnight preset sets one minute past midnight and begins the new day once`() {
        val now = Instant.ofEpochSecond((100 * DayNightCycle.secondsPerDay + 14 * 3_600).toLong())
        for (began in listOf(false, true)) {
            val engine = GameEngine()
            engine.state.clockOffset = 5.0 * 3_600
            engine.setTimeOfDay(12.0 * 60, now)
            if (began) engine.skipNewDayInProgress(now.advancedBy(1.0))
            val day = engine.state.day
            engine.setTimeToMidnight(now)
            assertTrue(abs(engine.minuteOfDay(now) - 1) < 1e-6)
            assertEquals("12:01 AM", DayNightCycle.clockText(engine.minuteOfDay(now)))
            assertTrue(NightPulse.isChimeDue(engine.minuteOfDay(now)))
            assertNotNull(NightPulse.wave(now, engine.state.clockOffset))
            assertNotNull(engine.beginNewDayIfDue(now))
            assertEquals(day + 1, engine.state.day)
            assertNull(engine.beginNewDayIfDue(now.advancedBy(5.0)))
            // Pressing it again replays the night.
            engine.setTimeOfDay(12.0 * 60, now)
            engine.setTimeToMidnight(now)
            assertNotNull(engine.beginNewDayIfDue(now))
            assertEquals(day + 2, engine.state.day)
        }
    }
}
