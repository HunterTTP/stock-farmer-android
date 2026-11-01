package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Fertilizer
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ToolStrokeTest {
    private val now = Instant.ofEpochSecond(16_000_000)
    private val barren = GridPoint(3, 3)
    private val field = GameState.startingFarmland.sortedWith(compareBy({ it.row }, { it.col }))

    private fun rejections(events: List<GameEvent>) = events.filterIsInstance<GameEvent.Rejected>().map { it.rejection }

    @Test
    fun `tiles that cant take the tool are passed over without a word`() {
        val engine = GameEngine(stateWith(money = 10.0))
        val events = ToolStroke().use(engine, listOf(barren) + field, now)
        assertTrue(rejections(events).isEmpty())
        assertEquals(field.size, engine.state.plots.size)
        assertEquals(field.size, events.size)
    }

    @Test
    fun `a real problem is reported once for the whole stroke`() {
        val engine = GameEngine(stateWith(money = Fertilizer.price, mode = PlayMode.fertilize))
        engine.selectFertilizer()
        val stroke = ToolStroke()
        val first = stroke.use(engine, field, now)
        assertEquals(listOf<Rejection>(Rejection.NeedMoney(Fertilizer.price)), rejections(first))
        assertEquals(1, engine.state.fertilized.size)
        val more = stroke.use(engine, listOf(GridPoint(24, 23)), now)
        assertTrue(rejections(more).isEmpty())
    }

    @Test
    fun `a new stroke reports the problem again`() {
        val engine = GameEngine(stateWith(money = 0.0, mode = PlayMode.fertilize))
        engine.selectFertilizer()
        val expected = listOf<Rejection>(Rejection.NeedMoney(Fertilizer.price))
        assertEquals(expected, rejections(ToolStroke().use(engine, field, now)))
        assertEquals(expected, rejections(ToolStroke().use(engine, field, now)))
    }

    @Test
    fun `reaching the farmland limit mid stroke is reported once`() {
        val engine = GameEngine(stateWith(mode = PlayMode.landscape))
        engine.select(LandscapeTool.farmland)
        val bare = (0 until 5).map { GridPoint(40, 10 + it) }
        val events = ToolStroke().use(engine, bare, now)
        assertEquals(
            listOf<Rejection>(Rejection.FarmlandLimit(engine.state.farmland.size, engine.state.farmlandLimit)),
            rejections(events),
        )
        assertEquals(engine.state.farmlandLimit, engine.state.farmland.size)
    }

    @Test
    fun `the axe counts one pass per stroke and the next stroke is another`() {
        val engine = GameEngine()
        engine.select(HarvestTool.axe)
        val kind = Tree.named("oak-huge") ?: Tree.starter
        val origin = kind.origin(GridPoint(10, 10))
        engine.state.putTree(origin, PlacedTree(kind, kind.species.colors[0], now))
        val ground = kind.footprint(origin)

        val stroke = ToolStroke()
        val across = stroke.use(engine, ground, now)
        assertEquals(1, across.filterIsInstance<GameEvent.Chopped>().maxOf { it.hits })
        stroke.end(engine)

        val again = ToolStroke().use(engine, ground, now)
        assertEquals(2, again.filterIsInstance<GameEvent.Chopped>().maxOf { it.hits })
    }

    @Test
    fun `a stroke in build mode places nothing`() {
        val engine = GameEngine(stateWith(money = 1_000.0, xp = lateGameXP, mode = PlayMode.build))
        assertTrue(ToolStroke().use(engine, field, now).isEmpty())
        assertTrue(engine.state.buildings.isEmpty())
    }

    @Test
    fun `the first rejection is found among other events`() {
        val events =
            listOf(
                GameEvent.Destroyed(1),
                GameEvent.Rejected(Rejection.NeedGrass),
                GameEvent.Rejected(Rejection.StorageFull),
            )
        assertEquals(Rejection.NeedGrass, events.firstRejection)
        assertTrue(events.containsRejection)
        assertTrue(!listOf<GameEvent>(GameEvent.Destroyed(1)).containsRejection)
        assertNull(emptyList<GameEvent>().firstRejection)
    }
}
