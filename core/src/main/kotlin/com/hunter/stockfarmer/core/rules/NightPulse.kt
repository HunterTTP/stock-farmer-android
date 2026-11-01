package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GameState
import java.time.Instant
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A wave of light that rolls out slowly from wherever the player is looking every midnight, in a color the player
 * picks. Like the night itself it is only a picture and never reaches the rules.
 */
object NightPulse {
    /** The wave begins at midnight, when it is darkest, and rolls for this many minutes of the farm's clock. */
    const val LENGTH_MINUTES = 20.0

    /** The chime rings for this long from midnight, as the wave sets off; the music fades out as it starts. */
    private const val CHIME_MINUTES = 10.0

    /** The minute at which the summary of the day starts to fade in, while the chime is still ringing. */
    const val SUMMARY_MINUTE = 9.0

    /** How much of the roll the wave takes to fade in, so it never appears all at once. */
    const val FADE_IN_PROGRESS = 0.12

    /** How far from the front of the wave, in tiles, it still glows on either side. */
    const val BAND_WIDTH = 5.0

    data class Wave(
        val day: Int,
        val progress: Double,
    )

    /** Which night, counted in days of the farm's clock since the epoch; the midnight the wave rolls at belongs to the new day. */
    fun day(
        date: Instant,
        offset: Double,
    ): Int = floor((date.epochSecond + date.nano / 1e9 + offset) / DayNightCycle.secondsPerDay).toInt()

    /** The wave rolling at a moment, if there is one. */
    fun wave(
        date: Instant,
        offset: Double,
    ): Wave? {
        val progress = progress(DayNightCycle.minuteOfDay(date, offset)) ?: return null
        return Wave(day(date, offset), progress)
    }

    /** Whether it is the time of the wave, when the chime starts as it turns true. */
    fun isChimeDue(minuteOfDay: Double): Boolean = progress(minuteOfDay) != null

    /** Whether the music should be quiet, to leave the midnight chime to ring alone. */
    fun isMusicHushed(minuteOfDay: Double): Boolean = minuteOfDay < CHIME_MINUTES

    /** How far a wave has rolled since it started, from 0 up to but not including 1, or null when it isn't rolling. */
    internal fun progress(minuteOfDay: Double): Double? =
        if (minuteOfDay >= 0 &&
            minuteOfDay < LENGTH_MINUTES
        ) {
            minuteOfDay / LENGTH_MINUTES
        } else {
            null
        }

    /** How far, in tiles, the front of the wave has to go from where it starts to leave the far corner of the map behind. */
    fun reach(
        row: Double,
        column: Double,
    ): Double {
        val size = GameState.GRID_SIZE.toDouble()
        return hypot(max(row, size - row), max(column, size - column)) + BAND_WIDTH
    }

    /** How far from where it started, in tiles, the front of the wave is. */
    fun radius(
        progress: Double,
        reach: Double,
    ): Double = progress * reach

    /**
     * How brightly a tile glows, from 0 to 1, given how far it is from where the wave started: brightest at the front of
     * the wave, fading to nothing at the edges of its band, and the whole wave fading in as it starts and away as it
     * reaches the end of its roll.
     */
    fun intensity(
        distance: Double,
        progress: Double,
        reach: Double,
    ): Double {
        val fromFront = abs(distance - radius(progress, reach)) / BAND_WIDTH
        if (fromFront >= 1) return 0.0
        val band = 1 - fromFront
        val fadeIn = min(1.0, progress / FADE_IN_PROGRESS)
        return band * band * (3 - 2 * band) * fadeIn * fadeIn * (3 - 2 * fadeIn) * (1 - progress * progress)
    }
}
