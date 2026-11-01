package com.hunter.stockfarmer.farm.animals

import kotlin.math.max

/** Alpha bounds, normalized with y counting up from the frame's bottom, as in the iOS footprint measurement. */
internal data class AnimalFootprint(
    val centerX: Float,
    val bottom: Float,
    val width: Float,
    val bodyCenterX: Float,
    val bodyTop: Float,
) {
    fun center(
        waterline: Float?,
        tail: Boolean,
    ): Pair<Float, Float> {
        val visibleBottom = if (waterline == null || tail) bottom else max(bottom, 1 - waterline)
        return bodyCenterX to (visibleBottom + bodyTop) / 2
    }

    companion object {
        fun measure(
            width: Int,
            height: Int,
            alpha: (Int, Int) -> Int,
        ): AnimalFootprint? {
            val solidRows = (0 until height).filter { y -> (0 until width).any { x -> alpha(x, y) > 40 } }
            if (solidRows.isEmpty()) return null
            val lowest = solidRows.last()
            val feetTop = max(0, lowest - max(2, height / 16))
            var left = width
            var right = -1
            var leftFoot = width
            var rightFoot = -1
            for (y in solidRows) {
                for (x in 0 until width) {
                    if (alpha(x, y) <= 40) continue
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    if (y >= feetTop) {
                        leftFoot = minOf(leftFoot, x)
                        rightFoot = maxOf(rightFoot, x)
                    }
                }
            }
            return AnimalFootprint(
                ((leftFoot + rightFoot) / 2f + 0.5f) / width,
                (height - 1 - lowest).toFloat() / height,
                (right - left + 1).toFloat() / width,
                ((left + right) / 2f + 0.5f) / width,
                (height - solidRows.first()).toFloat() / height,
            )
        }
    }
}
