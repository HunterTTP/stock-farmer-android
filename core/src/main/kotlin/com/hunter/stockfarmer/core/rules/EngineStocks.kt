package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.Holding
import com.hunter.stockfarmer.core.model.PricePoint
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.model.StockQuote
import com.hunter.stockfarmer.core.model.StockTrade
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/** The holdings in ticker order. */
val GameEngine.holdings: List<Holding> get() = state.holdings.values.sortedBy { it.ticker }

val GameEngine.stockSlotsUsed: Int get() = state.holdings.size

/** How many different stocks can be owned: more once the House has been upgraded. */
val GameEngine.stockSlotLimit: Int
    get() =
        if (state.buildings.values.any { Building.named(it)?.isUpgradedHouse == true }) {
            StockMarketRules.UPGRADED_SLOT_LIMIT
        } else {
            StockMarketRules.BASE_SLOT_LIMIT
        }

/** What the shares owned are worth at their last known prices. */
val GameEngine.portfolioValue: Double get() = StockMarketRules.dollars(state.holdings.values.sumOf { it.valueCents })

/**
 * Buys whole shares, at the quote's price unless a price is given, for money, taking a slot if the ticker is new. The
 * holding keeps the quote's price as its last real one.
 */
fun GameEngine.buyShares(
    quote: StockQuote,
    shares: Long,
    atCents: Long? = null,
    now: Instant,
): List<GameEvent> {
    val price = atCents ?: quote.priceCents
    val total = StockMarketRules.totalCents(shares, price)
    if (shares !in 1..StockMarketRules.MAX_SHARES || price !in 0..StockMarketRules.MAX_PRICE_CENTS ||
        quote.priceCents !in 0..StockMarketRules.MAX_PRICE_CENTS || total == null
    ) {
        return rejected(Rejection.InvalidQuantity)
    }
    val owned = state.holdings[quote.ticker]
    if (owned == null &&
        state.holdings.size >= stockSlotLimit
    ) {
        return rejected(Rejection.StockSlotsFull(stockSlotLimit))
    }
    val ownedShares = owned?.shares ?: 0
    if (ownedShares + shares > StockMarketRules.MAX_SHARES) return rejected(Rejection.InvalidQuantity)
    val cash = StockMarketRules.cents(state.money)
    if (cash < total) return rejected(Rejection.NeedMoney(StockMarketRules.dollars(total)))
    state.money = StockMarketRules.dollars(cash - total)
    state.holdings[quote.ticker] =
        Holding(
            ticker = quote.ticker,
            shares = ownedShares + shares,
            averageCostCents =
                StockMarketRules.averageCostCents(ownedShares, owned?.averageCostCents ?: 0, shares, price),
            lastPriceCents = quote.priceCents,
            quotedAt = quote.quotedAt,
            previousCloseCents = quote.previousCloseCents,
        )
    if (owned == null) state.priceHistory[quote.ticker] = listOf(PricePoint(quote.quotedAt, quote.priceCents))
    record(StockTrade(quote.ticker, true, shares, price, now))
    return listOf(GameEvent.BoughtShares(quote.ticker, shares, StockMarketRules.dollars(total)))
}

/**
 * Sells whole shares, at the quote's price unless a price is given. The last share sold frees the slot and forgets the
 * ticker's price history.
 */
fun GameEngine.sellShares(
    quote: StockQuote,
    shares: Long,
    atCents: Long? = null,
    now: Instant,
): List<GameEvent> {
    val price = atCents ?: quote.priceCents
    val total = StockMarketRules.totalCents(shares, price)
    if (shares <= 0 || price !in 0..StockMarketRules.MAX_PRICE_CENTS || total == null) {
        return rejected(Rejection.InvalidQuantity)
    }
    val holding = state.holdings[quote.ticker]
    if (holding == null || holding.shares < shares) {
        return rejected(Rejection.NotEnoughShares(state.holdings[quote.ticker]?.shares ?: 0))
    }
    state.money = min(GameState.MAX_MONEY, StockMarketRules.dollars(StockMarketRules.cents(state.money) + total))
    val profit = total - shares * holding.averageCostCents
    record(StockTrade(quote.ticker, false, shares, price, now, profit))
    countProfitTowardsQuests(profit)
    val remaining = holding.shares - shares
    if (remaining == 0L) {
        state.holdings.remove(quote.ticker)
        state.priceHistory.remove(quote.ticker)
    } else {
        state.holdings[quote.ticker] = holding.copy(shares = remaining)
    }
    return listOf(GameEvent.SoldShares(quote.ticker, shares, StockMarketRules.dollars(total)))
}

/**
 * Whether the prices of owned stocks are due to be pulled; if they are, notes that they are being pulled now, so nothing
 * else pulls them.
 */
fun GameEngine.claimStockPull(now: Instant): Boolean {
    if (state.holdings.isEmpty() || !StockMarketRules.isPullDue(state.lastStockPullAt, now)) return false
    state.lastStockPullAt = now
    return true
}

/** Records prices just pulled for the tickers owned, and keeps each in the ticker's history; other tickers are ignored. */
fun GameEngine.updatePrices(
    quotes: List<StockQuote>,
    pulledAt: Instant,
) {
    for (quote in quotes.filter { it.priceCents in 0..StockMarketRules.MAX_PRICE_CENTS }) {
        val holding = state.holdings[quote.ticker] ?: continue
        state.holdings[quote.ticker] =
            holding.copy(
                lastPriceCents = quote.priceCents,
                quotedAt = quote.quotedAt,
                previousCloseCents = quote.previousCloseCents ?: holding.previousCloseCents,
            )
        val history = (state.priceHistory[quote.ticker] ?: emptyList()) + PricePoint(pulledAt, quote.priceCents)
        state.priceHistory[quote.ticker] = history.takeLast(StockMarketRules.MAX_HISTORY_POINTS)
    }
}

/** Profit or loss from every stock sale ever made on this farm, in dollars. */
val GameEngine.lifetimeStockProfit: Double get() = StockMarketRules.dollars(state.lifetimeStockProfitCents)

private fun rejected(rejection: Rejection): List<GameEvent> = listOf(GameEvent.Rejected(rejection))

private fun GameEngine.record(trade: StockTrade) {
    state.stockTrades = (state.stockTrades + trade).takeLast(StockMarketRules.MAX_TRADES_KEPT)
    val limit = StockMarketRules.MAX_PROFIT_CENTS
    state.career.addGrossIncome(StockMarketRules.dollars(trade.profitCents))
    state.career.addStockResult(trade.profitCents)
    state.lifetimeStockProfitCents = min(limit, max(-limit, state.lifetimeStockProfitCents + trade.profitCents))
}

/** Whole dollars of profit made count towards "earn profit" quests; losses take nothing back. */
private fun GameEngine.countProfitTowardsQuests(profitCents: Long) {
    if (profitCents <= 0) return
    val total = state.stockProfitRemainderCents + min(profitCents, StockMarketRules.MAX_PROFIT_CENTS)
    state.stockProfitRemainderCents = total % 100
    recordQuestProgress(QuestGoal.StockProfit, min(total / 100, Int.MAX_VALUE.toLong()).toInt())
}
