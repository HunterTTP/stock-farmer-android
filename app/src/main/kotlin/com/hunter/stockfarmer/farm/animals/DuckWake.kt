package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.farm.effects.WaterDrops
import kotlin.math.hypot
import kotlin.random.Random

internal data class WakePixel(
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float,
    val tint: Int,
)

/** Six foam pixels per puff, every 0.07 seconds, drifting away for 0.7 seconds, in the foam and spray colors of [WaterDrops]. */
internal class DuckWake(
    seed: Int,
) {
    private data class Particle(
        val born: Double,
        val x: Float,
        val y: Float,
        val dx: Float,
        val dy: Float,
        val swayX: Float,
        val swayY: Float,
        val size: Float,
        val alpha: Float,
        val tint: Int,
    )

    private val random = Random(seed)
    private val particles = mutableListOf<Particle>()
    private var emitted = Double.NEGATIVE_INFINITY

    fun update(
        seconds: Double,
        x: Float,
        y: Float,
        facing: Facing,
        walking: Boolean,
    ): List<WakePixel> {
        if (seconds < emitted) {
            particles.clear()
            emitted = Double.NEGATIVE_INFINITY
        }
        particles.removeAll { seconds - it.born >= LIFE }
        if (walking && seconds - emitted >= INTERVAL) {
            emit(seconds, x, y, facing)
            emitted = seconds
        }
        return particles.map { pixel ->
            val age = ((seconds - pixel.born) / LIFE).coerceIn(0.0, 1.0).toFloat()
            val first = easeOut((age / 0.4f).coerceAtMost(1f))
            val second = easeOut(((age - 0.4f) / 0.6f).coerceAtLeast(0f))
            WakePixel(
                pixel.x + pixel.dx * (first + second) / 2 + pixel.swayX * (first - second),
                pixel.y + pixel.dy * (first + second) / 2 + pixel.swayY * (first - second),
                pixel.size,
                pixel.alpha * (1 - age),
                pixel.tint,
            )
        }
    }

    private fun emit(
        seconds: Double,
        x: Float,
        y: Float,
        facing: Facing,
    ) {
        val dx =
            when (facing) {
                Facing.RIGHT -> 1f
                Facing.LEFT -> -1f
                else -> 0f
            }
        val dy =
            when (facing) {
                Facing.UP -> 1f
                Facing.DOWN -> -1f
                else -> 0f
            }
        val sideX = -dy
        val sideY = dx * 0.4f
        val sideLength = hypot(sideX, sideY).coerceAtLeast(0.001f)
        for (sign in listOf(-1, 1)) {
            repeat(3) {
                val behind = random.nextDouble(0.2, 0.45).toFloat()
                val out = random.nextDouble(0.85, 1.15).toFloat()
                val reach = random.nextDouble(0.25, 0.45).toFloat()
                val spread = random.nextDouble(0.04, 0.14).toFloat()
                val stray = random.nextDouble(-4.0, 4.0).toFloat()
                particles +=
                    Particle(
                        seconds,
                        x + (sideX * sign * out - dx * behind) * halfWidth + jitter(),
                        y + (sideY * sign * out - dy * behind) * halfWidth + jitter(),
                        (-dx * reach + sideX * sign * spread) * 100,
                        (-dy * reach + sideY * sign * spread) * 100,
                        sideX / sideLength * stray,
                        sideY / sideLength * stray,
                        WaterDrops.SIZE.toFloat(),
                        random.nextDouble(0.7, 0.95).toFloat(),
                        random.nextInt(WaterDrops.wake.size),
                    )
            }
        }
    }

    var halfWidth: Float = 0f

    private fun jitter(): Float = random.nextDouble(-2.0, 2.0).toFloat()

    private fun easeOut(t: Float): Float = 1 - (1 - t) * (1 - t)

    companion object {
        const val INTERVAL = 0.07
        const val LIFE = 0.7
    }
}
