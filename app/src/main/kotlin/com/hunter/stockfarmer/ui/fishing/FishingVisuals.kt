package com.hunter.stockfarmer.ui.fishing

import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.rules.FishingContest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Screen points, including system bars. The arena stays fixed during a resize so the contest never restarts. */
internal data class FishingArea(
    val width: Double,
    val height: Double,
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
    val controlScale: Double = 1.0,
) {
    val bounds: FishingContest.Bounds
        get() {
            val minX = (left + 68).coerceAtMost(width / 2 - 0.5)
            val maxX = (width - right - 68).coerceAtLeast(minX + 1)
            val minY = (top + 128 * controlScale + 68).coerceAtMost(height / 2 - 0.5)
            val maxY = (height - bottom - 102 * controlScale - 68).coerceAtLeast(minY + 1)
            return FishingContest.Bounds(minX, maxX, minY, maxY)
        }
}

internal data class FishingFishPose(
    val mirrored: Boolean,
    val degrees: Float,
)

/** Canvas angles turn downwards; the source fish face left, except the four upright species. */
internal fun fishingFishPose(
    kindID: String,
    heading: Double,
): FishingFishPose {
    if (kindID in listOf("jellyfish", "lobster", "octopus", "squid")) {
        return FishingFishPose(false, Math.toDegrees(heading + PI / 2).toFloat())
    }
    return if (cos(heading) >= 0) {
        FishingFishPose(true, Math.toDegrees(heading).toFloat())
    } else {
        FishingFishPose(false, Math.toDegrees(heading - if (heading > 0) PI else -PI).toFloat())
    }
}

/** A 16 by 16 shimmer, five-second loop and six phase groups. */
internal class FishingWater(
    style: WaterStyle,
) {
    private val phases: DoubleArray
    private val low: IntArray
    private val high: IntArray

    init {
        var seed = style.ordinal.toLong() + 1
        phases =
            DoubleArray(256) {
                seed = seed * 6364136223846793005L + 1442695040888963407L
                (seed.toULong() shr 11).toDouble() / (1L shl 53).toDouble()
            }
        val colors =
            when (style) {
                WaterStyle.deep -> intArrayOf(16, 88, 136) to intArrayOf(64, 148, 208)
                WaterStyle.medium -> intArrayOf(24, 112, 156) to intArrayOf(82, 182, 222)
                WaterStyle.shallow -> intArrayOf(40, 132, 189) to intArrayOf(108, 179, 224)
            }
        low = colors.first
        high = colors.second
    }

    fun pixels(
        frame: Int,
        glowing: Boolean = false,
    ): IntArray =
        IntArray(256) { index ->
            val mix = 0.5 + 0.5 * sin(2 * PI * (2.0 * frame / 60 + phases[index]))
            if (glowing) {
                val glow = 0.4 * mix * mix
                argb((255 * glow).roundToInt(), 70, 240, 255)
            } else {
                val channels = IntArray(3) { (low[it] + (high[it] - low[it]) * mix).roundToInt() }
                argb(255, channels[0], channels[1], channels[2])
            }
        }

    companion object {
        fun frame(
            seconds: Double,
            column: Int,
            row: Int,
        ): Int {
            val group = Math.floorMod(column * 31 + row * 17, 6)
            return ((seconds % 5 / 5 * 60).toInt() + group * 10) % 60
        }

        private fun argb(
            alpha: Int,
            red: Int,
            green: Int,
            blue: Int,
        ): Int = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
