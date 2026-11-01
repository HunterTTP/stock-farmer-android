package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor

val Double.roundedToCents: Double get() = (this * 100).roundedHalfAwayFromZero() / 100

private val compactSizes = listOf(1e9 to "B", 1e6 to "M", 1e3 to "K")

/** Thousands, millions and billions with their zeros dropped: "$25", "$14K", "$1.5K", "$2M". */
val Double.compactDollars: String
    get() {
        val (size, suffix) = compactSizes.firstOrNull { this >= it.first } ?: return dollars
        val tenths = floor(this / size * 10) / 10
        val isWhole = tenths.roundedHalfAwayFromZero() == tenths
        val text = if (isWhole) tenths.toLong().toString() else "%.1f".format(Locale.US, tenths)
        return "$$text$suffix"
    }

/** Whole dollars when possible, cents otherwise: "$25", "$1,000", "$0.50". */
val Double.dollars: String
    get() {
        val digits = if (roundedHalfAwayFromZero() == this) 0 else 2
        val format = NumberFormat.getCurrencyInstance(Locale.US)
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(this)
    }
