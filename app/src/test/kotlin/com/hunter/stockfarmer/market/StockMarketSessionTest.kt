package com.hunter.stockfarmer.market

import com.hunter.stockfarmer.core.market.StockQuoteError
import com.hunter.stockfarmer.core.market.StockQuoteException
import com.hunter.stockfarmer.core.market.StockQuoteProvider
import com.hunter.stockfarmer.core.model.StockQuote
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    private val start = Instant.parse("2026-01-03T12:00:00Z")

    @Test
    fun `freshness follows fetch time rather than the age of the exchange quote`() =
        runBlocking {
            var requests = 0
            val session =
                StockMarketSession(
                    StockQuoteProvider {
                        requests++
                        staleQuote(it)
                    },
                )
            session.pull(listOf("AAPL"), start)
            session.search(" aapl ", start.plusMillis(299_999))
            assertEquals(1, requests)
            session.search("AAPL", start.plusSeconds(300))
            assertEquals(2, requests)
            assertFalse(session.hasFreshQuote("AAPL", start.plusSeconds(299)))
            assertTrue(session.hasFreshQuote("AAPL", start.plusSeconds(300)))
        }

    @Test
    fun `a failed refresh preserves both the old quote and its original freshness deadline`() =
        runBlocking {
            var fail = false
            val session =
                StockMarketSession(
                    StockQuoteProvider {
                        if (fail) throw StockQuoteException(StockQuoteError.network)
                        staleQuote(it)
                    },
                )
            session.pull(listOf("AAPL"), start)
            session.select("AAPL")
            fail = true
            session.pull(listOf("AAPL"), start.plusSeconds(300), reportsFailure = true)
            assertEquals(staleQuote("AAPL"), session.quotes.value["AAPL"])
            assertFalse(session.hasFreshQuote("AAPL", start.plusSeconds(300)))
            assertEquals("AAPL", session.selected.value)
            assertEquals(StockQuoteError.network.message, session.message.value)
        }

    @Test
    fun `silent failure retains a previous message and any successful pull clears it`() =
        runBlocking {
            var fail = true
            val session =
                StockMarketSession(
                    StockQuoteProvider {
                        if (fail) throw StockQuoteException(StockQuoteError.network)
                        staleQuote(it)
                    },
                )
            session.search("bad ticker", start)
            val previous = session.message.value
            session.pull(listOf("AAPL"), start, reportsFailure = false)
            assertEquals(previous, session.message.value)
            fail = false
            session.pull(listOf("AAPL"), start, reportsFailure = false)
            assertNull(session.message.value)
            assertNull(session.selected.value)
            assertEquals(MarketPrices(quotes = mapOf("AAPL" to staleQuote("AAPL"))), session.marketPrices.value)
        }

    @Test
    fun `empty pull clears a previous error without clearing cached prices or selection`() =
        runBlocking {
            val session = StockMarketSession(StockQuoteProvider { staleQuote(it) })
            session.search("AAPL", start)
            session.search("bad ticker", start)
            assertTrue(session.pull(emptyList(), start, reportsFailure = true).isEmpty())
            assertNull(session.message.value)
            assertEquals("AAPL", session.selected.value)
            assertEquals(setOf("AAPL"), session.quotes.value.keys)
            assertFalse(session.isLoading.value)
        }

    @Test
    fun `an unclassified provider error is ignored by the task group`() =
        runBlocking {
            val session = StockMarketSession(StockQuoteProvider { throw IllegalStateException("No typed quote error") })
            assertTrue(session.pull(listOf("AAPL"), start, reportsFailure = true).isEmpty())
            assertNull(session.message.value)
            assertFalse(session.isLoading.value)
        }

    @Test
    fun `the first completed typed failure supplies the message`() =
        runBlocking {
            withTimeout(5000) {
                val slower = CompletableDeferred<Unit>()
                val session =
                    StockMarketSession(
                        StockQuoteProvider {
                            if (it == "SLOW") {
                                slower.await()
                                throw StockQuoteException(StockQuoteError.network)
                            }
                            slower.complete(Unit)
                            throw StockQuoteException(StockQuoteError.unavailable)
                        },
                    )
                session.pull(listOf("SLOW", "FAST"), start, reportsFailure = true)
                assertEquals(StockQuoteError.unavailable.message, session.message.value)
            }
        }

    @Test
    fun `a later request starts while an earlier pull waits and controls the loading indicator`() =
        runBlocking {
            withTimeout(5000) {
                val firstStarted = CompletableDeferred<Unit>()
                val secondStarted = CompletableDeferred<Unit>()
                val releaseFirst = CompletableDeferred<Unit>()
                val releaseSecond = CompletableDeferred<Unit>()
                val session =
                    StockMarketSession(
                        StockQuoteProvider {
                            if (it == "FIRST") {
                                firstStarted.complete(Unit)
                                releaseFirst.await()
                            } else {
                                secondStarted.complete(Unit)
                                releaseSecond.await()
                            }
                            staleQuote(it)
                        },
                    )
                val first = launch { session.pull(listOf("FIRST"), start) }
                firstStarted.await()
                val second = launch { session.search("SECOND", start) }
                secondStarted.await()
                releaseFirst.complete(Unit)
                first.join()
                assertTrue(session.isLoading.value)
                releaseSecond.complete(Unit)
                second.join()
                assertFalse(session.isLoading.value)
                assertEquals("SECOND", session.selected.value)
                assertEquals(setOf("FIRST", "SECOND"), session.marketPrices.value.quotes.keys)
            }
        }

    @Test
    fun `cancelling an older pull cannot clear a newer search loading indicator or cache partial prices`() =
        runBlocking {
            withTimeout(5000) {
                val oldStarted = CompletableDeferred<Unit>()
                val newStarted = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val session =
                    StockMarketSession(
                        StockQuoteProvider {
                            when (it) {
                                "STALL" -> {
                                    oldStarted.complete(Unit)
                                    awaitCancellation()
                                }

                                "NEW" -> {
                                    newStarted.complete(Unit)
                                    release.await()
                                    staleQuote(it)
                                }

                                else -> {
                                    staleQuote(it)
                                }
                            }
                        },
                    )
                val old =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        session.pull(listOf("OK", "STALL"), start)
                    }
                oldStarted.await()
                val fresh = launch { session.search("NEW", start) }
                newStarted.await()
                old.cancelAndJoin()
                assertTrue(session.isLoading.value)
                assertTrue(session.quotes.value.isEmpty())
                release.complete(Unit)
                fresh.join()
                assertEquals(setOf("NEW"), session.quotes.value.keys)
                assertFalse(session.isLoading.value)
            }
        }

    private fun staleQuote(ticker: String) = StockQuote(ticker, 100_000_000_000L, start.minusSeconds(86_400))
}
