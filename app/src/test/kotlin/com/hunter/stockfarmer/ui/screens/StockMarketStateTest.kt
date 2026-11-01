package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.market.StockQuoteError
import com.hunter.stockfarmer.core.market.StockQuoteException
import com.hunter.stockfarmer.core.market.StockQuoteProvider
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Holding
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
    @Test
    fun `buy maximum respects remaining share capacity even with ample cash`() {
        val engine = GameEngine(GameState().apply { money = 100000000000.0 })
        engine.state.holdings["AAPL"] = Holding("AAPL", StockMarketRules.MAX_SHARES - 1, 1000, 1000, now)
        val row = StockRowState(quote(), engine.state.holdings["AAPL"], 1000)
        val preview = StockTradeState.from(engine, row, true, 1, now)
        assertEquals(1L, preview.maximum)
        assertNull(preview.rejection)
        assertNotNull(StockTradeState.from(engine, row, true, 2, now).rejection)
        engine.state.holdings["AAPL"] =
            engine.state.holdings
                .getValue("AAPL")
                .copy(shares = StockMarketRules.MAX_SHARES)
        assertEquals(0L, StockTradeState.from(engine, row, true, 1, now).maximum)
    }

    @Test
    fun `empty and selected only portfolios have no cost or performance`() {
        val engine = GameEngine(GameState())
        val empty = StockMarketState.from(engine, MarketPrices(), now)
        assertTrue(empty.rows.isEmpty())
        assertEquals("0/5", empty.slots)
        assertEquals("$0", empty.portfolioValue)
        assertEquals("0%", empty.performance)
        assertEquals(0L, empty.gainCents)
        assertFalse(empty.hasCost)
        assertNull(empty.houseOrigin)
        assertNull(empty.upgrade)
        val selected = StockMarketState.from(engine, MarketPrices(mapOf("AAPL" to quote()), "AAPL"), now)
        assertEquals(1, selected.rows.size)
        assertNull(selected.rows.single().worth)
        assertEquals(empty.portfolioValue, selected.portfolioValue)
        assertEquals(empty.slots, selected.slots)
        assertTrue(StockMarketState.from(engine, MarketPrices(selected = "MISSING"), now).rows.isEmpty())
    }

    @Test
    fun `row formatting uses the displayed price and omits unavailable day changes`() {
        val row = StockRowState(quote(cents = 1000), null, 1125)
        assertEquals("$11.25", row.price)
        assertEquals("+25.00%", row.dayChange)
        assertNull(row.worth)
        assertEquals("-50.00%", row.copy(priceCents = 450).dayChange)
        assertEquals("+0.00%", row.copy(priceCents = 900).dayChange)
        assertNull(row.copy(quote = quote().copy(previousCloseCents = null)).dayChange)
        assertNull(row.copy(quote = quote().copy(previousCloseCents = 0)).dayChange)
    }

    @Test
    fun `selected owned ticker appears once and offline snapshot retains saved values`() {
        val engine = GameEngine(GameState().apply { money = 100.0 })
        engine.buyShares(quote(), 3, now = now)
        val saved = StockMarketState.from(engine, MarketPrices(), now)
        val failed =
            StockMarketState.from(
                engine,
                MarketPrices(selected = "AAPL", message = StockQuoteError.network.message),
                now,
            )
        assertEquals(saved, failed)
        assertEquals("$30", failed.rows.single().worth)
    }

    @Test
    fun `trade maximum floors affordable shares and refuses quantity above it`() {
        val engine = GameEngine(GameState().apply { money = 24.99 })
        val row = StockRowState(quote(), null, 1000)
        val allowed = StockTradeState.from(engine, row, true, 2, now)
        assertEquals(2L, allowed.maximum)
        assertEquals("$20", allowed.total)
        assertNull(allowed.rejection)
        assertNotNull(StockTradeState.from(engine, row, true, 3, now).rejection)
        for (price in listOf(0L, -1L)) {
            val invalid = StockTradeState.from(engine, row.copy(priceCents = price), true, 1, now)
            assertEquals(0L, invalid.maximum)
        }
        assertNotNull(StockTradeState.from(engine, row.copy(priceCents = -1), true, 1, now).rejection)
        engine.state.money = 9.99
        assertEquals(0L, StockTradeState.from(engine, row, true, 1, now).maximum)
    }

    @Test
    fun `sell maximum reads current holdings and loss formatting preserves cents`() {
        val engine = GameEngine(GameState().apply { money = 100.0 })
        engine.buyShares(quote(), 3, now = now)
        val row = StockRowState(quote(), null, 999)
        val sale = StockTradeState.from(engine, row, false, 3, now)
        assertEquals(3L, sale.maximum)
        assertEquals("$29.97", sale.total)
        assertEquals("-$0.03", sale.profit)
        assertEquals(-3L, sale.profitCents)
        assertNull(sale.rejection)
        val excessive = StockTradeState.from(engine, row, false, 4, now)
        assertNotNull(excessive.rejection)
        assertNull(excessive.profit)
        assertNull(excessive.profitCents)
        assertEquals(
            3L,
            engine.state.holdings
                .getValue("AAPL")
                .shares,
        )
    }

    @Test
    fun `failed search preserves selection and cached search clears network error without another request`() =
        runBlocking {
            val requests = mutableListOf<String>()
            val controller =
                StockMarketController(
                    GameSession(GameEngine(GameState())),
                    StockQuoteProvider {
                        requests += it
                        if (it == "FAIL") throw StockQuoteException(StockQuoteError.network)
                        quote(it)
                    },
                    { now },
                )
            controller.search("AAPL")
            controller.search("FAIL")
            assertEquals("AAPL", controller.prices.value.selected)
            assertEquals(StockQuoteError.network.message, controller.prices.value.message)
            assertFalse(controller.prices.value.loading)
            assertEquals(setOf("AAPL"), controller.prices.value.quotes.keys)
            controller.search(" aapl ")
            assertNull(controller.prices.value.message)
            assertEquals(listOf("AAPL", "FAIL"), requests)
        }

    @Test
    fun `search publishes loading before fetch and success after fetch without touching the farm`() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val response = CompletableDeferred<StockQuote>()
            val engine = GameEngine(GameState().apply { money = 123.0 })
            val controller =
                StockMarketController(
                    GameSession(engine),
                    StockQuoteProvider {
                        started.complete(Unit)
                        response.await()
                    },
                    { now },
                )
            val search = launch { controller.search("aapl") }
            started.await()
            assertTrue(controller.prices.value.loading)
            assertNull(controller.prices.value.selected)
            response.complete(quote())
            search.join()
            assertFalse(controller.prices.value.loading)
            assertEquals("AAPL", controller.prices.value.selected)
            assertEquals(quote(), controller.prices.value.quotes["AAPL"])
            assertTrue(engine.state.holdings.isEmpty())
            assertEquals(123.0, engine.state.money)
        }

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
