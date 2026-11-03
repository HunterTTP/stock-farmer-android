package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.MarketQuest
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.QuestObjective
import com.hunter.stockfarmer.core.model.StockQuote
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.buyShares
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.core.rules.sellShares
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.ui.sheets.SheetAction
import com.hunter.stockfarmer.ui.sheets.SheetTarget
import com.hunter.stockfarmer.ui.sheets.sheetAction
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Random
import java.util.UUID

/** iOS routes every action through `commit(events)`, which turns in the quests it finished; so does `publish`. */
class GameSessionCommitTest {
    private val now = Instant.ofEpochSecond(100_000)
    private val tile = GridPoint(3, 3)
    private val stand = GridPoint(10, 10)
    private val quest = MarketQuest(UUID(0, 1), listOf(QuestObjective(QuestGoal.Harvest(Crop.starter.id), 1)), 50)
    private val engine =
        GameEngine(GameState().apply { putBuilding(stand, "marketStand", null) }).also {
            it.state.marketQuests["marketStand"] = listOf(quest)
            it.state.plots[tile] = Plot(Crop.starter.id, now.minusSeconds(10_000))
            it.select(HarvestTool.scythe)
        }
    private val random = Random(7)
    private val session = GameSession(engine, RandomSource(random::nextLong))

    private fun collecting(action: () -> Unit): List<List<GameEvent>> =
        runBlocking {
            val received = mutableListOf<List<GameEvent>>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { session.events.collect { received += it } }
            try {
                action()
                yield()
                received.toList()
            } finally {
                collector.cancel()
            }
        }

    private fun List<GameEvent>.questBanners() = filterIsInstance<GameEvent.QuestCompleted>()

    @Test
    fun `harvesting the last crop of a quest turns it in at once`() {
        var events = emptyList<GameEvent>()
        val batches = collecting { events = session.touch(tile, now) }
        assertEquals(1, events.questBanners().size)
        assertEquals(1, batches.single().questBanners().size)
        assertEquals(1, engine.state.completedMarketQuests)
    }

    @Test
    fun `dragging a tool over the last crop of a quest turns it in at once`() {
        val batches = collecting { ToolDrag(session).move(tile, now) }
        assertEquals(1, batches.single().questBanners().size)
        assertEquals(1, engine.state.completedMarketQuests)
    }

    @Test
    fun `an empty batch is free and leaves a finished quest for the next commit`() {
        engine.state.marketQuests["marketStand"] =
            listOf(MarketQuest(UUID(0, 2), listOf(QuestObjective(QuestGoal.PlantTrees, 1, progress = 1)), 50))
        val batches = collecting { assertTrue(session.publish(emptyList()).isEmpty()) }
        assertTrue(batches.isEmpty())
        assertEquals(0, engine.state.completedMarketQuests)

        val committed = collecting { session.commit() }
        assertEquals(1, committed.single().questBanners().size)
        assertEquals(1, engine.state.completedMarketQuests)
    }

    @Test
    fun `a commit with nothing finished sends nothing`() {
        assertTrue(collecting { assertTrue(session.commit().isEmpty()) }.isEmpty())
    }

    @Test
    fun `a sheet action turns in a finished quest once`() {
        engine.state.marketQuests["marketStand"] =
            listOf(MarketQuest(UUID(0, 2), listOf(QuestObjective(QuestGoal.PlantTrees, 1, progress = 1)), 50))
        val batches = collecting { session.sheetAction(SheetTarget.Fish(tile), SheetAction.Release, now) }
        assertEquals(1, batches.single().questBanners().size)
        assertEquals(1, batches.single().filterIsInstance<GameEvent.Rejected>().size)
    }

    @Test
    fun `a stock sale published from a screen turns in its quest`() {
        engine.state.money = 1000.0
        engine.state.marketQuests["marketStand"] =
            listOf(MarketQuest(UUID(0, 3), listOf(QuestObjective(QuestGoal.StockProfit, 1)), 50))
        val quote = StockQuote("A", 1000, now)
        engine.buyShares(quote, 1, now = now)
        val sold = session.act { it.sellShares(quote.copy(priceCents = 1100), 1, now = now) }
        val batches = collecting { session.publish(sold) }
        assertTrue(batches.single().first() is GameEvent.SoldShares)
        assertEquals(1, batches.single().questBanners().size)
    }

    @Test
    fun `a quest is announced once however many batches follow`() {
        val batches =
            collecting {
                session.touch(tile, now)
                session.publish(listOf(GameEvent.Placed(tile)))
                session.commit()
            }
        assertEquals(1, batches.flatten().questBanners().size)
        assertFalse(batches.any { it.isEmpty() })
    }
}
