package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.rules.ShadowProjection
import com.hunter.stockfarmer.core.rules.SunShadow
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
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

    fun render(
        shape: ShadowShape,
        sun: SunShadow,
        intensity: Double,
        mask: AlphaMask? = null,
    ): ShadowImage? {
        if (shape.width <= 0 || shape.height <= 0) return null
        val softnessScale = 1.6 - 1.2 * (intensity * 4).let { floor(it + 0.5) / 4 }
        val projection = ShadowProjection(sun.dx to sun.dy, sun.length * 0.4 * if (shape.picture) 2 else 1, 0.55)
        val tipX = projection.leanX * shape.height
        val tipY = projection.leanY * shape.height
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
                        val up = (y - shape.castFrom) / projection.leanY
                        val across = x - up * projection.leanX
                        mask?.at((across + shape.width / 2) / shape.width, up / shape.height) ?: 0.0
                    } else if (inBlock(x, y, shape, projection)) {
                        1.0
                    } else {
                        0.0
                    }
                silhouette[row * width + col] = alpha.toFloat()
            }
        }
        val offsets =
            listOf(0 to 0) +
                listOf(spread, spread / 2).flatMap { ring ->
                    (0 until 12).map { step ->
                        val angle = step * Math.PI / 6
                        floor(ring * RESOLUTION * cos(angle) + 0.5).toInt() to
                            floor(ring * RESOLUTION * sin(angle) + 0.5).toInt()
                    }
                }
        var blurred =
            FloatArray(width * height) { index ->
                val x = index % width
                val y = index / width
                offsets.maxOf { (dx, dy) ->
                    if (x + dx in 0 until width &&
                        y + dy in 0 until height
                    ) {
                        silhouette[(y + dy) * width + x + dx]
                    } else {
                        0f
                    }
                }
            }
        val radius = maxOf(1, (blur * RESOLUTION / 3).toInt())
        repeat(3) { blurred = boxBlur(boxBlur(blurred, width, height, radius, true), width, height, radius, false) }
        for (row in 0 until height) {
            val y = top - (row + 0.5) / RESOLUTION
            for (col in 0 until width) {
                val x = left + (col + 0.5) / RESOLUTION
                val tipLength = hypot(tipX, tipY)
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

    private fun inBlock(
        x: Double,
        y: Double,
        shape: ShadowShape,
        projection: ShadowProjection,
    ): Boolean {
        var low = 0.0
        var high = shape.height

        fun clip(
            value: Double,
            from: Double,
            to: Double,
            lean: Double,
        ): Boolean {
            if (kotlin.math.abs(lean) < 1e-9) return value in from..to
            val a = (value - from) / lean
            val b = (value - to) / lean
            low = maxOf(low, minOf(a, b))
            high = minOf(high, maxOf(a, b))
            return low <= high
        }
        return clip(x, shape.baseLeft, shape.baseLeft + shape.width, projection.leanX) &&
            clip(y, shape.baseBottom, shape.baseBottom + shape.baseHeight, projection.leanY)
    }

    private fun boxBlur(
        input: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        horizontal: Boolean,
    ): FloatArray {
        val result = FloatArray(input.size)
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val stride = if (horizontal) 1 else width
        for (line in 0 until lines) {
            val start = if (horizontal) line * width else line
            val prefix = DoubleArray(length + 1)
            for (i in 0 until length) prefix[i + 1] = prefix[i] + input[start + i * stride]
            for (i in 0 until length) {
                val a = maxOf(0, i - radius)
                val b = minOf(length, i + radius + 1)
                result[start + i * stride] = ((prefix[b] - prefix[a]) / (radius * 2 + 1)).toFloat()
            }
        }
        return result
    }
}
