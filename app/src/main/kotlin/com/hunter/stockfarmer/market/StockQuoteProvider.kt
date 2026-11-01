package com.hunter.stockfarmer.market

import com.hunter.stockfarmer.core.market.StockQuoteError
import com.hunter.stockfarmer.core.market.StockQuoteException
import com.hunter.stockfarmer.core.market.YahooChartParser
import com.hunter.stockfarmer.core.model.StockQuote
import com.hunter.stockfarmer.core.rules.StockMarketRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import com.hunter.stockfarmer.core.market.StockQuoteProvider as QuoteProvider

/** Device-side Yahoo quotes, cached for the same pull spacing as the market session. */
class StockQuoteProvider(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val now: () -> Instant = Instant::now,
) : QuoteProvider {
    private data class CachedQuote(
        val quote: StockQuote,
        val fetchedAt: Instant,
    )

    private val cache = mutableMapOf<String, CachedQuote>()
    private val mutex = Mutex()

    override suspend fun quote(ticker: String): StockQuote {
        val symbol =
            StockMarketRules.normalizedTicker(ticker) ?: throw StockQuoteException(StockQuoteError.invalidTicker)
        return mutex.withLock {
            currentCoroutineContext().ensureActive()
            val fetchedAt = now()
            cache[symbol]?.takeUnless { StockMarketRules.isPullDue(it.fetchedAt, fetchedAt) }?.let { return it.quote }
            // Thread interruption cannot stop a blocked socket read, so a cancelled quote closes its connection
            // to release the lock instead of holding other tickers for the full timeout.
            val open = AtomicReference<HttpURLConnection?>()
            val quote =
                try {
                    coroutineScope {
                        val fetching = async(Dispatchers.IO) { runInterruptible { fetch(symbol, open) } }
                        try {
                            fetching.await()
                        } catch (cancelled: CancellationException) {
                            open.get()?.disconnect()
                            throw cancelled
                        }
                    }
                } catch (error: StockQuoteException) {
                    currentCoroutineContext().ensureActive()
                    throw error
                }
            currentCoroutineContext().ensureActive()
            cache[symbol] = CachedQuote(quote, fetchedAt)
            quote
        }
    }

    private fun fetch(
        symbol: String,
        open: AtomicReference<HttpURLConnection?>,
    ): StockQuote {
        val path = URLEncoder.encode(symbol, "UTF-8")
        val url = URI("https://query1.finance.yahoo.com/v8/finance/chart/$path?interval=1d&range=1d").toURL()
        try {
            val connection = openConnection(url)
            open.set(connection)
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.useCaches = false
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                val stream =
                    when (connection.responseCode) {
                        200 -> connection.inputStream

                        // Yahoo explains unknown tickers in its 404 JSON body.
                        404 -> connection.errorStream ?: runCatching { connection.inputStream }.getOrNull()

                        else -> throw StockQuoteException(StockQuoteError.network)
                    } ?: throw StockQuoteException(StockQuoteError.unavailable)
                val data = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                return YahooChartParser.quote(symbol, data, now())
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            throw StockQuoteException(StockQuoteError.network)
        }
    }
}
