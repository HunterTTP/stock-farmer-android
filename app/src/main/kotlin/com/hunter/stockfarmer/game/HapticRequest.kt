package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.feedback.HapticCue

/** Direct interaction feedback, including reel pulses with a cadence independent of taps and alerts. */
sealed interface HapticRequest {
    data class Cue(
        val cue: HapticCue,
    ) : HapticRequest

    data class Reeling(
        val progress: Double,
    ) : HapticRequest
}
