package com.hunter.stockfarmer.audio

/** Retargeting a fade begins at its current value, including when midnight is skipped by hand. */
internal class VolumeFade {
    private var from = 0.0
    private var target = 0.0
    private var started = 0.0
    private var duration = 0.0

    fun value(at: Double): Double {
        if (duration <= 0.0) return target
        val progress = ((at - started) / duration).coerceIn(0.0, 1.0)
        return from + (target - from) * progress
    }

    fun set(
        target: Double,
        duration: Double,
        at: Double,
    ) {
        from = value(at)
        this.target = target.coerceIn(0.0, 1.0)
        this.duration = duration.coerceAtLeast(0.0)
        started = at
    }

    fun isFinished(at: Double): Boolean = at >= started + duration
}
