package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.market.StockQuoteError
import com.hunter.stockfarmer.core.market.StockQuoteException
import com.hunter.stockfarmer.core.market.StockQuoteProvider
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.StockQuote
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.StockMarketRules
import com.hunter.stockfarmer.core.rules.buyShares
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.market.MarketPrices
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class StockMarketStateTest {
    private val now = Instant.ofEpochSecond(30_000_000)

    private fun quote(
        ticker: String = "AAPL",
        cents: Long = 1000,
    ) = StockQuote(ticker, cents, now, 900)

    @Test
    fun `holdings sort by ticker and searched stock does not inflate portfolio`() {
        val engine = GameEngine(GameState().apply { money = 1000.0 })
        engine.buyShares(quote("Z"), 2, now = now)
        engine.buyShares(quote("A"), 1, now = now)
        val prices = MarketPrices(mapOf("B" to quote("B", 500)), "B")
        val state = StockMarketState.from(engine, prices, now)
        assertEquals(listOf("A", "Z", "B"), state.rows.map { it.quote.ticker })
        assertEquals("$30", state.portfolioValue)
        assertEquals("2/5", state.slots)
        assertEquals("+0.00%", state.performance)
        engine.state.putBuilding(GridPoint(10, 10), "house2", null)
        assertEquals("2/10", StockMarketState.from(engine, prices, now).slots)
    }

    @Test
    fun `saved holding price wins over a searched quote and falls back only for previous close`() {
        val engine = GameEngine(GameState().apply { money = 1000.0 })
        engine.buyShares(quote().copy(previousCloseCents = null), 2, now = now)
        val state = StockMarketState.from(engine, MarketPrices(mapOf("AAPL" to quote(cents = 2000))), now)
        assertEquals(1000L, state.rows.single().priceCents)
        assertEquals("+11.11%", state.rows.single().dayChange)
        assertEquals("$20", state.rows.single().worth)
    }

    @Test
    fun `trade previews match real core profit without changing money holdings quests or career`() {
        val engine = GameEngine(GameState().apply { money = 1000.0 })
        engine.buyShares(quote(), 4, now = now)
        val before = engine.state.copy()
        val row = StockRowState(quote(cents = 1250), engine.state.holdings["AAPL"], 1250)
        val trade = StockTradeState.from(engine, row, false, 3, now)
        assertEquals(4L, trade.maximum)
        assertEquals("$37.50", trade.total)
        assertEquals("+$7.50", trade.profit)
        assertEquals(750L, trade.profitCents)
        assertNull(trade.rejection)
        val buyTrade = StockTradeState.from(engine, row, true, 2, now)
        assertNull(buyTrade.profit)
        assertNull(buyTrade.profitCents)
        assertEquals(before.money, engine.state.money)
        assertEquals(before.holdings, engine.state.holdings)
        assertEquals(before.marketQuests, engine.state.marketQuests)
        assertEquals(before.career.grossIncomeCents, engine.state.career.grossIncomeCents)
        assertEquals(before.stockTrades, engine.state.stockTrades)
        assertEquals(before.lifetimeStockProfitCents, engine.state.lifetimeStockProfitCents)
    }

    @Test
    fun `preview validates quantities cash share limits overflow and full slots through core`() {
        val engine = GameEngine(GameState().apply { money = 1000.0 })
        for (ticker in listOf("A", "B", "C", "D", "E")) engine.buyShares(quote(ticker), 1, now = now)
        val new = StockRowState(quote("F"), null, 1000)
        assertNotNull(StockTradeState.from(engine, new, true, 1, now).rejection)
        val owned = StockRowState(quote("A"), engine.state.holdings["A"], 1000)
        assertNull(StockTradeState.from(engine, owned, true, 1, now).rejection)
        assertEquals(95L, StockTradeState.from(engine, owned, true, 1, now).maximum)
        for (quantity in listOf(0L, -1L, Long.MAX_VALUE)) {
            assertNotNull(StockTradeState.from(engine, owned, true, quantity, now).rejection)
        }
        val expensive = new.copy(priceCents = StockMarketRules.MAX_PRICE_CENTS)
        assertNotNull(StockTradeState.from(engine, expensive, true, 100_000_000, now).rejection)
    }

    @Test
    fun `search normalizes validates caches until five minutes and refetches after clock rollback`() =
        runBlocking {
            var time = now
            val requested = mutableListOf<String>()
            val controller =
                StockMarketController(
                    GameSession(GameEngine(GameState())),
                    StockQuoteProvider {
                        requested += it
                        quote(it)
                    },
                    { time },
                )
            controller.search("bad ticker")
            assertEquals(StockQuoteError.invalidTicker.message, controller.prices.value.message)
            assertTrue(requested.isEmpty())
            controller.search(" aapl ")
            assertEquals("AAPL", controller.prices.value.selected)
            time = now.plusSeconds(299)
            controller.search("AAPL")
            assertEquals(1, requested.size)
            time = now.plusSeconds(300)
            controller.search("AAPL")
            assertEquals(2, requested.size)
            time = now
            controller.search("AAPL")
            assertEquals(3, requested.size)
            assertFalse(controller.prices.value.loading)
        }

    @Test
    fun `owned searches share pull gate and partial refresh retains saved prices for failed tickers`() =
        runBlocking {
            val engine = GameEngine(GameState().apply { money = 1000.0 })
            engine.buyShares(quote("A"), 1, now = now)
            engine.buyShares(quote("B"), 1, now = now)
            val calls = mutableListOf<String>()
            val controller =
                StockMarketController(
                    GameSession(engine),
                    StockQuoteProvider {
                        calls += it
                        if (it == "B") throw StockQuoteException(StockQuoteError.network)
                        quote(it, 2000)
                    },
                    { now },
                )
            controller.pullIfDue(true)
            assertEquals("Some prices couldn't be refreshed", controller.prices.value.message)
            assertEquals(
                2000L,
                engine.state.holdings
                    .getValue("A")
                    .lastPriceCents,
            )
            assertEquals(
                1000L,
                engine.state.holdings
                    .getValue("B")
                    .lastPriceCents,
            )
            controller.search("B")
            assertEquals(listOf("A", "B"), calls)
            assertEquals("B", controller.prices.value.selected)
        }

    @Test
    fun `cancellation clears loading and never applies a partial fetch to the farm`() =
        runBlocking {
            val engine = GameEngine(GameState().apply { money = 1000.0 })
            engine.buyShares(quote("A"), 1, now = now)
            engine.buyShares(quote("B"), 1, now = now)
            val waiting = CompletableDeferred<Unit>()
            val controller =
                StockMarketController(
                    GameSession(engine),
                    StockQuoteProvider {
                        if (it == "A") {
                            quote(it, 2000)
                        } else {
                            waiting.complete(Unit)
                            awaitCancellation()
                        }
                    },
                    { now },
                )
            val job = launch { controller.pullIfDue() }
            waiting.await()
            job.cancelAndJoin()
            assertFalse(controller.prices.value.loading)
            assertTrue(
                controller.prices.value.quotes
                    .isEmpty(),
            )
            assertEquals(
                1000L,
                engine.state.holdings
                    .getValue("A")
                    .lastPriceCents,
            )
        }
}
