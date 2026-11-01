package com.hunter.stockfarmer.farm

import kotlin.math.max

/** Presentation envelopes in seconds, independent of Android and the render clock. */
internal object VisualTiming {
    fun gridAlpha(
        scale: Float,
        maxScale: Float,
    ): Float = ((maxScale - scale) / (maxScale * 0.4f)).coerceIn(0f, 1f)

    fun previewAlpha(age: Double): Float = (1 - max(0.0, age - 0.35) / 0.35).coerceIn(0.0, 1.0).toFloat()

    fun growthAlpha(age: Double): Float = (age / 1.4).coerceIn(0.0, 1.0).toFloat()

    fun growthGlow(age: Double): Float =
        when {
            age < 0 -> 0f
            age < 0.6 -> (0.5 * easeOut(age / 0.6)).toFloat()
            age < 4.6 -> 0.5f
            age < 7 -> (0.5 * (1 - easeIn((age - 4.6) / 2.4))).toFloat()
            else -> 0f
        }

    fun bob(age: Double): Float {
        val phase = age.coerceAtLeast(0.0) % 1.2
        return (if (phase < 0.6) phase * 10 else (1.2 - phase) * 10).toFloat()
    }

    // SpriteKit's ease-in/out uses a sinusoidal timing curve.
    private fun easeIn(t: Double): Double = 1 - kotlin.math.cos(t * Math.PI / 2)

    private fun easeOut(t: Double): Double = kotlin.math.sin(t * Math.PI / 2)
}
