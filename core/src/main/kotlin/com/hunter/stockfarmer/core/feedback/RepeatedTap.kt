package com.hunter.stockfarmer.core.feedback

import java.time.Duration
import java.time.Instant

/** Counts taps that each come within [window] of the one before, and says when [required] of them have happened in a row. */
class RepeatedTap(
    val required: Int,
    val window: Duration,
) {
    private var count = 0
    private var lastTap: Instant? = null

    /** Records a tap and returns true when it completes the run, which starts the count over. */
    fun register(at: Instant): Boolean {
        val previous = lastTap
        if (previous != null && Duration.between(previous, at) > window) count = 0
        lastTap = at
        count++
        if (count < required) return false
        count = 0
        return true
    }
}
