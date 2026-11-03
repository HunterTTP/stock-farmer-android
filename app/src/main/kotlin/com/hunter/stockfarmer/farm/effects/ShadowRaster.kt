package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.rules.ShadowProjection
import com.hunter.stockfarmer.core.rules.SunShadow
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class ShadowKind(
    val opacity: Double,
) {
    BUILDING(0.186),
    TREE(0.174),
    FRUIT_TREE(0.174),
    ROCK(0.156),
    DECORATION(0.156),
    FENCE(0.135),
}

internal data class AlphaMask(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
) {
    fun at(
        x: Double,
        y: Double,
    ): Double {
        if (x < 0 || x >= 1 || y < 0 || y >= 1) return 0.0
        val column = (x * width).toInt().coerceIn(0, width - 1)
        val row = ((1 - y) * height).toInt().coerceIn(0, height - 1)
        return (pixels[row * width + column].toInt() and 255) / 255.0
    }
}

internal data class ShadowShape(
    val kind: ShadowKind,
    val width: Double,
    val height: Double,
    val image: String? = null,
    val baseLeft: Double = -width / 2,
    val baseBottom: Double =
        when (kind) {
            ShadowKind.BUILDING -> {
                height * 0.15
            }

            ShadowKind.FENCE -> {
                0.0
            }

            else -> {
                height *
                    0.2
            }
        },
    val baseHeight: Double = if (kind == ShadowKind.BUILDING) height * 0.35 else 0.0,
) {
    val picture get() = image != null
    val castFrom get() = baseBottom + baseHeight
}

internal data class ShadowImage(
    val width: Int,
    val height: Int,
    val left: Double,
    val bottom: Double,
    val alpha: FloatArray,
)

/** Low-resolution CPU silhouettes, grown and blurred once per shape and sun step, never per caster. */
internal object ShadowRaster {
    const val RESOLUTION = 0.25

    private const val RING_STEPS = 12
    private const val OFFSETS = 1 + 2 * RING_STEPS
    private const val NO_LEAN = 1e-9

    fun render(
        shape: ShadowShape,
        sun: SunShadow,
        intensity: Double,
        mask: AlphaMask? = null,
    ): ShadowImage? {
        if (shape.width <= 0 || shape.height <= 0) return null
        val softnessScale = 1.6 - 1.2 * (intensity * 4).let { floor(it + 0.5) / 4 }
        val projection = ShadowProjection(sun.dx to sun.dy, sun.length * 0.4 * if (shape.picture) 2 else 1, 0.55)
        val leanX = projection.leanX
        val leanY = projection.leanY
        val tipX = leanX * shape.height
        val tipY = leanY * shape.height
        val blur = maxOf(14.0, minOf(shape.width, shape.height) * 0.3) * softnessScale
        val spread = 24 * softnessScale
        val margin = blur * 1.5 + spread
        val left = minOf(shape.baseLeft, shape.baseLeft + tipX) - margin
        val right = maxOf(shape.baseLeft + shape.width, shape.baseLeft + shape.width + tipX) + margin
        val bottom = maxOf(0.0, minOf(shape.baseBottom, shape.baseBottom + tipY) - margin)
        val top = maxOf(shape.baseBottom + shape.baseHeight, shape.baseBottom + shape.baseHeight + tipY) + margin
        val width = ceil((right - left) * RESOLUTION).toInt()
        val height = ceil((top - bottom) * RESOLUTION).toInt()
        if (width <= 0 || height <= 0) return null
        val silhouette = FloatArray(width * height)
        for (row in 0 until height) {
            val y = top - (row + 0.5) / RESOLUTION
            for (col in 0 until width) {
                val x = left + (col + 0.5) / RESOLUTION
                val alpha =
                    if (shape.picture) {
                        if (mask != null) {
                            val up = (y - shape.castFrom) / leanY
                            val across = x - up * leanX
                            mask.at((across + shape.width / 2) / shape.width, up / shape.height)
                        } else {
                            0.0
                        }
                    } else if (inBlock(x, y, shape, leanX, leanY)) {
                        1.0
                    } else {
                        0.0
                    }
                silhouette[row * width + col] = alpha.toFloat()
            }
        }
        val blurred = FloatArray(width * height)
        grow(silhouette, blurred, width, height, spread)
        val radius = maxOf(1, (blur * RESOLUTION / 3).toInt())
        val prefix = DoubleArray(maxOf(width, height) + 1)
        repeat(3) {
            boxBlur(blurred, silhouette, width, height, radius, true, prefix)
            boxBlur(silhouette, blurred, width, height, radius, false, prefix)
        }
        val tipLength = hypot(tipX, tipY)
        for (row in 0 until height) {
            val y = top - (row + 0.5) / RESOLUTION
            for (col in 0 until width) {
                val x = left + (col + 0.5) / RESOLUTION
                val along =
                    if (tipLength >
                        0
                    ) {
                        ((x * tipX + (y - shape.castFrom) * tipY) / (tipLength * tipLength)).coerceIn(0.0, 1.0)
                    } else {
                        0.0
                    }
                val edge =
                    if (shape.kind ==
                        ShadowKind.FENCE
                    ) {
                        1.0
                    } else if (tipX <=
                        0
                    ) {
                        ((shape.baseLeft + shape.width - x) / blur).coerceIn(0.0, 1.0)
                    } else {
                        (
                            (x - shape.baseLeft) /
                                blur
                        ).coerceIn(0.0, 1.0)
                    }
                blurred[row * width + col] *= ((1 - 0.65 * along) * (y / blur).coerceIn(0.0, 1.0) * edge).toFloat()
            }
        }
        return ShadowImage(width, height, left, top - height / RESOLUTION, blurred)
    }

