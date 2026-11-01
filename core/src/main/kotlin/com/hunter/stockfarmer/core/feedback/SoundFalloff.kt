package com.hunter.stockfarmer.core.feedback

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Sounds are fully loud across the visible view only while the camera is zoomed well in. */
object SoundFalloff {
    private val fullVolumeZoomFraction = 0.2
    private val zoomedOutGain = 0.4
    private val farAwayGain = 0.1
    private val minimumGain = min(zoomedOutGain, farAwayGain)
    private val zoomSharpness = 4.0
    private val distanceFadeLength = 2.5

    fun gain(
        zoomFraction: Double,
        horizontalOffset: Double,
        verticalOffset: Double,
        viewWidth: Double,
        viewHeight: Double,
    ): Double {
        val zoom = min(max(zoomFraction, 0.0), 1.0)
        val zoomFade = max(0.0, zoom - fullVolumeZoomFraction) / (1 - fullVolumeZoomFraction)
        val zoomGain = zoomedOutGain + (1 - zoomedOutGain) * (1 - zoomFade).pow(zoomSharpness)
        if (!(viewWidth > 0 && viewHeight > 0)) return zoomGain
        val outsideX = max(0.0, abs(horizontalOffset) - viewWidth / 2)
        val outsideY = max(0.0, abs(verticalOffset) - viewHeight / 2)
        val distance = hypot(outsideX, outsideY) / viewHeight
        val away = min(distance / distanceFadeLength, 1.0)
        val distanceGain = 1 - (1 - farAwayGain) * away
        return max(zoomGain * distanceGain, minimumGain)
    }
}
