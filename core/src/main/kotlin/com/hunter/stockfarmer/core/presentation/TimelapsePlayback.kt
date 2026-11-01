package com.hunter.stockfarmer.core.presentation

import kotlin.math.max
import kotlin.math.min

@ConsistentCopyVisibility
data class TimelapsePlayback private constructor(
    val count: Int,
    val index: Int,
    val isPlaying: Boolean,
    val isScrubbing: Boolean,
    private val resumesAfterScrubbing: Boolean,
) {
    constructor(count: Int) : this(max(0, count), 0, count > 1, false, false)

    fun advance(): TimelapsePlayback {
        if (!isPlaying || isScrubbing) return this
        val next = seek(index + 1)
        return if (next.index == count - 1) next.copy(isPlaying = false) else next
    }

    fun seek(to: Int): TimelapsePlayback = copy(index = min(max(0, to), max(0, count - 1)))

    fun togglePlaying(): TimelapsePlayback {
        if (count <= 1) return this
        return copy(index = if (!isPlaying && index == count - 1) 0 else index, isPlaying = !isPlaying)
    }

    fun pause(): TimelapsePlayback = copy(isPlaying = false, resumesAfterScrubbing = false)

    fun beginScrubbing(): TimelapsePlayback =
        if (isScrubbing) this else copy(resumesAfterScrubbing = isPlaying, isPlaying = false, isScrubbing = true)

    fun endScrubbing(): TimelapsePlayback =
        copy(
            isScrubbing = false,
            isPlaying = resumesAfterScrubbing && index < count - 1,
            resumesAfterScrubbing = false,
        )
}
