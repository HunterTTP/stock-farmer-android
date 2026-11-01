package com.hunter.stockfarmer.market

import com.hunter.stockfarmer.core.market.StockQuoteError
import com.hunter.stockfarmer.core.market.StockQuoteException
import com.hunter.stockfarmer.core.market.StockQuoteProvider
import com.hunter.stockfarmer.core.model.StockQuote
import com.hunter.stockfarmer.core.rules.StockMarketRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Snapshot of the prices and status for the Stock Market menu.
 */
data class MarketPrices(
    val quotes: Map<String, StockQuote> = emptyMap(),
    val selected: String? = null,
    val message: String? = null,
    val loading: Boolean = false,
)

/**
 * The prices fetched for the Stock Market menu, and the one place that asks the provider for them.
 * Pure Kotlin/coroutine equivalent of iOS StockMarketSession.
 */
class StockMarketSession(
    private val provider: StockQuoteProvider = StockQuoteProvider(),
    private val now: () -> Instant = Instant::now,
) {
    private val _marketPrices = MutableStateFlow(MarketPrices())
    val marketPrices: StateFlow<MarketPrices> = _marketPrices.asStateFlow()

    private val _quotes = MutableStateFlow<Map<String, StockQuote>>(emptyMap())
    val quotes: StateFlow<Map<String, StockQuote>> = _quotes.asStateFlow()

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val fetchedAt = mutableMapOf<String, Instant>()
    private val mutex = Mutex()
    private var generation = 0

    /** Fetches these tickers now; the caller has already checked that a pull is allowed. */
    suspend fun pull(
        tickers: List<String>,
        now: Instant = this.now(),
        reportsFailure: Boolean = false,
    ): List<StockQuote> =
        mutex.withLock {
            val fetched = fetch(tickers, reportsFailure)
            for (quote in fetched) {
                fetchedAt[quote.ticker] = now
            }
            fetched
        }

    /** Looks a ticker up, reusing a price fetched within the pull spacing instead of asking again. */
    suspend fun search(
        text: String,
        now: Instant = this.now(),
    ) = mutex.withLock {
        val ticker = StockMarketRules.normalizedTicker(text)
        if (ticker == null) {
            updateStatus(message = StockQuoteError.invalidTicker.message)
            return@withLock
        }
        if (hasFreshQuote(ticker, now)) {
            updateStatus(selected = ticker, message = null)
            return@withLock
        }
        val fetched = fetch(listOf(ticker), reportsFailure = true)
        for (quote in fetched) {
            fetchedAt[quote.ticker] = now
        }
        if (fetched.isNotEmpty()) {
            updateStatus(selected = ticker)
        }
    }

    fun select(ticker: String) {
        updateStatus(selected = ticker, message = null)
    }

    fun hasFreshQuote(
        ticker: String,
        now: Instant = this.now(),
    ): Boolean = _quotes.value[ticker] != null && !StockMarketRules.isPullDue(fetchedAt[ticker], now)

    private fun updateStatus(
        quotes: Map<String, StockQuote>? = null,
        selected: String? = _selected.value,
        message: String? = _message.value,
        loading: Boolean = _isLoading.value,
    ) {
        if (quotes != null) _quotes.value = quotes
        _selected.value = selected
        _message.value = message
        _isLoading.value = loading
        _marketPrices.value =
            MarketPrices(
                quotes = quotes ?: _quotes.value,
                selected = selected,
                message = message,
                loading = loading,
            )
    }

    private suspend fun fetch(
        tickers: List<String>,
        reportsFailure: Boolean,
    ): List<StockQuote> =
        coroutineScope {
            generation++
            val mine = generation
            updateStatus(loading = true)
            try {
                val deferreds =
                    tickers.map { ticker ->
                        async {
                            try {
                                Result.success(provider.quote(ticker))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: StockQuoteException) {
                                Result.failure(error)
                            } catch (_: Exception) {
                                Result.failure(StockQuoteException(StockQuoteError.network))
                            }
                        }
                    }
                val results = deferreds.map { it.await() }
                val fetched = mutableListOf<StockQuote>()
                var failure: StockQuoteError? = null
                for (result in results) {
                    result.fold(
                        onSuccess = { fetched.add(it) },
                        onFailure = { error ->
                            if (failure == null && error is StockQuoteException) {
                                failure = error.error
                            }
                        },
                    )
                }
                val updatedQuotes =
                    if (fetched.isNotEmpty()) {
                        _quotes.value + fetched.associateBy { it.ticker }
                    } else {
                        _quotes.value
                    }
                val updatedMessage =
                    if (reportsFailure || failure == null) {
                        failure?.let {
                            if (fetched.isEmpty()) it.message else "Some prices couldn't be refreshed"
                        }
                    } else {
                        _message.value
                    }
                updateStatus(
                    quotes = updatedQuotes,
                    message = updatedMessage,
                    loading = if (mine == generation) false else _isLoading.value,
                )
                fetched
            } finally {
                if (mine == generation) {
                    updateStatus(loading = false)
                }
            }
        }
}
