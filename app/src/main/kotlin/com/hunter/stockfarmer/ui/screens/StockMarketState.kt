package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.market.StockQuoteError
import com.hunter.stockfarmer.core.market.StockQuoteException
import com.hunter.stockfarmer.core.market.StockQuoteProvider
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Holding
import com.hunter.stockfarmer.core.model.StockQuote
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.PriceJitter
import com.hunter.stockfarmer.core.rules.StockMarketRules
import com.hunter.stockfarmer.core.rules.buyShares
import com.hunter.stockfarmer.core.rules.claimStockPull
import com.hunter.stockfarmer.core.rules.holdings
import com.hunter.stockfarmer.core.rules.farmhouseUpgradeRejection
import com.hunter.stockfarmer.core.rules.sellShares
import com.hunter.stockfarmer.core.rules.stockSlotLimit
import com.hunter.stockfarmer.core.rules.updatePrices
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.market.StockMarketSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import com.hunter.stockfarmer.market.MarketPrices as DomainMarketPrices

internal typealias MarketPrices = DomainMarketPrices

/** Owns one visit's searches. Owned tickers always use the engine's shared five-minute pull gate. */
internal class StockMarketController(
    private val session: GameSession,
    private val provider: StockQuoteProvider,
    private val now: () -> Instant = Instant::now,
    val market: StockMarketSession = StockMarketSession(provider, now),
) {
    val prices: StateFlow<MarketPrices> get() = market.marketPrices
    private val requests = Mutex()

    suspend fun pullIfDue(reportsFailure: Boolean = false) =
        requests.withLock {
            pullOwned(reportsFailure)
        }

    suspend fun search(text: String) =
        requests.withLock {
            val ticker = StockMarketRules.normalizedTicker(text)
            if (ticker == null) {
                market.search(text, now())
                return@withLock
            }
            if (session.read { ticker in it.holdings }) {
                pullOwned(true)
                market.select(ticker)
            } else {
                market.search(text, now())
            }
        }

    private suspend fun pullOwned(reportsFailure: Boolean) {
        val time = now()
        val tickers =
            session.act { engine ->
                if (engine.claimStockPull(time)) engine.holdings.map { it.ticker } else emptyList()
            }
        if (tickers.isEmpty()) return
        val quotes = market.pull(tickers, time, reportsFailure)
        session.act { it.updatePrices(quotes, time) }
        session.commit()
    }
}

internal data class StockRowState(
    val quote: StockQuote,
    val holding: Holding?,
    val priceCents: Long,
) {
    val price: String get() = StockMarketRules.dollars(priceCents).dollars
    val dayChange: String? get() =
        StockMarketRules.dayChange(priceCents, quote.previousCloseCents)?.let(ScreenFormat::percent)
    val worth: String? get() = holding?.let { StockMarketRules.dollars(it.shares * priceCents).dollars }
}

internal data class StockMarketState(
    val rows: List<StockRowState>,
    val slots: String,
    val portfolioValue: String,
    val performance: String,
    val gainCents: Long,
    val hasCost: Boolean,
    val houseOrigin: GridPoint?,
    val upgrade: UpgradeState?,
) {
    companion object {
        fun from(
            engine: GameEngine,
            prices: MarketPrices,
            time: Instant,
        ): StockMarketState {
            val tickers = (engine.holdings.map { it.ticker } + listOfNotNull(prices.selected)).distinct()
            val rows =
                tickers.mapNotNull { ticker ->
                    val holding = engine.state.holdings[ticker]
                    val fetched = prices.quotes[ticker]
                    val quote =
                        if (holding == null) {
                            fetched
                        } else {
                            StockQuote(
                                ticker,
                                holding.lastPriceCents,
                                holding.quotedAt,
                                holding.previousCloseCents ?: fetched?.previousCloseCents,
                            )
                        }
                    quote?.let {
                        StockRowState(
                            it,
                            holding,
                            PriceJitter.priceCents(ticker, it.priceCents, PriceJitter.step(time)),
                        )
                    }
                }
            val value = rows.sumOf { (it.holding?.shares ?: 0) * it.priceCents }
            val cost = engine.holdings.sumOf { it.shares * it.averageCostCents }
            val house =
                engine.state.buildings.entries
                    .firstOrNull { Building.named(it.value)?.isHouse == true }
            val upgrade =
                house?.takeIf { Building.named(it.value)?.isUpgradedHouse == false }?.let {
                    houseUpgrade(engine, it.key, time)
                }
            val slots = "${engine.state.holdings.size}/${engine.stockSlotLimit}"
            val performance = if (cost > 0) ScreenFormat.percent((value - cost).toDouble() / cost) else "0%"
            return StockMarketState(
                rows,
                slots,
                StockMarketRules.dollars(value).dollars,
                performance,
                value - cost,
                cost > 0,
                house?.key,
                upgrade,
            )
        }
    }
}

private fun houseUpgrade(
    engine: GameEngine,
    origin: GridPoint,
    time: Instant,
): UpgradeState {
    val baseLimit = StockMarketRules.BASE_SLOT_LIMIT
    val upgradedLimit = StockMarketRules.UPGRADED_SLOT_LIMIT
    return UpgradeState(
        "Luxury House",
        listOf(
            "Your House becomes a Luxury House where it stands",
            "Own up to $upgradedLimit different stocks, up from $baseLimit",
            "It is a little taller, so it needs one more row of free space",
            "Unlocked at Level ${StockMarketRules.HOUSE_UPGRADE_LEVEL}",
        ),
        StockMarketRules.HOUSE_UPGRADE_COST.dollars,
        engine.farmhouseUpgradeRejection(origin, time)?.message,
    )
}

internal data class StockTradeState(
    val maximum: Long,
    val total: String,
    val profit: String?,
    val rejection: String?,
    val profitCents: Long? = null,
) {
    companion object {
        fun from(
            engine: GameEngine,
            row: StockRowState,
            buying: Boolean,
            quantity: Long,
            now: Instant,
        ): StockTradeState {
            val owned = engine.state.holdings[row.quote.ticker]?.shares ?: 0L
            val maximum =
                if (!buying) {
                    owned
                } else if (row.priceCents <= 0) {
                    0L
                } else {
                    minOf(
                        StockMarketRules.cents(engine.state.money) / row.priceCents,
                        StockMarketRules.MAX_SHARES - owned,
                    )
                }
            val preview = engine.copy()
            val events =
                if (buying) {
                    preview.buyShares(row.quote, quantity, row.priceCents, now)
                } else {
                    preview.sellShares(row.quote, quantity, row.priceCents, now)
                }
            val rejection =
                events
                    .filterIsInstance<GameEvent.Rejected>()
                    .firstOrNull()
                    ?.rejection
                    ?.message
            val total = StockMarketRules.totalCents(quantity, row.priceCents)
            val profitCents =
                if (rejection == null && !buying) {
                    preview.state.stockTrades
                        .lastOrNull()
                        ?.profitCents
                } else {
                    null
                }
            return StockTradeState(
                maximum,
                total?.let { StockMarketRules.dollars(it).dollars } ?: "—",
                profitCents?.let(ScreenFormat::signedMoney),
                rejection,
                profitCents,
            )
        }
    }
}
