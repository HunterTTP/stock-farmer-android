package com.hunter.stockfarmer.market

import com.hunter.stockfarmer.core.market.StockQuoteError
import com.hunter.stockfarmer.core.market.StockQuoteException
import com.hunter.stockfarmer.core.market.StockQuoteProvider
import com.hunter.stockfarmer.core.model.StockQuote
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class StockMarketSessionTest {
    private val now = Instant.ofEpochSecond(10_000_000)

    private fun quote(
        ticker: String,
        cents: Long = 1000,
    ) = StockQuote(ticker, cents, now)

    @Test
    fun `search normalizes ticker, checks freshness, and selects on success`() =
        runBlocking {
            var time = now
            val requested = mutableListOf<String>()
            val session =
                StockMarketSession(
                    StockQuoteProvider {
                        requested += it
                        quote(it)
                    },
                    { time },
                )

            session.search(" bad ticker ")
            assertEquals(StockQuoteError.invalidTicker.message, session.message.value)
            assertNull(session.selected.value)
            assertTrue(requested.isEmpty())

            session.search(" aapl ")
            assertEquals("AAPL", session.selected.value)
            assertNull(session.message.value)
            assertEquals(listOf("AAPL"), requested)
            assertTrue(session.hasFreshQuote("AAPL", time))

            time = now.plusSeconds(299)
            session.search("AAPL")
            assertEquals(1, requested.size)

            time = now.plusSeconds(300)
            session.search("AAPL")
            assertEquals(2, requested.size)

            time = now
            session.search("AAPL")
            assertEquals(3, requested.size)
            assertFalse(session.isLoading.value)
        }

    @Test
    fun `select updates selected and clears message`() {
        val session = StockMarketSession(StockQuoteProvider { quote(it) }, { now })
        session.select("MSFT")
        assertEquals("MSFT", session.selected.value)
        assertNull(session.message.value)
    }

    @Test
    fun `pull fetches multiple tickers concurrently and caches them`() =
        runBlocking {
            val requested = mutableListOf<String>()
            val session =
                StockMarketSession(
                    StockQuoteProvider {
                        requested += it
                        quote(it, 2500)
                    },
                    { now },
                )
            val quotes = session.pull(listOf("AAPL", "GOOG"), now, reportsFailure = true)
            assertEquals(2, quotes.size)
            assertEquals(setOf("AAPL", "GOOG"), session.quotes.value.keys)
            assertEquals(2500L, session.quotes.value["AAPL"]?.priceCents)
            assertNull(session.message.value)
        }

    @Test
    fun `partial refresh failure reports some prices could not be refreshed`() =
        runBlocking {
            val session =
                StockMarketSession(
                    StockQuoteProvider {
                        if (it == "FAIL") throw StockQuoteException(StockQuoteError.network)
                        quote(it)
                    },
                    { now },
                )
            val quotes = session.pull(listOf("OK", "FAIL"), now, reportsFailure = true)
            assertEquals(1, quotes.size)
            assertEquals("Some prices couldn't be refreshed", session.message.value)
            assertEquals("OK", quotes.first().ticker)
        }

    @Test
    fun `complete failure reports specific error message when reportsFailure is true`() =
        runBlocking {
            val session =
                StockMarketSession(
                    StockQuoteProvider {
                        throw StockQuoteException(StockQuoteError.network)
                    },
                    { now },
                )
            val quotes = session.pull(listOf("FAIL"), now, reportsFailure = true)
            assertTrue(quotes.isEmpty())
            assertEquals(StockQuoteError.network.message, session.message.value)
        }

    @Test
    fun `failure is suppressed when reportsFailure is false`() =
        runBlocking {
            val session =
                StockMarketSession(
                    StockQuoteProvider {
                        throw StockQuoteException(StockQuoteError.network)
                    },
                    { now },
                )
            val quotes = session.pull(listOf("FAIL"), now, reportsFailure = false)
            assertTrue(quotes.isEmpty())
            assertNull(session.message.value)
        }

    @Test
    fun `cancellation clears loading state`() =
        runBlocking {
            val waiting = CompletableDeferred<Unit>()
            val session =
                StockMarketSession(
                    StockQuoteProvider {
                        waiting.complete(Unit)
                        awaitCancellation()
                    },
                    { now },
                )
            val job = launch { session.pull(listOf("STALL"), now) }
            waiting.await()
            assertTrue(session.isLoading.value)
            job.cancelAndJoin()
            assertFalse(session.isLoading.value)
        }
}
