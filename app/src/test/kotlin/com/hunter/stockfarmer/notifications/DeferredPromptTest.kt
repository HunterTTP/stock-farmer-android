package com.hunter.stockfarmer.notifications

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.ToolDrag
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class DeferredPromptTest {
    private var busy = false
    private var shown = 0
    private val prompt = DeferredPrompt({ busy }) { shown++ }

    @Test
    fun `an idle moment shows the prompt at once`() {
        prompt.request()
        assertEquals(1, shown)
    }

    @Test
    fun `a busy moment holds it back until idle, then shows it once`() {
        busy = true
        prompt.request()
        prompt.whenIdle()
        assertEquals(0, shown)
        busy = false
        prompt.whenIdle()
        prompt.whenIdle()
        assertEquals(1, shown)
    }

    @Test
    fun `asking again while waiting still shows it once`() {
        busy = true
        prompt.request()
        prompt.request()
        busy = false
        prompt.whenIdle()
        assertEquals(1, shown)
    }

    @Test
    fun `nothing is shown when nothing was asked`() {
        prompt.whenIdle()
        assertEquals(0, shown)
    }

    /** F1: the dialog took the touch after the first tile, so a drag over four tiles planted only one. */
    @Test
    fun `a first planting drag over four tiles plants all four before the prompt appears`() =
        runBlocking {
            val now = Instant.ofEpochSecond(100_000)
            val tiles = (0 until 4).map { GridPoint(20, 20 + it) }
            val engine = GameEngine(GameState().apply { farmland = tiles.toMutableSet() })
            val session = GameSession(engine)
            val strokePrompt = DeferredPrompt({ session.toolStrokes.value }) { shown++ }
            val planted = mutableListOf<GameEvent.Planted>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    session.events.collect { batch ->
                        planted += batch.filterIsInstance<GameEvent.Planted>()
                        if (batch.any { it is GameEvent.Planted }) strokePrompt.request()
                    }
                }
            val drag = ToolDrag(session)
            for (tile in tiles) {
                drag.move(tile, now)
                yield()
                assertEquals(0, shown)
            }
            assertEquals(4, engine.state.plots.size)
            assertEquals(4, planted.size)
            assertTrue(session.toolStrokes.value)
            drag.end()
            assertFalse(session.toolStrokes.value)
            strokePrompt.whenIdle()
            assertEquals(1, shown)
            collector.cancel()
        }
}
