package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.util.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** A price for one share, in whole cents. */
@Serializable
data class StockQuote(
    val ticker: String,
    val priceCents: Long,
    @Serializable(with = InstantSerializer::class) val quotedAt: Instant,
    /** Yesterday's closing price, which the day's change is measured from, when the market reports one. */
    val previousCloseCents: Long? = null,
)

/** One price pulled from the market, kept for charting. */
@Serializable
data class PricePoint(
    @Serializable(with = InstantSerializer::class) val at: Instant,
    val priceCents: Long,
)

/** One buy or sale, kept for the farm's lifetime record. A sale carries the profit it made over the shares' average cost. */
@Serializable
data class StockTrade(
    val ticker: String,
    val isBuy: Boolean,
    val shares: Long,
    val priceCents: Long,
    @Serializable(with = InstantSerializer::class) val at: Instant,
    /** Zero for a buy; for a sale, what it paid above (or below, if negative) what those shares cost. */
    val profitCents: Long = 0,
)

/** The whole shares of one ticker the player owns. Money is kept in whole cents so buying and selling never drift. */
@Serializable
data class Holding(
    val ticker: String,
    val shares: Long,
    val averageCostCents: Long,
    val lastPriceCents: Long,
    @Serializable(with = InstantSerializer::class) val quotedAt: Instant,
    val previousCloseCents: Long? = null,
) {
    val valueCents: Long get() = shares * lastPriceCents
}
