package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.catalog.WaterStyle
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class RGB(
    val red: Int,
    val green: Int,
    val blue: Int,
) {
    fun packed(alpha: Int = 255) = rgba(red, green, blue, alpha)

    fun washed() =
        RGB(
            (red + (255 - red) * 0.6).roundToInt(),
            (green + (255 - green) * 0.6).roundToInt(),
            (blue + (255 - blue) * 0.6).roundToInt(),
        )
}

internal object WaterPalette {
    fun colors(style: WaterStyle): Pair<RGB, RGB> =
        when (style) {
            WaterStyle.deep -> RGB(16, 88, 136) to RGB(64, 148, 208)
            WaterStyle.medium -> RGB(24, 112, 156) to RGB(82, 182, 222)
            WaterStyle.shallow -> RGB(40, 132, 189) to RGB(108, 179, 224)
        }
}

internal object WaterDrops {
    const val SIZE = TILE / 16
    val colors = WaterStyle.entries.flatMap { WaterPalette.colors(it).toList() }
    val spray = colors.map { it.washed() }
    val foam = listOf(RGB(237, 247, 255), RGB(204, 230, 255), RGB(173, 214, 250))
}

/** Unsigned LCG, 16-square cells and 60-frame loop; the constants are part of the look. */
internal object WaterAnimation {
    const val SIDE = 16
    const val FRAMES = 60
    const val PHASE_GROUPS = 6
    private val phases =
        WaterStyle.entries.associateWith { style ->
            var seed =
                when (style) {
                    WaterStyle.deep -> 1L
                    WaterStyle.medium -> 2L
                    WaterStyle.shallow -> 3L
                }
            DoubleArray(SIDE * SIDE) {
                seed = seed * 6364136223846793005L + 1442695040888963407L
                (seed ushr 11).toDouble() / (1L shl 53).toDouble()
            }
        }

    fun frame(
        time: Double,
        phaseGroup: Int,
    ): Int =
        Math
            .floorMod(
                kotlin.math.floor(time * FRAMES / 5).toLong() + phaseGroup * FRAMES / PHASE_GROUPS,
                FRAMES.toLong(),
            ).toInt()

    fun pixels(
        style: WaterStyle,
        frame: Int,
        glow: Boolean = false,
    ): IntArray {
        val (low, high) = WaterPalette.colors(style)
        return IntArray(SIDE * SIDE) { cell ->
            val mix = 0.5 + 0.5 * sin(2 * PI * (2.0 * frame / FRAMES + phases.getValue(style)[cell]))
            if (glow) {
                val strength = 0.4 * mix * mix
                rgba(
                    (70 * strength).roundToInt(),
                    (240 * strength).roundToInt(),
                    (255 * strength).roundToInt(),
                    (
                        255 *
                            strength
                    ).roundToInt(),
                )
            } else {
                fun blend(
                    a: Int,
                    b: Int,
                ) = (a + (b - a) * mix).roundToInt()
                rgba(blend(low.red, high.red), blend(low.green, high.green), blend(low.blue, high.blue))
            }
        }
    }
}
