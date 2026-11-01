package com.hunter.stockfarmer.farm.animals

import kotlin.math.cos

internal data class FishOrientation(
    val mirrored: Boolean,
    val degrees: Float,
)

internal fun fishOrientation(
    kind: String,
    facing: Facing,
    mirrored: Boolean,
): FishOrientation {
    if (kind in listOf("jellyfish", "lobster", "octopus", "squid")) {
        return FishOrientation(
            false,
            when (facing) {
                Facing.LEFT -> 90f
                Facing.RIGHT -> -90f
                Facing.DOWN -> 180f
                Facing.UP -> 0f
            },
        )
    }
    val mirror =
        when (facing) {
            Facing.RIGHT -> true
            Facing.LEFT -> false
            else -> mirrored
        }
    val angle =
        when (facing) {
            Facing.UP -> if (mirror) 90f else -90f
            Facing.DOWN -> if (mirror) -90f else 90f
            else -> 0f
        }
    return FishOrientation(mirror, angle)
}

/** The two eased scale actions on the shadow: 1 -> 1.04 in 1.1s, then -> 0.97 in 1.3s. */
internal fun fishBreathing(seconds: Double): Float {
    val age = seconds.coerceAtLeast(0.0)
    val phase = age % 2.4
    val rising = phase < 1.1
    val t = if (rising) phase / 1.1 else (phase - 1.1) / 1.3
    val ease = ((1 - cos(Math.PI * t)) / 2).toFloat()
    return if (rising) {
        (if (age < 2.4) 1f else 0.97f) +
            (if (age < 2.4) 0.04f else 0.07f) * ease
    } else {
        1.04f - 0.07f * ease
    }
}

internal fun turnDegrees(
    from: Float,
    to: Float,
    seconds: Double,
): Float {
    val difference = ((to - from + 540) % 360) - 180
    return from + difference * (seconds / 0.6).coerceIn(0.0, 1.0).toFloat()
}
