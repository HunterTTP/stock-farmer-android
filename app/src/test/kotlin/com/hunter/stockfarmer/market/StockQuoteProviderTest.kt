package com.hunter.stockfarmer.market

import com.hunter.stockfarmer.core.market.StockQuoteError
import com.hunter.stockfarmer.core.market.StockQuoteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.time.Instant

class StockQuoteProviderTest {
    private val now = Instant.ofEpochSecond(9_000_000)
    private val response =
        """{"chart":{"result":[{"meta":{"regularMarketPrice":123.45,"previousClose":120}}],"error":null}}"""

    @Test
    fun `normalizes and encodes ticker and configures HTTPS request`() =
        runBlocking {
            val connection = FakeConnection(response)
            var requested: URL? = null
            val provider =
                StockQuoteProvider({
                    requested = it
                    connection
                }, { now })
            val quote = provider.quote(" ^gspc ")
            assertEquals(
                "https://query1.finance.yahoo.com/v8/finance/chart/%5EGSPC?interval=1d&range=1d",
                requested.toString(),
            )
            assertEquals("^GSPC", quote.ticker)
            assertEquals(12_345L, quote.priceCents)
            assertEquals(12_000L, quote.previousCloseCents)
            assertEquals(now, quote.quotedAt)
            assertEquals(10_000, connection.connectTimeout)
            assertEquals(10_000, connection.readTimeout)
            assertEquals("Mozilla/5.0", connection.getRequestProperty("User-Agent"))
            assertFalse(connection.useCaches)
            assertFalse(connection.instanceFollowRedirects)
            assertTrue(connection.closed)
            assertTrue(connection.disconnected)
        }

    @Test
    fun `invalid tickers never open a connection`() {
        val provider = StockQuoteProvider({ error("Unexpected request") }, { now })
        for (ticker in listOf("", "../bad", "TOO-LONG-TICKER", "A B")) {
            assertError(StockQuoteError.invalidTicker) { provider.quote(ticker) }
        }
    }

    @Test
    fun `cache uses fetch time and expires at five minutes including clock rollback`() =
        runBlocking {
            var time = now
            var requests = 0
            val oldQuote = response.replace("\"previousClose\":120", "\"regularMarketTime\":1")
            val provider =
                StockQuoteProvider({
                    requests++
                    FakeConnection(oldQuote)
                }, { time })
            provider.quote("aapl")
            time = now.plusSeconds(299)
            provider.quote(" AAPL ")
            assertEquals(1, requests)
            time = now.plusSeconds(300)
            provider.quote("AAPL")
            assertEquals(2, requests)
            time = now
            provider.quote("AAPL")
            assertEquals(3, requests)
            provider.quote("MSFT")
            assertEquals(4, requests)
        }

    @Test
    fun `404 parses Yahoo error body instead of becoming a network error`() {
        val connection = FakeConnection("""{"chart":{"result":null,"error":{"code":"Not Found"}}}""", 404)
        assertError(StockQuoteError.invalidTicker) { StockQuoteProvider({ connection }, { now }).quote("NOPE") }
        assertTrue(connection.closed)
        assertTrue(connection.disconnected)
    }

    @Test
    fun `malformed and missing prices remain unavailable and are not cached`() {
        for (body in listOf("not json", """{"chart":{"result":[{"meta":{}}],"error":null}}""")) {
            var requests = 0
            val provider =
                StockQuoteProvider({
                    requests++
                    FakeConnection(body)
                }, { now })
            repeat(2) { assertError(StockQuoteError.unavailable) { provider.quote("AAPL") } }
            assertEquals(2, requests)
        }
    }

    @Test
    fun `other status codes are network errors and release connection`() {
        for (status in listOf(301, 403, 429, 500)) {
            val connection = FakeConnection(response, status)
            assertError(StockQuoteError.network) { StockQuoteProvider({ connection }, { now }).quote("AAPL") }
            assertTrue(connection.disconnected)
        }
    }

    @Test
    fun `connection and stream failures map to network and failed refresh does not serve stale data`() {
        assertError(StockQuoteError.network) { StockQuoteProvider({ throw IOException() }, { now }).quote("AAPL") }
        var time = now
        var failure: IOException? = null
        val connections = mutableListOf<FakeConnection>()
        val provider =
            StockQuoteProvider({ FakeConnection(response, failure = failure).also(connections::add) }, { time })
        runBlocking { provider.quote("AAPL") }
        time = now.plusSeconds(300)
        failure = SocketTimeoutException()
        repeat(2) { assertError(StockQuoteError.network) { provider.quote("AAPL") } }
        assertEquals(3, connections.size)
        assertTrue(connections.all { it.closed && it.disconnected })
    }

    @Test
    fun `cancellation is not converted into a quote error`() {
        val provider = StockQuoteProvider({ throw CancellationException("Cancelled") }, { now })
        assertThrows(CancellationException::class.java) { runBlocking { provider.quote("AAPL") } }
    }

    private fun assertError(
        error: StockQuoteError,
        action: suspend () -> Unit,
    ) {
        val exception = assertThrows(StockQuoteException::class.java) { runBlocking { action() } }
        assertEquals(error, exception.error)
    }

    private class FakeConnection(
        body: String,
        private val status: Int = 200,
        private val failure: IOException? = null,
    ) : HttpURLConnection(URI("https://example.com").toURL()) {
        var closed = false
        var disconnected = false
        private val stream =
            object : InputStream() {
                private val bytes = ByteArrayInputStream(body.toByteArray())

                override fun read(): Int {
                    failure?.let { throw it }
                    return bytes.read()
                }

                override fun close() {
                    closed = true
                }
            }

        override fun getResponseCode() = status

        override fun getInputStream(): InputStream = stream

        override fun getErrorStream(): InputStream = stream

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy() = false

        override fun connect() = Unit
    }
}
