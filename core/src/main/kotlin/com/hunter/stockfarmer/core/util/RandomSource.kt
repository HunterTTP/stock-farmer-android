package com.hunter.stockfarmer.core.util

import java.util.concurrent.ThreadLocalRandom

/**
 * A source of 64-bit random numbers, like Swift's `RandomNumberGenerator`. The helpers below follow the algorithms of
 * Swift's standard library so a seeded generator draws the same numbers the Swift rules drew.
 */
fun interface RandomSource {
    fun nextLong(): Long
}

object SystemRandomSource : RandomSource {
    override fun nextLong(): Long = ThreadLocalRandom.current().nextLong()
}

/** A uniform number in 0 until [upperBound], by Swift's multiply-and-reject method. [upperBound] is unsigned. */
fun RandomSource.nextBelow(upperBound: ULong): ULong {
    require(upperBound > 0uL) { "upperBound must be positive" }
    var random = nextLong()
    var low = random.toULong() * upperBound
    if (low < upperBound) {
        val threshold = (0uL - upperBound) % upperBound
        while (low < threshold) {
            random = nextLong()
            low = random.toULong() * upperBound
        }
    }
    return Math.unsignedMultiplyHigh(random, upperBound.toLong()).toULong()
}

/** A uniform Int in a closed range, as Swift's `Int.random(in:using:)`. */
fun RandomSource.nextInt(range: IntRange): Int {
    require(!range.isEmpty()) { "range must not be empty" }
    val span = (range.last.toLong() - range.first.toLong() + 1).toULong()
    return (range.first + nextBelow(span).toLong()).toInt()
}

/** A uniform Double in 0 until 1, using the top 53 bits as Swift's `Double.random(in:using:)` does. */
fun RandomSource.nextUnit(): Double = (nextLong().toULong() shr 11).toDouble() * UNIT_STEP

/** A uniform Double in [lower] until [upper]. */
fun RandomSource.nextDouble(
    lower: Double,
    upper: Double,
): Double = lower + (upper - lower) * nextUnit()

fun RandomSource.nextBoolean(): Boolean = nextLong() and 1L == 1L

/** The list in a random order, by the Fisher-Yates shuffle Swift's `shuffled(using:)` performs. */
fun <T> List<T>.shuffled(using: RandomSource): List<T> {
    val result = toMutableList()
    for (index in result.size - 1 downTo 1) {
        val other = using.nextInt(0..index)
        val held = result[index]
        result[index] = result[other]
        result[other] = held
    }
    return result
}

fun <T> List<T>.randomElement(using: RandomSource): T? = if (isEmpty()) null else this[using.nextInt(indices)]

private const val UNIT_STEP = 1.0 / (1L shl 53)
