package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.rules.currentLesson
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.ui.hud.TimePreset
import com.hunter.stockfarmer.ui.hud.setTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Random

class GameTickTest {
    private val planted = Instant.ofEpochSecond(100)
    private val ripe = planted.advancedBy(30.0)
    private val tile = GridPoint(24, 24)

    @Test
    fun `tick advances the lesson when planted wheat becomes ripe`() {
        val engine = GameEngine()
        engine.tap(tile, planted)
        assertEquals(Lesson.grow, engine.currentLesson)

        val session = GameSession(engine)
        val tick = GameTick(session)

        // Initial tick establishes timing baseline
        tick.tick(planted)
        assertEquals(Lesson.grow, engine.currentLesson)

        // Tick before wheat has ripened stays on waiting
        tick.tick(planted.advancedBy(1.0))
        assertEquals(Lesson.grow, engine.currentLesson)

        // Tick after wheat ripens advances to picking harvest tool
        tick.tick(ripe)
        assertEquals(Lesson.pickHarvest, engine.currentLesson)
    }

    @Test
    fun `tick advances straight to harvesting if scythe was already selected when wheat ripens`() {
        val engine = GameEngine()
        engine.tap(tile, planted)
        engine.setMode(PlayMode.harvest)
        engine.select(HarvestTool.scythe)
        assertEquals(Lesson.grow, engine.currentLesson)

        val session = GameSession(engine)
        val tick = GameTick(session)

        tick.tick(planted)
        tick.tick(ripe)
        assertEquals(Lesson.harvest, engine.currentLesson)
    }

    private val midnight = Instant.ofEpochSecond(1_440_000)

    /** Collects what [flow] sends while [block] runs, giving the collector a turn after each step through `yield`. */
    private fun <T> collecting(
        flow: SharedFlow<T>,
        block: suspend (received: List<T>) -> Unit,
    ): List<T> =
        runBlocking {
            val received = mutableListOf<T>()
            val job = launch(start = CoroutineStart.UNDISPATCHED) { flow.collect { received += it } }
            block(received)
            yield()
            job.cancel()
            received
        }

    @Test
    fun `nothing happens while the app is away and the clocks start over when it returns`() {
        val engine = GameEngine()
        val session = GameSession(engine)
        val tick = GameTick(session)

        tick.tick(midnight)
        tick.tick(midnight.advancedBy(1.0))
        assertEquals(1.0, engine.state.playTime)

        session.isActive = false
        tick.tick(midnight.advancedBy(2.0))
        tick.tick(midnight.advancedBy(3.0))
        assertEquals(1.0, engine.state.playTime)

        session.isActive = true
        tick.tick(midnight.advancedBy(4.0))
        assertEquals(1.0, engine.state.playTime)
        tick.tick(midnight.advancedBy(5.0))
        assertEquals(2.0, engine.state.playTime)
    }

    @Test
    fun `timelapse pauses rules and play time and restarts step clocks when closed`() {
        val engine = GameEngine()
        engine.tap(tile, planted)
        val session = GameSession(engine)
        val tick = GameTick(session, RandomSource(Random(42)::nextLong))
        tick.tick(planted)
        tick.tick(planted.plusSeconds(1))

        session.isTimelapseViewerOpen = true
        assertTrue(tick.tick(ripe).isEmpty())
        assertTrue(tick.tick(ripe.plusSeconds(1)).isEmpty())
        assertEquals(Lesson.grow, engine.currentLesson)
        assertEquals(1.0, engine.state.playTime)

        session.isTimelapseViewerOpen = false
        tick.tick(ripe.plusSeconds(2))
        assertEquals(Lesson.grow, engine.currentLesson)
        assertEquals(1.0, engine.state.playTime)
        tick.tick(ripe.plusSeconds(3))
        assertEquals(Lesson.pickHarvest, engine.currentLesson)
        assertEquals(2.0, engine.state.playTime)
    }

    @Test
    fun `closing timelapse while inactive does not resume ticks or note weather`() {
        val engine = GameEngine()
        val session = GameSession(engine)
        val tick = GameTick(session)
        val raining = midnight.plusSeconds(900)
        tick.tick(midnight)
        session.isTimelapseViewerOpen = true
        tick.tick(raining)
        session.isActive = false
        session.isTimelapseViewerOpen = false
        tick.tick(raining.plusSeconds(1))
        assertFalse(CareerRecord.RAIN_ID in engine.state.career.seenEnvironmentIDs)
        assertEquals(0.0, engine.state.playTime)

        session.isActive = true
        tick.tick(raining.plusSeconds(2))
        assertTrue(CareerRecord.RAIN_ID in engine.state.career.seenEnvironmentIDs)
        assertEquals(0.0, engine.state.playTime)
    }

