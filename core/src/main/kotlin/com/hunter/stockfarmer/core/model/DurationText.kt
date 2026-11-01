package com.hunter.stockfarmer.core.model

import kotlin.math.ceil

/** At most two adjacent units, rounded up to whole seconds. */
val Double.durationText: String
    get() {
        val total = durationSeconds()
        val hours = total / 3600
        val minutes = total % 3600 / 60
        val seconds = total % 60
        val parts =
            when {
                hours > 0 -> listOf(hours to "h", minutes to "min")
                minutes > 0 -> listOf(minutes to "min", seconds to "sec")
                else -> listOf(seconds to "sec")
            }
        return parts.filter { it.first > 0 }.joinToString(" ") { "${it.first} ${it.second}" }.ifEmpty { "0 sec" }
    }

val Double.compactDurationText: String
    get() {
        val total = durationSeconds()
        val units =
            listOf(
                total / 86_400 to "d",
                total % 86_400 / 3600 to "h",
                total % 3600 / 60 to "m",
                total % 60 to "s",
            )
        val first = units.indexOfFirst { it.first > 0 }
        if (first < 0) return "0s"
        return units
            .drop(first)
            .take(2)
            .filter { it.first > 0 }
            .joinToString(" ") { "${it.first}${it.second}" }
    }

private fun Double.durationSeconds(): Long {
    val rounded = ceil(this)
    require(rounded.isFinite() && rounded >= Long.MIN_VALUE.toDouble() && rounded < -Long.MIN_VALUE.toDouble())
    return rounded.toLong().coerceAtLeast(0)
}
