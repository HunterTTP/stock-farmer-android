package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.MarketQuest
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.QuestObjective
import com.hunter.stockfarmer.core.model.StockQuote
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.buyShares
import com.hunter.stockfarmer.core.rules.sellShares
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.game.GameSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Random
import java.util.UUID

class ScreenActionsTest {
    @Test
    fun `a stock sale turns in its completed quest in the same session action`() {
        val now = Instant.ofEpochSecond(30_000_000)
        val engine =
            GameEngine(
                GameState().apply {
                    money = 1000.0
                    xp = LevelRules.xpToReach(19)
                },
            )
        val beforeXP = engine.state.xp
        engine.state.putBuilding(GridPoint(10, 10), "marketStand", null)
        val quest = MarketQuest(UUID(0, 1), listOf(QuestObjective(QuestGoal.StockProfit, 1)), 50)
        engine.state.marketQuests["marketStand"] = listOf(quest)
        engine.buyShares(StockQuote("A", 1000, now), 1, now = now)
        val random = Random(42)
        val events =
            GameSession(engine).screenAction(RandomSource(random::nextLong)) {
                it.sellShares(StockQuote("A", 1100, now), 1, now = now)
            }
        assertTrue(events.first() is GameEvent.SoldShares)
        assertEquals(listOf(QuestReward(quest.summary, 50)), QuestReward.from(events))
        assertEquals(beforeXP + 50, engine.state.xp)
        assertEquals(1, engine.state.completedMarketQuests)
        assertTrue(engine.state.holdings.isEmpty())
    }
}
