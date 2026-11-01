package com.hunter.stockfarmer.farm

import kotlin.math.roundToInt

/** The reference's 48 px, staggered stones, rasterized without antialiasing. Coordinates count down the image. */
internal object CobblestonePath {
    private val widths = listOf(listOf(15, 19, 14), listOf(20, 13, 15), listOf(14, 17, 17), listOf(17, 15, 16))

    fun shade(
        x: Int,
        y: Int,
    ): Double {
        val row = y / 12
        for (repeat in -1..1) {
            var left = repeat * 48 + (row % 2) * 8
            for ((column, width) in widths[row].withIndex()) {
                val px = x + 0.5 - (left + 1)
                val py = y + 0.5 - (row * 12 + 1)
                val right = width - 1.0
                val polygon =
                    listOf(
                        2.0 to 0.0,
                        right - 3 to 0.0,
                        right to 2.0,
                        right - 1 to 9.0,
                        right - 3 to 11.0,
                        1.0 to 10.0,
                        0.0 to 3.0,
                    )
                val shade = 0.45 + ((row * 2 + column * 3) % 5) * 0.035
                if (py > 0.5 && py <= 1.5 && px >= 3 && px < right - 4) return shade + 0.12
                var inside = false
                var previous = polygon.last()
                for (next in polygon) {
                    if ((next.second > py) != (previous.second > py) &&
                        px < (previous.first - next.first) * (py - next.second) /
                        (previous.second - next.second) + next.first
                    ) {
                        inside = !inside
                    }
                    previous = next
                }
                if (inside) return shade
                left += width
            }
        }
        return 0.25
    }

    fun pixel(
        x: Int,
        y: Int,
        grainLuminance: Double?,
    ): Int {
        val stone = shade(x, y)
        val value =
            ((if (grainLuminance == null) stone else stone * 0.84 + grainLuminance * 0.16) * 255)
                .roundToInt()
                .coerceIn(0, 255)
        return (value shl 24) or (value shl 16) or (value shl 8) or 255
    }
}
