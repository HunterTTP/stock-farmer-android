package com.hunter.stockfarmer.core.market

@Suppress("ktlint:standard:enum-entry-name-case")
enum class StockQuoteError(
    val message: String,
) {
    invalidTicker("No stock found with that ticker"),
    unavailable("No price is available for that stock right now"),
    network("Couldn't reach the market. Check your connection and try again"),
}
