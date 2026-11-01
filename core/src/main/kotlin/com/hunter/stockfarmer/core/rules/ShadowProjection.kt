package com.hunter.stockfarmer.core.rules

/** The picture stays upright, squashed and leaning over, with its foot under the caster's. */
data class ShadowProjection(
    val leanX: Double,
    val leanY: Double,
) {
    constructor(direction: Pair<Double, Double>, length: Double, slant: Double) :
        this(length * direction.first, length * direction.second * slant)

    fun place(
        across: Double,
        up: Double,
    ): Pair<Double, Double> = (across + up * leanX) to (up * leanY)
}
