package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class HarvestAnywhereTest {
    private val planted = Instant.ofEpochSecond(6_000_000)
    private val tile = GridPoint(24, 24)
    private val ready get() = planted.advancedBy(30.0)

    private fun engine(mode: PlayMode): GameEngine {
        val engine = GameEngine()
        engine.tap(tile, planted)
        engine.state.mode = mode
        return engine
    }

    @Test
    fun `a finished crop is not harvested without the scythe in hand`() {
        for (mode in listOf(PlayMode.landscape, PlayMode.build, PlayMode.plant, PlayMode.fertilize)) {
            val engine = engine(mode)
            assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.UseTheScythe)), engine.tap(tile, ready))
            assertNotNull(engine.state.plots[tile])
            assertTrue(engine.state.storage.isEmpty())
        }
    }

    @Test
    fun `the scythe harvests only the tapped tile`() {
        val engine = engine(PlayMode.plant)
        val neighbor = GridPoint(24, 25)
        engine.tap(neighbor, planted)
        engine.select(HarvestTool.scythe)
        val harvested = engine.tap(tile, ready).filterIsInstance<GameEvent.Stored>()
        assertEquals(listOf(GameEvent.Stored(tile, Crop.starter.id, 2)), harvested)
        assertNotNull(engine.state.plots[neighbor])
        assertNull(engine.state.plots[tile])
    }

    @Test
    fun `a growing crop still follows the selected modes rules`() {
        val engine = engine(PlayMode.landscape)
        engine.select(LandscapeTool.grass)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NotFinishedGrowing("Wheat", 25.0))),
            engine.tap(tile, planted.advancedBy(5.0)),
        )
        assertNotNull(engine.state.plots[tile])
    }

    @Test
    fun `tapping a growing crop reports it in any mode`() {
        for (mode in listOf(PlayMode.landscape, PlayMode.build, PlayMode.decorate, PlayMode.fertilize)) {
            val engine = GameEngine()
            engine.tap(tile, planted)
            engine.state.mode = mode
            val money = engine.state.money
            val events = engine.tap(tile, planted.advancedBy(1.0))
            val first = events.first()
            assertTrue(first is GameEvent.Rejected && first.rejection is Rejection.NotFinishedGrowing, "got $events")
            assertEquals(money, engine.state.money)
            assertNotNull(engine.state.plots[tile])
        }
    }
}
