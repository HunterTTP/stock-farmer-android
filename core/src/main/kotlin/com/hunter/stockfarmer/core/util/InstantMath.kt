package com.hunter.stockfarmer.core.util

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Seconds from [earlier] to this moment, as Swift's `timeIntervalSince` gives them. */
fun Instant.secondsSince(earlier: Instant): Double = Duration.between(earlier, this).toNanos() / 1e9

/**
 * This moment moved on by [seconds], which may be fractional or negative, as Swift's `addingTimeInterval`. The result is
 * kept to the microsecond, which a saved `Double` of seconds holds exactly, so a time survives a save unchanged.
 */
fun Instant.advancedBy(seconds: Double): Instant = plusNanos((seconds * 1e9).toLong()).roundedToMicroseconds()

/** The moment rounded to the nearest microsecond. */
fun Instant.roundedToMicroseconds(): Instant = plusNanos(500).truncatedTo(ChronoUnit.MICROS)

/** Seconds since 1970, as Swift's `timeIntervalSince1970`. */
val Instant.epochSecondsDouble: Double get() = epochSecond + nano / 1e9
