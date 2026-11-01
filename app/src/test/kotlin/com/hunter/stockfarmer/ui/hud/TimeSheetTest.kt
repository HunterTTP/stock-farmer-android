package com.hunter.stockfarmer.ui.hud

import com.hunter.stockfarmer.audio.NightChime
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.rules.minuteOfDay
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.GameTick
import com.hunter.stockfarmer.game.WildGrowthDriver
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Random

class TimeSheetTest {
    private val midnight = Instant.ofEpochSecond(1_440_000)

    private fun random() = RandomSource(Random(42)::nextLong)

    @Test fun `manual time changes stop chime retries and only Midnight starts another day`() =
        runBlocking {
            val engine = GameEngine()
            val session = GameSession(engine)
            val tick = GameTick(session, random())
            var attempts = 0
            var stopped = 0
            val chime =
                NightChime {
                    attempts++
                    false
                }
            session.onNightEnded = { stopped++ }
            val listener = launch(start = CoroutineStart.UNDISPATCHED) { session.dayBegins.collect(chime::begin) }
            try {
                tick.tick(midnight.minusSeconds(1))
                tick.tick(midnight.plusSeconds(1))
                yield()
                val night = NightPulse.day(midnight, 0.0)
                assertTrue(chime.hushes(night))
                assertEquals(night, session.nightPulse)
                assertEquals(1, attempts)
                val day = engine.state.day

                for (preset in TimePreset.entries.filter { it != TimePreset.Midnight }) {
                    session.setTime(preset, midnight.plusSeconds(2))
                    assertNull(session.nightPulse)
                    assertEquals(preset.minute, engine.minuteOfDay(midnight.plusSeconds(2)), 1e-6)
                    yield()
                    assertFalse(chime.hushes(night))
                    chime.retry(2.0)
                    assertEquals(1, attempts)
                }
                assertEquals(5, stopped)
                tick.tick(midnight.plusSeconds(3))
                assertEquals(day, engine.state.day)

                repeat(2) { index ->
                    val now = midnight.plusSeconds(4L + index * 2)
                    session.setTime(TimePreset.Midnight, now)
                    assertNull(session.nightPulse)
                    yield()
                    assertFalse(chime.hushes(night))
                    tick.tick(now.plusSeconds(1))
                    yield()
                    assertEquals(day + index + 1, engine.state.day)
                    assertEquals(night, session.nightPulse)
                    assertTrue(chime.hushes(night))
                    assertEquals(index + 2, attempts)
                }
                assertEquals(7, stopped)
            } finally {
                listener.cancel()
            }
        }

    @Test fun `changing time finishes the old wave's remaining growth before moving its clock`() {
        val now = midnight.plusSeconds(1)
        val engine = GameEngine()
        val expected = engine.copy()
        val session = GameSession(engine)
        val night = NightPulse.day(now, 0.0)
        session.wildGrowth.begin(engine, night, 25.0, 25.0, random())
        session.beginDay(night)
        val expectedGrowth = WildGrowthDriver()
        expectedGrowth.begin(expected, night, 25.0, 25.0, random())
        expectedGrowth.advance(expected, now, finishing = true)
        assertTrue(expected.state.trees.isNotEmpty())
        session.onNightEnded = { assertEquals(1.0, engine.minuteOfDay(now), 1e-6) }

        session.setTime(TimePreset.Morning, now)

        assertEquals(expected.state.trees, engine.state.trees)
        assertEquals(expected.state.rocks, engine.state.rocks)
        assertNull(session.nightPulse)
        assertTrue(session.wildGrowth.drain().isNotEmpty())
        session.wildGrowth.advance(engine, now.plusSeconds(1), finishing = true)
        assertTrue(session.wildGrowth.drain().isEmpty())
    }

    @Test
    fun `the presets set the expected times`() {
        assertEquals(listOf(1.0, 360.0, 720.0, 840.0, 1080.0, 1320.0), TimePreset.entries.map { it.minute })
        assertEquals("12:01 AM", TimePreset.Midnight.clockText)
        assertEquals("2:00 PM", TimePreset.Rain.clockText)
    }

    @Test
    fun `the sky is bluest at noon, darkest at night and grey in the rain`() {
        val noon = TimeSky.color(12 * 60.0)
        val night = TimeSky.color(23 * 60.0)
        val rain = TimeSky.color(15 * 60.0)
        assertTrue(noon.blue > night.blue)
        assertTrue(rain.blue < noon.blue)
        assertEquals("☀️", TimeSky.symbol(12 * 60.0))
        assertEquals("🌙", TimeSky.symbol(23 * 60.0))
    }
}
