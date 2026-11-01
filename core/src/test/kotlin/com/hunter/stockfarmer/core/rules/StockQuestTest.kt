package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.MarketQuest
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.QuestObjective
import com.hunter.stockfarmer.core.model.StockQuote
import com.hunter.stockfarmer.core.model.StockTrade
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class StockQuestTest {
    private val stand = GridPoint(10, 10)
    private val now = Instant.ofEpochSecond(1_700_000_000)

    private fun engine(
        level: Int = 12,
        large: Boolean = false,
        money: Double = 100_000.0,
    ): GameEngine {
        val state = stateWith(money = money, xp = LevelRules.xpToReach(level), logs = 0)
        state.putBuilding(stand, if (large) "marketStandLarge" else "marketStand")
        return GameEngine(state)
    }

    private fun quote(
        cents: Long,
        ticker: String = "AAPL",
    ) = StockQuote(ticker, cents, now)

    private fun stockQuest(engine: GameEngine): MarketQuest? =
        engine.quests(stand).firstOrNull { q -> q.objectives.any { it.goal.isStock } }

    private fun setBoard(
        engine: GameEngine,
        goal: QuestGoal,
        target: Int,
    ) {
        val quest =
            MarketQuest(
                objectives = listOf(QuestObjective(goal, target)),
                rewardXP = 100,
                rewardLevel = engine.state.level,
            )
        engine.state.marketQuests[engine.state.buildings[stand]!!] = listOf(quest)
    }

    // Ledger

    @Test
    fun `every trade is recorded with its profit`() {
        val engine = engine()
        engine.buyShares(quote(1_000), 4, now = now)
        engine.sellShares(quote(1_250), 3, now = now)
        engine.sellShares(quote(900), 1, now = now)
        val trades = engine.state.stockTrades
        assertEquals(listOf(true, false, false), trades.map { it.isBuy })
        assertEquals(listOf(0L, 750L, -100L), trades.map { it.profitCents })
        assertEquals(StockTrade("AAPL", false, 3, 1_250, now, 750), trades[1])
        assertEquals(650L, engine.state.lifetimeStockProfitCents)
        assertEquals(6.5, engine.lifetimeStockProfit)
    }

    @Test
    fun `refused trades leave no record`() {
        val engine = engine(money = 1.0)
        engine.buyShares(quote(1_000), 1, now = now)
        engine.sellShares(quote(1_000), 1, now = now)
        assertTrue(engine.state.stockTrades.isEmpty())
    }

    @Test
    fun `the ledger keeps the newest trades but lifetime profit counts all`() {
        val engine = engine(money = 1_000_000.0)
        val rounds = StockMarketRules.MAX_TRADES_KEPT / 2 + 5
        repeat(rounds) {
            engine.buyShares(quote(100), 1, now = now)
            engine.sellShares(quote(101), 1, now = now)
        }
        assertEquals(StockMarketRules.MAX_TRADES_KEPT, engine.state.stockTrades.size)
        assertEquals(rounds.toLong(), engine.state.lifetimeStockProfitCents)
    }

    @Test
    fun `the ledger survives a save`() {
        val engine = engine()
        engine.buyShares(quote(1_000), 2, now = now)
        engine.sellShares(quote(1_100), 1, now = now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.stockTrades, loaded.stockTrades)
        assertEquals(100L, loaded.lifetimeStockProfitCents)
        val old = GameStateJson.decode("""{"money": 1}""")
        assertTrue(old.stockTrades.isEmpty() && old.lifetimeStockProfitCents == 0L)
    }

    // Quests

    @Test
    fun `profit quests count whole dollars of profit from sales`() {
        val engine = engine()
        setBoard(engine, QuestGoal.StockProfit, 10)
        engine.buyShares(quote(1_000), 10, now = now)
        engine.sellShares(quote(1_150), 1, now = now)
        assertEquals(1, engine.quests(stand)[0].objectives[0].progress)
        engine.sellShares(quote(1_150), 1, now = now)
        assertEquals(3, engine.quests(stand)[0].objectives[0].progress)
        engine.sellShares(quote(500), 1, now = now)
        assertEquals(3, engine.quests(stand)[0].objectives[0].progress)
    }

    @Test
    fun `a retired portfolio value quest becomes a profit quest`() {
        val engine = engine()
        val retired =
            MarketQuest(
                objectives = listOf(QuestObjective(QuestGoal.StockValue, 500, 100)),
                rewardXP = 80,
                rewardLevel = 12,
            )
        engine.state.marketQuests["marketStand"] = listOf(retired)
        engine.prepareQuests(stand, SeededRandom(2))
        val quests = engine.quests(stand)
        assertEquals(1, quests.size)
        assertNotEquals(retired.id, quests[0].id)
        assertEquals(QuestGoal.StockProfit, quests[0].objectives[0].goal)
    }

    @Test
    fun `every board carries one stock quest from the house level`() {
        for (large in listOf(false, true)) {
            val engine = engine(large = large)
            engine.prepareQuests(stand, SeededRandom(3))
            val quest = stockQuest(engine)
            assertNotNull(quest)
            assertEquals(1, quest?.objectives?.size)
            assertEquals(1, engine.quests(stand).count { q -> q.objectives.any { it.goal.isStock } })
        }
    }

    @Test
    fun `no stock quest before the house can be built`() {
        val engine = engine(level = QuestRules.stockQuestLevel - 1)
        engine.prepareQuests(stand, SeededRandom(3))
        assertNull(stockQuest(engine))
        assertEquals(4, engine.quests(stand).size)
    }

    @Test
    fun `an existing board gains one stock quest`() {
        val engine = engine()
        val old =
            MarketQuest(
                objectives = listOf(QuestObjective(QuestGoal.PlantTrees, 3, 1)),
                rewardXP = 50,
                rewardLevel = 12,
            )
        engine.state.marketQuests["marketStand"] = listOf(old)
        val random = SeededRandom(3)
        engine.prepareQuests(stand, random)
        assertEquals(2, engine.quests(stand).size)
        assertEquals(old, engine.quests(stand)[0])
        assertNotNull(stockQuest(engine))
        engine.prepareQuests(stand, random)
        assertEquals(2, engine.quests(stand).size)
    }

    @Test
    fun `a full board without one gives up its last unfinished quest`() {
        val engine = engine()
        val quests =
            List(4) {
                MarketQuest(
                    objectives = listOf(QuestObjective(QuestGoal.PlantTrees, 3)),
                    rewardXP = 50,
                    rewardLevel = 12,
                )
            }
        engine.state.marketQuests["marketStand"] = quests
        engine.prepareQuests(stand, SeededRandom(3))
        assertEquals(quests.take(3), engine.quests(stand).take(3))
        assertTrue(
            engine
                .quests(stand)[3]
                .objectives[0]
                .goal.isStock,
        )
    }

    @Test
    fun `claiming or replacing a stock quest brings another profit quest`() {
        val engine = engine()
        val random = SeededRandom(9)
        engine.prepareQuests(stand, random)
        repeat(20) {
            val before = stockQuest(engine)!!
            engine.replaceQuest(before.id, stand, random)
            val after = stockQuest(engine)!!
            assertNotEquals(before.id, after.id)
            assertEquals(QuestGoal.StockProfit, after.objectives[0].goal)
        }
        val quest = stockQuest(engine)!!
        engine.recordQuestProgress(QuestGoal.StockProfit, quest.objectives[0].target)
        assertTrue(engine.claimQuest(quest.id, stand, random).any { it is GameEvent.QuestCompleted })
        assertNotNull(stockQuest(engine))
        assertEquals(4, engine.quests(stand).size)
    }

    @Test
    fun `ordinary replacements never pick stock goals`() {
        val engine = engine()
        val random = SeededRandom(5)
        engine.prepareQuests(stand, random)
        repeat(60) {
            val quest = engine.quests(stand).first { !it.objectives[0].goal.isStock }
            engine.replaceQuest(quest.id, stand, random)
            assertEquals(1, engine.quests(stand).count { q -> q.objectives.any { it.goal.isStock } })
        }
    }

    @Test
    fun `profit targets stay modest for the level`() {
        for (level in QuestRules.stockQuestLevel..LevelRules.maxLevel) {
            for (large in listOf(false, true)) {
                val target = QuestRules.stockProfitTarget(level, large)
                assertTrue(target >= 1 && target.toDouble() <= QuestRules.stockReferenceDollars(level) * 0.01)
            }
        }
        assertTrue(QuestRules.stockProfitTarget(12, true) > QuestRules.stockProfitTarget(12, false))
    }

    @Test
    fun `stock quests are saved and their titles name the market`() {
        assertEquals("Earn $25 profit in the Stock Market", QuestGoal.StockProfit.title(25))
        val engine = engine()
        engine.prepareQuests(stand, SeededRandom(3))
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.marketQuests, loaded.marketQuests)
    }
}
