package com.hunter.stockfarmer.market

import com.hunter.stockfarmer.core.rules.StockMarketRules
import java.time.Instant

/** The pause between price pulls while the Stock Market is open: at least a second, and longer with nothing owned. */
object StockPolling {
    const val IDLE_EXTRA_SECONDS = 4.0

    fun waitSeconds(
        lastPullAt: Instant?,
        now: Instant,
        ownsNothing: Boolean,
    ): Double =
        StockMarketRules.secondsUntilPull(lastPullAt, now).coerceAtLeast(1.0) +
            if (ownsNothing) IDLE_EXTRA_SECONDS else 0.0
}
