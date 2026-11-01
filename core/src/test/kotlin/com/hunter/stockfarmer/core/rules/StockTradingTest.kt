package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.market.StockQuoteError
import com.hunter.stockfarmer.core.market.StockQuoteException
import com.hunter.stockfarmer.core.market.YahooChartParser
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Holding
import com.hunter.stockfarmer.core.model.MarketQuest
import com.hunter.stockfarmer.core.model.PricePoint
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.QuestObjective
import com.hunter.stockfarmer.core.model.StockQuote
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class StockTradingTest {
    private val now = Instant.ofEpochSecond(1_700_000_000)

    private fun quote(
        ticker: String = "AAPL",
        cents: Long = 15_025,
        previousClose: Long? = null,
    ) = StockQuote(ticker, cents, now, previousClose)

    private fun engine(money: Double = 1_000.0) = GameEngine(stateWith(money = money, logs = 0))

    private fun rejected(rejection: Rejection): List<GameEvent> = listOf(GameEvent.Rejected(rejection))

    @Test fun `buying takes money and adds a holding`() {
        val engine = engine()
        val events = engine.buyShares(quote(), 2, now = now)
        assertEquals(listOf<GameEvent>(GameEvent.BoughtShares("AAPL", 2, 300.5)), events)
        assertEquals(699.5, engine.state.money)
        assertEquals(Holding("AAPL", 2, 15_025, 15_025, now), engine.state.holdings["AAPL"])
    }

    @Test fun `buying more shares averages the cost to the cent`() {
        val engine = engine()
        engine.buyShares(quote(cents = 1_000), 1, now = now)
        engine.buyShares(quote(cents = 1_001), 2, now = now)
        val holding = engine.state.holdings["AAPL"]
        assertEquals(3L, holding?.shares)
        assertEquals(1_001L, holding?.averageCostCents)
        assertEquals(1_001L, holding?.lastPriceCents)
    }

    @Test fun `cents stay exact`() {
        val engine = engine(money = 1.0)
        engine.buyShares(quote(cents = 33), 3, now = now)
        assertEquals(0.01, engine.state.money)
    }

    @Test fun `buying without enough money changes nothing`() {
        val engine = engine(money = 100.0)
        val before = engine.state.copy()
        assertEquals(rejected(Rejection.NeedMoney(150.25)), engine.buyShares(quote(), 1, now = now))
        assertTrue(sameState(before, engine.state))
    }

    @Test fun `quantities must be positive whole and sane`() {
        val engine = engine()
        val before = engine.state.copy()
        val invalid = rejected(Rejection.InvalidQuantity)
        assertEquals(invalid, engine.buyShares(quote(), 0, now = now))
        assertEquals(invalid, engine.buyShares(quote(), -3, now = now))
        assertEquals(invalid, engine.buyShares(quote(cents = Long.MAX_VALUE), 2, now = now))
        assertEquals(invalid, engine.sellShares(quote(), 0, now = now))
        assertTrue(sameState(before, engine.state))
    }

    @Test fun `selling pays and reduces the holding`() {
        val engine = engine()
        engine.buyShares(quote(cents = 1_000), 4, now = now)
        val events = engine.sellShares(quote(cents = 1_250), 3, now = now)
        assertEquals(listOf<GameEvent>(GameEvent.SoldShares("AAPL", 3, 37.5)), events)
        assertEquals(997.5, engine.state.money)
        val holding = engine.state.holdings["AAPL"]
        assertEquals(1L, holding?.shares)
        assertEquals(1_000L, holding?.averageCostCents)
        assertEquals(1_000L, holding?.lastPriceCents)
    }

    @Test fun `cannot sell more than owned or what is not owned`() {
        val engine = engine()
        engine.buyShares(quote(), 1, now = now)
        val before = engine.state.copy()
        assertEquals(rejected(Rejection.NotEnoughShares(1)), engine.sellShares(quote(), 2, now = now))
        assertEquals(rejected(Rejection.NotEnoughShares(0)), engine.sellShares(quote("TSLA"), 1, now = now))
        assertTrue(sameState(before, engine.state))
    }

    @Test fun `at most five different tickers`() {
        val engine = engine()
        for (ticker in listOf("A", "B", "C", "D", "E")) engine.buyShares(quote(ticker, 100), 1, now = now)
        assertEquals(5, engine.stockSlotsUsed)
        assertEquals(rejected(Rejection.StockSlotsFull(5)), engine.buyShares(quote("F", 100), 1, now = now))
        assertNull(engine.state.holdings["F"])
        assertEquals(995.0, engine.state.money)
    }

    @Test fun `more shares of an owned ticker need no slot`() {
        val engine = engine()
        for (ticker in listOf("A", "B", "C", "D", "E")) engine.buyShares(quote(ticker, 100), 1, now = now)
        engine.buyShares(quote("C", 100), 4, now = now)
        assertEquals(5L, engine.state.holdings["C"]?.shares)
        assertEquals(5, engine.stockSlotsUsed)
    }

    @Test fun `selling everything frees the slot`() {
        val engine = engine()
        for (ticker in listOf("A", "B", "C", "D", "E")) engine.buyShares(quote(ticker, 100), 1, now = now)
        engine.sellShares(quote("B", 100), 1, now = now)
        assertNull(engine.state.holdings["B"])
        assertEquals(
            listOf<GameEvent>(GameEvent.BoughtShares("F", 1, 1.0)),
            engine.buyShares(quote("F", 100), 1, now = now),
        )
    }

    @Test fun `sale proceeds are capped at max money`() {
        val engine = engine(money = GameState.MAX_MONEY - 1)
        engine.state.holdings["AAPL"] = Holding("AAPL", 10, 100, 100, now)
        engine.sellShares(quote(cents = 10_000), 10, now = now)
        assertEquals(GameState.MAX_MONEY, engine.state.money)
    }

    @Test fun `portfolio value uses last known prices`() {
        val engine = engine(money = 10_000.0)
        engine.buyShares(quote("AAPL", 15_025), 2, now = now)
        engine.buyShares(quote("TSLA", 20_000), 1, now = now)
        engine.updatePrices(listOf(quote("AAPL", 16_000), quote("MSFT", 1)), now)
        assertEquals(520.0, engine.portfolioValue)
        assertNull(engine.state.holdings["MSFT"])
        assertEquals(listOf("AAPL", "TSLA"), engine.holdings.map { it.ticker })
    }

    @Test fun `tickers are normalized`() {
        assertEquals("AAPL", StockMarketRules.normalizedTicker(" aapl "))
        assertEquals("BRK-B", StockMarketRules.normalizedTicker("BRK-B"))
        assertNull(StockMarketRules.normalizedTicker(""))
        assertNull(StockMarketRules.normalizedTicker("A/B"))
        assertNull(StockMarketRules.normalizedTicker("TOOLONGTICKER"))
    }

    @Test fun `holdings survive a save round trip`() {
        val engine = engine()
        engine.buyShares(quote(), 3, now = now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.holdings, loaded.holdings)
        assertEquals(engine.state.money, loaded.money)
    }

    @Test fun `a save without holdings still loads`() {
        val saved = Json.parseToJsonElement(GameStateJson.encode(GameState())) as JsonObject
        val loaded = GameStateJson.decode(JsonObject(saved - "holdings").toString())
        assertTrue(loaded.holdings.isEmpty())
    }

    @Test fun `a save with too many holdings is rejected`() {
        val state = GameState()
        for (ticker in listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K")) {
            state.holdings[ticker] = Holding(ticker, 1, 1, 1, now)
        }
        val text = GameStateJson.encode(state)
        assertThrows(Exception::class.java) { GameStateJson.decode(text) }
    }

    // Live prices

    @Test fun `pulls are spaced five minutes apart`() {
        val engine = engine()
        assertFalse(engine.claimStockPull(now))
        engine.buyShares(quote(cents = 10_000), 1, now = now)
        assertTrue(engine.claimStockPull(now))
        assertFalse(engine.claimStockPull(now.plusSeconds(299)))
        assertEquals(1.0, StockMarketRules.secondsUntilPull(engine.state.lastStockPullAt, now.plusSeconds(299)))
        assertTrue(engine.claimStockPull(now.plusSeconds(300)))
    }

    @Test fun `a clock set back does not hold pulls off`() {
        assertTrue(StockMarketRules.isPullDue(now.plusSeconds(3_600), now))
    }

    @Test fun `every pull is kept in the history`() {
        val engine = engine()
        engine.buyShares(quote(cents = 10_000), 1, now = now)
        engine.updatePrices(listOf(quote(cents = 10_100)), now.plusSeconds(300))
        engine.updatePrices(listOf(quote(cents = 10_200)), now.plusSeconds(600))
        assertEquals(listOf(10_000L, 10_100L, 10_200L), engine.state.priceHistory["AAPL"]?.map { it.priceCents })
        assertEquals(
            now.plusSeconds(600),
            engine.state.priceHistory["AAPL"]
                ?.last()
                ?.at,
        )
    }

    @Test fun `history is capped at the newest points`() {
        val engine = engine()
        engine.buyShares(quote(cents = 10_000), 1, now = now)
        for (index in 0 until StockMarketRules.MAX_HISTORY_POINTS + 10) {
            engine.updatePrices(listOf(quote(cents = 10_000L + index)), now.plusSeconds(index.toLong()))
        }
        val history = engine.state.priceHistory["AAPL"]
        assertEquals(StockMarketRules.MAX_HISTORY_POINTS, history?.size)
        assertEquals(10_000L + StockMarketRules.MAX_HISTORY_POINTS + 9, history?.last()?.priceCents)
    }

    @Test fun `history goes when the last share is sold`() {
        val engine = engine()
        engine.buyShares(quote(cents = 10_000), 2, now = now)
        engine.buyShares(quote("TSLA", 10_000), 1, now = now)
        engine.sellShares(quote(cents = 10_000), 1, now = now)
        assertNotNull(engine.state.priceHistory["AAPL"])
        engine.sellShares(quote(cents = 10_000), 1, now = now)
        assertNull(engine.state.priceHistory["AAPL"])
        assertNotNull(engine.state.priceHistory["TSLA"])
    }

    @Test fun `trades at a displayed price keep the real price on the holding`() {
        val engine = engine()
        engine.buyShares(quote(cents = 10_000), 1, atCents = 10_005, now = now)
        assertEquals(899.95, engine.state.money)
        assertEquals(10_005L, engine.state.holdings["AAPL"]?.averageCostCents)
        assertEquals(10_000L, engine.state.holdings["AAPL"]?.lastPriceCents)
    }

    @Test fun `cost basis is what the shares owned cost`() {
        val engine = engine(money = 10_000.0)
        engine.buyShares(quote("AAPL", 10_000), 2, now = now)
        engine.buyShares(quote("TSLA", 5_000), 1, now = now)
        assertEquals(25_000L, engine.costBasisCents)
        engine.sellShares(quote("AAPL", 12_000), 1, now = now)
        assertEquals(15_000L, engine.costBasisCents)
        engine.sellShares(quote("AAPL", 12_000), 1, now = now)
        assertEquals(5_000L, engine.costBasisCents)
    }

    @Test fun `day change is measured from the previous close`() {
        assertEquals(0.0125, StockMarketRules.dayChange(10_125, 10_000))
        assertEquals(-0.01, StockMarketRules.dayChange(9_900, 10_000))
        assertNull(StockMarketRules.dayChange(9_900, null))
        assertNull(StockMarketRules.dayChange(9_900, 0))
    }

    @Test fun `jitter stays between the quote and point zero five percent above it`() {
        val seen = mutableSetOf<Long>()
        for (step in 0L until 2_000L) {
            val price = PriceJitter.priceCents("AAPL", 20_000, step)
            assertTrue(price in 20_000L..20_010L)
            assertEquals(price, PriceJitter.priceCents("AAPL", 20_000, step))
            seen += price
        }
        assertTrue(seen.size > 5)
    }

    @Test fun `jitter respects the percentage limit at cent boundaries`() {
        for (baseCents in listOf(1L, 100L, 1999L, 2000L, 3999L, 19999L)) {
            for (step in 0L until 200L) {
                val price = PriceJitter.priceCents("X", baseCents, step)
                assertTrue(price >= baseCents)
                assertTrue((price - baseCents) * 2000 <= baseCents)
            }
        }
    }

    @Test fun `history and pull time survive a save`() {
        val engine = engine()
        engine.buyShares(quote(cents = 10_000, previousClose = 9_900), 1, now = now)
        engine.claimStockPull(now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.priceHistory, loaded.priceHistory)
        assertEquals(now, loaded.lastStockPullAt)
        assertEquals(9_900L, loaded.holdings["AAPL"]?.previousCloseCents)
    }

    @Test fun `a save with history for a sold ticker is rejected`() {
        val state = GameState()
        state.priceHistory["AAPL"] = listOf(PricePoint(now, 1))
        val text = GameStateJson.encode(state)
        assertThrows(Exception::class.java) { GameStateJson.decode(text) }
    }

    @Test fun `a profit past the 32-bit range still counts towards a profit quest`() {
        val stand = GridPoint(10, 10)
        val engine = engine(money = 0.0)
        engine.state.putBuilding(stand, "marketStand")
        engine.state.marketQuests[engine.state.buildings[stand]!!] =
            listOf(
                MarketQuest(
                    objectives = listOf(QuestObjective(QuestGoal.StockProfit, 5)),
                    rewardXP = 1,
                    rewardLevel = 1,
                ),
            )
        engine.state.holdings["AAPL"] = Holding("AAPL", 1_000_000, 1, 1, now)
        engine.sellShares(quote(cents = 99_000_000), 1_000_000, now = now)
        assertTrue(engine.quests(stand)[0].objectives[0].isComplete)
    }

    // Yahoo chart parsing

    private val fallback = Instant.ofEpochSecond(5)

    private fun parse(json: String) = YahooChartParser.quote("AAPL", json, fallback)

    private fun assertQuoteError(
        expected: StockQuoteError,
        json: String,
    ) = assertEquals(expected, assertThrows(StockQuoteException::class.java) { parse(json) }.error)

    @Test fun `reads the price and time`() {
        val quote =
            parse(
                """{"chart":{"result":[{"meta":{"regularMarketPrice":150.255,"regularMarketTime":1700000000}}],"error":null}}""",
            )
        assertEquals(15_026L, quote.priceCents)
        assertEquals(Instant.ofEpochSecond(1_700_000_000), quote.quotedAt)
    }

    @Test fun `reads the previous close`() {
        val quote = parse("""{"chart":{"result":[{"meta":{"regularMarketPrice":10,"chartPreviousClose":9.5}}]}}""")
        assertEquals(950L, quote.previousCloseCents)
    }

    @Test fun `an unknown ticker is invalid`() {
        assertQuoteError(
            StockQuoteError.invalidTicker,
            """{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found"}}}""",
        )
    }

    @Test fun `a missing or zero price is unavailable`() {
        assertQuoteError(StockQuoteError.unavailable, """{"chart":{"result":[{"meta":{}}],"error":null}}""")
        assertQuoteError(
            StockQuoteError.unavailable,
            """{"chart":{"result":[{"meta":{"regularMarketPrice":0}}],"error":null}}""",
        )
        assertQuoteError(StockQuoteError.unavailable, "nonsense")
    }

    @Test fun `a malformed result is unavailable rather than an unknown ticker`() {
        assertQuoteError(StockQuoteError.unavailable, """{"chart":{"result":[{}]}}""")
        assertQuoteError(
            StockQuoteError.unavailable,
            """{"chart":{"result":[{"meta":{"regularMarketPrice":"150"}}]}}""",
        )
    }

    @Test fun `a missing time falls back to now`() {
        assertEquals(fallback, parse("""{"chart":{"result":[{"meta":{"regularMarketPrice":1}}]}}""").quotedAt)
    }
}
