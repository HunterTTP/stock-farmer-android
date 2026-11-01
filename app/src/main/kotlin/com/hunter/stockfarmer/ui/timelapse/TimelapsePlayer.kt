package com.hunter.stockfarmer.ui.timelapse

import com.hunter.stockfarmer.core.presentation.TimelapsePlayback

/** Timing: one screenshot a second, with no skipped frames after a slow decode or background pause. */
class TimelapsePlayer(
    count: Int,
) {
    var playback = TimelapsePlayback(count)
        private set
    private var elapsed = 0.0
    private var automaticTransition = false

    fun advance(
        seconds: Double,
        imageReady: Boolean,
    ) {
        if (!playback.isPlaying || playback.isScrubbing || !imageReady) {
            elapsed = 0.0
            return
        }
        if (!seconds.isFinite() || seconds < 0.0) return
        elapsed += seconds
        if (elapsed >= 1.0) {
            playback = playback.advance()
            automaticTransition = true
            elapsed = 0.0
        }
    }

    fun togglePlaying() {
        playback = playback.togglePlaying()
        resetTiming()
    }

    fun pause() {
        playback = playback.pause()
        resetTiming()
    }

    fun seek(index: Int) {
        playback = playback.seek(index)
        resetTiming()
    }

    fun beginScrubbing() {
        playback = playback.beginScrubbing()
        resetTiming()
    }

    fun endScrubbing() {
        playback = playback.endScrubbing()
        resetTiming()
    }

    fun transitionMillis(
        crossfades: Boolean,
        reduceMotion: Boolean,
    ): Int = if (automaticTransition && crossfades && !reduceMotion && !playback.isScrubbing) 200 else 0

    private fun resetTiming() {
        elapsed = 0.0
        automaticTransition = false
    }
}
