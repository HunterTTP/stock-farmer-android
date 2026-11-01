package com.hunter.stockfarmer.notifications

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.wholeFieldReadyDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class FieldReadyNotifierTest {
    private val now = Instant.ofEpochSecond(9_000_000)
    private val scheduler = FakeScheduler()
    private val notifier = FieldReadyNotifier(scheduler)

    @Test
    fun `a partially planted or empty field has no reminder`() {
        for (count in 0..3) {
            notifier.onBackground(engine(count), now)
            assertNull(scheduler.delay)
        }
        val empty = engine(0)
        empty.state.farmland.clear()
        notifier.onBackground(empty, now)
        assertNull(scheduler.delay)
    }

    @Test
    fun `reminder waits for the last crop and includes fruit trees on grass`() {
        val engine = engine()
        val apple = Crop.named("apple")!!
        engine.state.plots[GridPoint(10, 10)] = Plot(apple.id, now)
        notifier.onBackground(engine, now)
        assertEquals(Duration.between(now, engine.wholeFieldReadyDate()), scheduler.delay)
        assertEquals(apple.growDuration, scheduler.delay!!.toNanos() / 1e9, 0.001)
    }

    @Test
    fun `ready fields do not schedule at or after readiness`() {
        val engine = engine()
        val ready = engine.wholeFieldReadyDate()!!
        notifier.onBackground(engine, ready)
        assertNull(scheduler.delay)
        notifier.onBackground(engine, ready.plusSeconds(1))
        assertNull(scheduler.delay)
    }

    @Test
    fun `future reminder has a minimum delay of one second`() {
        val engine = engine()
        val ready = engine.wholeFieldReadyDate()!!
        notifier.onBackground(engine, ready.minusNanos(1))
        assertEquals(Duration.ofSeconds(1), scheduler.delay)
        notifier.onBackground(engine, ready.minusMillis(1500))
        assertEquals(Duration.ofMillis(1500), scheduler.delay)
    }

    @Test
    fun `backgrounding replaces the reminder and returning cancels pending work`() {
        val engine = engine()
        notifier.onBackground(engine, now)
        val first = scheduler.delay!!
        notifier.onBackground(engine, now.plusSeconds(1))
        assertEquals(first.minusSeconds(1), scheduler.delay)
        notifier.onActive()
        assertNull(scheduler.delay)
        notifier.onBackground(engine, now)
        engine.state.plots.clear()
        notifier.onBackground(engine, now)
        assertNull(scheduler.delay)
    }

    private fun engine(count: Int = 4): GameEngine =
        GameEngine().also { engine ->
            GameState.startingFarmland.sortedWith(compareBy({ it.row }, { it.col })).take(count).forEachIndexed {
                index,
                tile,
                ->
                engine.state.plots[tile] = Plot(Crop.starter.id, now.plusSeconds(index.toLong()))
            }
        }

    private class FakeScheduler : FieldReadyScheduler {
        var delay: Duration? = null

        override fun replace(delay: Duration) {
            this.delay = delay
        }

        override fun cancel() {
            delay = null
        }
    }
}
