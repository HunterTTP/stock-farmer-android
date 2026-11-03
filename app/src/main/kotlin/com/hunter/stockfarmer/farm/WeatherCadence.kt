package com.hunter.stockfarmer.farm

/** How often the game state is sampled; the rain eases over minutes, so tenths of a second are invisible. */
internal const val SAMPLE_SECONDS = 0.25
internal const val SAMPLE_MILLIS = 250L

/** When the weather loop samples the game, and when it can sleep to the next sample instead of waking every frame. */
internal class WeatherCadence {
    private var sinceSample = SAMPLE_SECONDS

    /** Nothing is falling, none is being cleared away and none can start before the next sample. */
    fun canSleep(
        showing: Boolean,
        amount: Double,
    ): Boolean = !showing && amount <= 0.0 && sinceSample < SAMPLE_SECONDS

    /** Called after sleeping a sample interval, so the next frame samples. */
    fun slept() {
        sinceSample = SAMPLE_SECONDS
    }

    /** Adds a frame's time; true when it is time to sample. */
    fun advance(elapsed: Double): Boolean {
        sinceSample += elapsed
        if (sinceSample < SAMPLE_SECONDS) return false
        sinceSample = 0.0
        return true
    }
}
