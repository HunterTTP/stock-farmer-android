package com.hunter.stockfarmer.core.market

import com.hunter.stockfarmer.core.model.StockQuote
import com.hunter.stockfarmer.core.rules.StockMarketRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant

/** Thrown when a quote can't be had; the [error] says why. */
class StockQuoteException(
    val error: StockQuoteError,
) : Exception(error.message)

/** Where prices come from, so the source can be replaced without touching the trading rules. */
fun interface StockQuoteProvider {
    suspend fun quote(ticker: String): StockQuote
}

/** Reads the price out of a Yahoo Finance chart response. */
object YahooChartParser {
    fun quote(
        ticker: String,
        data: String,
        fallbackDate: Instant,
    ): StockQuote {
        val chart =
            runCatching { (Json.parseToJsonElement(data) as JsonObject)["chart"] as? JsonObject }.getOrNull()
                ?: throw StockQuoteException(StockQuoteError.unavailable)
        val metas = chart["result"].metas()
        if (chart["error"].isPresent() &&
            chart["error"] !is JsonObject
        ) {
            throw StockQuoteException(StockQuoteError.unavailable)
        }
        val meta = metas.firstOrNull()
        if (chart["error"].isPresent() || meta == null) throw StockQuoteException(StockQuoteError.invalidTicker)
        val price = meta.number("regularMarketPrice")
        if (price == null || !price.isFinite() || price <= 0) throw StockQuoteException(StockQuoteError.unavailable)
        val cents = StockMarketRules.cents(price)
        if (cents <= 0 ||
            cents > StockMarketRules.MAX_PRICE_CENTS
        ) {
            throw StockQuoteException(StockQuoteError.unavailable)
        }
        val previousClose =
            (meta.number("chartPreviousClose") ?: meta.number("previousClose"))
                ?.takeIf { it.isFinite() && it > 0 }
                ?.let(StockMarketRules::cents)
        return StockQuote(
            ticker = ticker,
            priceCents = cents,
            quotedAt =
                meta.number("regularMarketTime")?.let { Instant.ofEpochMilli((it * 1000).toLong()) } ?: fallbackDate,
            previousCloseCents = previousClose?.takeIf { it <= StockMarketRules.MAX_PRICE_CENTS },
        )
    }

    private fun JsonElement?.isPresent() = this != null && this !is JsonNull

    /** Every result's meta; a result shaped any other way fails the whole decode, as it does on iOS. */
    private fun JsonElement?.metas(): List<JsonObject> {
        if (!isPresent()) return emptyList()
        val results = this as? JsonArray ?: throw StockQuoteException(StockQuoteError.unavailable)
        return results.map { result ->
            val meta = (result as? JsonObject)?.get("meta") as? JsonObject
            if (meta == null || !meta.hasOnlyNumbers()) throw StockQuoteException(StockQuoteError.unavailable)
            meta
        }
    }

    private fun JsonObject.hasOnlyNumbers() =
        NUMBER_KEYS.all { key ->
            val value = this[key]
            !value.isPresent() || ((value as? JsonPrimitive)?.let { !it.isString && it.doubleOrNull != null } ?: false)
        }

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private val NUMBER_KEYS = listOf("regularMarketPrice", "regularMarketTime", "chartPreviousClose", "previousClose")
}