    /** Each output pixel takes the strongest input at the center or on either of two rings of 12 offsets around it. */
    private fun grow(
        input: FloatArray,
        output: FloatArray,
        width: Int,
        height: Int,
        spread: Double,
    ) {
        val offsetX = IntArray(OFFSETS)
        val offsetY = IntArray(OFFSETS)
        for (step in 0 until RING_STEPS) {
            val angle = step * Math.PI / 6
            offsetX[1 + step] = floor(spread * RESOLUTION * cos(angle) + 0.5).toInt()
            offsetY[1 + step] = floor(spread * RESOLUTION * sin(angle) + 0.5).toInt()
            offsetX[1 + RING_STEPS + step] = floor(spread / 2 * RESOLUTION * cos(angle) + 0.5).toInt()
            offsetY[1 + RING_STEPS + step] = floor(spread / 2 * RESOLUTION * sin(angle) + 0.5).toInt()
        }
        // Most of a silhouette's box is empty, so spreading from the filled pixels beats gathering at every pixel.
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = input[y * width + x]
                if (value <= 0f) continue
                for (offset in 0 until OFFSETS) {
                    val toX = x - offsetX[offset]
                    val toY = y - offsetY[offset]
                    if (toX < 0 || toX >= width || toY < 0 || toY >= height) continue
                    val index = toY * width + toX
                    if (value > output[index]) output[index] = value
                }
            }
        }
    }

    private fun inBlock(
        x: Double,
        y: Double,
        shape: ShadowShape,
        leanX: Double,
        leanY: Double,
    ): Boolean {
        var low = 0.0
        var high = shape.height
        val right = shape.baseLeft + shape.width
        if (abs(leanX) < NO_LEAN) {
            if (x !in shape.baseLeft..right) return false
        } else {
            val a = (x - shape.baseLeft) / leanX
            val b = (x - right) / leanX
            low = maxOf(low, minOf(a, b))
            high = minOf(high, maxOf(a, b))
            if (low > high) return false
        }
        val top = shape.baseBottom + shape.baseHeight
        if (abs(leanY) < NO_LEAN) return y in shape.baseBottom..top
        val a = (y - shape.baseBottom) / leanY
        val b = (y - top) / leanY
        low = maxOf(low, minOf(a, b))
        high = minOf(high, maxOf(a, b))
        return low <= high
    }

    /** Writes into [output]; [prefix] is scratch space at least one longer than the longer side. */
    private fun boxBlur(
        input: FloatArray,
        output: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        horizontal: Boolean,
        prefix: DoubleArray,
    ) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val stride = if (horizontal) 1 else width
        for (line in 0 until lines) {
            val start = if (horizontal) line * width else line
            for (i in 0 until length) prefix[i + 1] = prefix[i] + input[start + i * stride]
            for (i in 0 until length) {
                val a = maxOf(0, i - radius)
                val b = minOf(length, i + radius + 1)
                output[start + i * stride] = ((prefix[b] - prefix[a]) / (radius * 2 + 1)).toFloat()
            }
        }
    }
}

/** Writes a shadow as black with its alpha, in the R, G, B, A byte order of an RGBA8888 pixmap's memory. */
internal fun ShadowImage.writeRgba(pixels: ByteBuffer) {
    for (index in alpha.indices) {
        val at = index * 4
        pixels.put(at, 0)
        pixels.put(at + 1, 0)
        pixels.put(at + 2, 0)
        pixels.put(at + 3, (alpha[index] * 255).roundToInt().coerceIn(0, 255).toByte())
    }
}

/** The alpha of each pixel in an RGBA8888 pixmap's memory, where every fourth byte is alpha. */
internal fun rgbaAlphaBytes(
    pixels: ByteBuffer,
    width: Int,
    height: Int,
): ByteArray = ByteArray(width * height) { index -> pixels.get(index * 4 + 3) }