    @Test
    fun `timelapse defers a pending day summary until closed`() {
        val session = GameSession(GameEngine())
        val tick = GameTick(session, RandomSource(Random(42)::nextLong))
        tick.tick(midnight.minusSeconds(1))
        tick.tick(midnight.plusSeconds(1))
        val summaries =
            collecting(session.newDays) { received ->
                session.isTimelapseViewerOpen = true
                tick.tick(midnight.plusSeconds(10))
                yield()
                assertTrue(received.isEmpty())
                session.isTimelapseViewerOpen = false
                tick.tick(midnight.plusSeconds(11))
            }
        assertEquals(1, summaries.size)
    }

    @Test
    fun `a gap longer than five seconds is not play but a shorter one is counted whole`() {
        val engine = GameEngine()
        val tick = GameTick(GameSession(engine))

        tick.tick(midnight)
        tick.tick(midnight.advancedBy(4.0))
        assertEquals(4.0, engine.state.playTime)
        tick.tick(midnight.advancedBy(10.0))
        assertEquals(4.0, engine.state.playTime)
    }

    @Test
    fun `rain is noted as seen, snow in winter, and a dry moment is not`() {
        val engine = GameEngine()
        GameTick(GameSession(engine)).tick(midnight.advancedBy(60.0))
        assertFalse(CareerRecord.RAIN_ID in engine.state.career.seenEnvironmentIDs)

        val raining = midnight.advancedBy(900.0)
        GameTick(GameSession(engine)).tick(raining)
        assertTrue(CareerRecord.RAIN_ID in engine.state.career.seenEnvironmentIDs)
        assertFalse(CareerRecord.SNOW_ID in engine.state.career.seenEnvironmentIDs)

        val winter = GameEngine()
        winter.state.season = Season.winter
        GameTick(GameSession(winter)).tick(raining)
        assertTrue(CareerRecord.SNOW_ID in winter.state.career.seenEnvironmentIDs)
        assertFalse(CareerRecord.RAIN_ID in winter.state.career.seenEnvironmentIDs)
    }

    @Test
    fun `a new day's chime is at once and its summary follows at the summary minute`() {
        val session = GameSession(GameEngine())
        val tick = GameTick(session).apply { tick(midnight.minusSeconds(1)) }
        val minuteOne = midnight.advancedBy(1.0)

        val nights = collecting(session.dayBegins) { tick.tick(minuteOne) }
        assertEquals(listOf<Int?>(NightPulse.day(minuteOne, 0.0)), nights)

        val other = GameSession(GameEngine())
        val otherTick = GameTick(other).apply { tick(midnight.minusSeconds(1)) }
        val summaries =
            collecting(other.newDays) { received ->
                otherTick.tick(minuteOne)
                yield()
                assertTrue(received.isEmpty())
                otherTick.tick(minuteOne.advancedBy(7.0))
                yield()
                assertTrue(received.isEmpty())
                otherTick.tick(minuteOne.advancedBy(8.0))
                yield()
                assertEquals(1, received.size)
                otherTick.tick(minuteOne.advancedBy(9.0))
            }
        assertEquals(1, summaries.size)
    }

    @Test
    fun `changing the time ends the pulse but leaves an already scheduled summary as on iOS`() {
        val session = GameSession(GameEngine())
        val tick = GameTick(session, RandomSource(Random(42)::nextLong)).apply { tick(midnight.minusSeconds(1)) }
        tick.tick(midnight.plusSeconds(1))
        session.setTime(TimePreset.Morning, midnight.plusSeconds(2))

        val summaries = collecting(session.newDays) { tick.tick(midnight.plusSeconds(9)) }
        assertEquals(1, summaries.size)
    }

    @Test
    fun `reset forgets a summary that has not appeared yet`() {
        val session = GameSession(GameEngine())
        val tick = GameTick(session).apply { tick(midnight.minusSeconds(1)) }
        val minuteOne = midnight.advancedBy(1.0)
        tick.tick(minuteOne)
        tick.reset()

        val summaries =
            collecting(session.newDays) {
                tick.tick(minuteOne.advancedBy(20.0))
                tick.tick(minuteOne.advancedBy(21.0))
            }
        assertTrue(summaries.isEmpty())
    }
}
