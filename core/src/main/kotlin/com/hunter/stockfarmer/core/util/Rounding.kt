package com.hunter.stockfarmer.core.util

import kotlin.math.abs
import kotlin.math.floor

/** Rounds halves away from zero, as Swift's `rounded()` does; Kotlin's own rounding sends halves to even or up. */
fun Double.roundedHalfAwayFromZero(): Double {
    if (isNaN() || isInfinite()) return this
    val magnitude = abs(this)
    val whole = floor(magnitude)
    val rounded = if (magnitude - whole >= 0.5) whole + 1 else whole
    return Math.copySign(rounded, this)
}
