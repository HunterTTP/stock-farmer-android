package com.hunter.stockfarmer.core.util

/** The cubic ease between 0 and 1 that starts and ends flat: `x * x * (3 - 2 * x)`. */
fun smoothstep(x: Double): Double = x * x * (3 - 2 * x)
