package com.hunter.stockfarmer.core.feedback

import kotlin.math.pow

/**
 * How the sliders and the trims make the mix. The clips are mastered to one reference loudness, so the per-cue trims in
 * [SoundCue] and the ones below are the mix, and every slider at 100% is the balance as designed. A slider is a number of
 * decibels, not an amplitude.
 */
object SoundMix {
    /** How many decibels below full a slider just above zero sits. */
    val sliderRangeDB = 40.0

    /** How far the music is turned down under a fanfare, in decibels, and how long it takes to fade into and out of that, in seconds. */
    val musicDuckDB = -6.0
    val duckFade = 0.25

    /** How far below its recording the rain plays, in decibels. */
    val rainTrimDB = -10.0

    /**
     * How loud each bus plays with its slider at 100%, in decibels: what its slider used to give at 80% (music), 75%
     * (effects) and 70% (ambience), which were loud enough. The master is full at 100%.
     */
    val musicFullDB = -8.0
    val effectsFullDB = -10.0
    val ambienceFullDB = -12.0

    val musicDuckGain: Double get() = fromDecibels(musicDuckDB)
    val rainTrimGain: Double get() = fromDecibels(rainTrimDB)

    /** The share of full volume a slider from 0 to 1 plays at: 0 is mute, 1 is [fullDB], and between them it falls evenly in decibels. */
    fun gain(
        forSlider: Double,
        fullDB: Double = 0.0,
    ): Double {
        val value = forSlider.coerceIn(0.0, 1.0)
        return if (value == 0.0) 0.0 else fromDecibels(fullDB + (value - 1) * sliderRangeDB)
    }

    private fun fromDecibels(decibels: Double): Double = 10.0.pow(decibels / 20)
}
