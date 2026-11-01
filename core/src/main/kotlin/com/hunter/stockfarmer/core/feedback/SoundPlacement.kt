package com.hunter.stockfarmer.core.feedback

import kotlin.math.max
import kotlin.math.min

data class SoundPlacement(
    val gain: Double,
    val pan: Double,
    val isCloseUp: Boolean,
) {
    constructor(
        zoomFraction: Double,
        horizontalOffset: Double,
        verticalOffset: Double,
        viewWidth: Double,
        viewHeight: Double,
    ) : this(
        SoundFalloff.gain(zoomFraction, horizontalOffset, verticalOffset, viewWidth, viewHeight),
        min(max(if (viewWidth > 0) horizontalOffset / (viewWidth / 2) else 0.0, -1.0), 1.0) * widestPan,
        SoundFalloff.gain(zoomFraction, horizontalOffset, verticalOffset, viewWidth, viewHeight) == 1.0,
    )

    companion object {
        val centered = SoundPlacement(1.0, 0.0, true)

        /** Kept gentle so it never sounds like one ear only. */
        private val widestPan = 0.6
    }
}
