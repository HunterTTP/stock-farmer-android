package com.hunter.stockfarmer.core.feedback

import kotlin.math.min
import kotlin.math.pow

/** Measured loudness lets the soundtrack play at one loudness whichever track is on. */
object MusicLoudness {
    /** The loudness every track is played at, in LUFS. No track plays above full volume, so a track quieter than this sits slightly under it. */
    val target = -30.0
    internal val measured: Map<String, Double> =
        mapOf(
            "Far Far Away" to -19.4,
            "Forgotten Biomes" to -21.5,
            "What Clouds Are Made Of" to -19.1,
            "Chickens In The Meadow" to -21.7,
            "Drifting Memories" to -27.1,
            "Ducks In A Pond" to -20.7,
            "Evening Harmony" to -20.9,
            "Floating Dream" to -22.0,
            "Gentle Breeze" to -21.5,
            "Pineapple Under The Sea" to -21.0,
            "Sheep" to -22.3,
            "Strange Worlds" to -22.1,
            "Wanderer's Tale" to -21.8,
            "Whispering Woods" to -18.7,
            "Wildflowers By The River" to -23.2,
            "Sunlight Through Leaves" to -22.0,
            "Polar Lights" to -23.3,
            "Wind Over The Trees" to -18.6,
            "Gymnopédie No.1" to -31.1,
            "Minuet" to -32.5,
        )

    fun gain(forTrack: String): Double {
        val loudness = measured[forTrack] ?: return 1.0
        return min(10.0.pow((target - loudness) / 20), 1.0)
    }
}
