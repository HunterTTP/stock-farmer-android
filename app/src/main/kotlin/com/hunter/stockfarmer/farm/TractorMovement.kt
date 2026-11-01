package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.model.TractorRules
import com.hunter.stockfarmer.core.model.TractorRun
import com.hunter.stockfarmer.core.presentation.TractorBobbing
import com.hunter.stockfarmer.core.presentation.TractorMotion
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant

/**
 * How long the Tractor has been driving, which turns its wheels and bobs its body. The clock runs only while the run changes, so a
 * Tractor held still stops turning, and it starts from how far along its step the run already is.
 */
internal class TractorMovement(
    private val bobbing: TractorBobbing = TractorBobbing(),
) {
    private var lastRun: TractorRun? = null
    private var lastAt: Instant? = null
    private var time = 0.0

    /** Moves the clock on to [now] for [run], or stops it when there is no run. */
    fun advance(
        run: TractorRun?,
        now: Instant,
    ) {
        val elapsed = lastAt?.let { now.secondsSince(it).coerceIn(0.0, MAX_STEP) } ?: 0.0
        lastAt = now
        val previous = lastRun
        lastRun = run
        when {
            run == null -> time = 0.0
            previous == null -> time = run.progress * TractorMotion.duration(run)
            run != previous -> time += elapsed
        }
    }

    /** The wheel frame of the six on the sheet: one turn for each tile driven. */
    fun wheelFrame(): Int =
        (time % TractorRules.SECONDS_PER_TILE / TractorRules.SECONDS_PER_TILE * FRAMES).toInt().coerceIn(
            0,
            FRAMES - 1,
        )

    /** How far the body is lifted, in tiles, while it drives a step. */
    fun lift(run: TractorRun): Double = if (run.nextTile != null) bobbing.offset(time) else 0.0

    private companion object {
        const val FRAMES = 6
        const val MAX_STEP = 0.1
    }
}
