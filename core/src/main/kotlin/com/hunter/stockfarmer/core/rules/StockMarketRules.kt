package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.Holding
import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero
import java.time.Duration
import java.time.Instant

/** The arithmetic and limits of buying and selling shares. All prices are in cents, as 64-bit values. */
object StockMarketRules {
    /** How many different tickers can be owned, and how many once the House is upgraded. */
    const val BASE_SLOT_LIMIT = 5
    const val UPGRADED_SLOT_LIMIT = 10
    const val HOUSE_UPGRADE_COST = 50_000.0
    const val HOUSE_UPGRADE_LEVEL = 24
    const val MAX_SHARES = 1_000_000_000L
    const val MAX_PRICE_CENTS = 100_000_000_000L

    /** Prices kept per ticker for charting: a week of pulls at the usual spacing. */
    const val MAX_HISTORY_POINTS = 2_016

    /** Trades kept in the ledger; the lifetime profit is kept separately and counts them all. */
    const val MAX_TRADES_KEPT = 10_000
    const val MAX_PROFIT_CENTS = 100_000_000_000_000L

    /** The least time between pulls of the prices of owned stocks, in seconds. */
    const val PULL_INTERVAL = 300.0

    /**
     * Whether the prices of owned stocks may be pulled again. A last pull in the future, from a clock set back,
     * doesn't hold them off.
     */
    fun isPullDue(
        lastPullAt: Instant?,
        now: Instant,
    ): Boolean {
        if (lastPullAt == null) return true
        val elapsed = secondsBetween(lastPullAt, now)
        return elapsed < 0 || elapsed >= PULL_INTERVAL
    }

    /** Seconds until the next pull is allowed, zero if it is due. */
    fun secondsUntilPull(
        lastPullAt: Instant?,
        now: Instant,
    ): Double {
        if (lastPullAt == null || isPullDue(lastPullAt, now)) return 0.0
        return PULL_INTERVAL - secondsBetween(lastPullAt, now)
    }

    /** How far the price is from yesterday's close, as a fraction: 0.0125 for up 1.25%. */
    fun dayChange(
        priceCents: Long,
        previousCloseCents: Long?,
    ): Double? {
        if (previousCloseCents == null || previousCloseCents <= 0) return null
        return (priceCents - previousCloseCents).toDouble() / previousCloseCents.toDouble()
    }

    /** The ticker as the market knows it: trimmed and upper case, or null if it isn't one. */
    fun normalizedTicker(text: String): String? {
        val ticker = text.trim().uppercase()
        if (ticker.length !in 1..10 || !ticker.all { it in ALLOWED_TICKER_CHARACTERS }) return null
        return ticker
    }

    /** What a number of shares cost at a price, or null if that is more than money can hold. */
    fun totalCents(
        shares: Long,
        priceCents: Long,
    ): Long? {
        if (shares <= 0 || priceCents < 0) return null
        val total =
            try {
                Math.multiplyExact(shares, priceCents)
            } catch (_: ArithmeticException) {
                return null
            }
        return if (total > (GameState.MAX_MONEY * 100).roundedHalfAwayFromZero().toLong()) null else total
    }

    /** The average cost per share after buying more, rounded to the cent. */
    fun averageCostCents(
        owned: Long,
        averageCents: Long,
        buying: Long,
        priceCents: Long,
    ): Long {
        val spent = owned * averageCents + buying * priceCents
        val count = owned + buying
        return (spent + count / 2) / count
    }

    fun cents(dollars: Double): Long = (dollars * 100).roundedHalfAwayFromZero().toLong()

    fun dollars(cents: Long): Double = cents.toDouble() / 100

    private const val ALLOWED_TICKER_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-^="

    private operator fun String.contains(character: Char) = indexOf(character) >= 0

    private fun secondsBetween(
        from: Instant,
        to: Instant,
    ): Double = Duration.between(from, to).toNanos() / 1e9
}

val Holding.value: Double get() = StockMarketRules.dollars(valueCents)
