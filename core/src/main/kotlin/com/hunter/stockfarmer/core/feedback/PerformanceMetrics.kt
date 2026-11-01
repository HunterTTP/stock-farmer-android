package com.hunter.stockfarmer.core.feedback

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Fixed storage for arbitrarily long captures. Percentiles round up to 0.25 ms; overflow reports the observed maximum. */
@ConsistentCopyVisibility
data class PerformanceDurations private constructor(
    private val buckets: List<Long>,
    val count: Long,
    val totalMilliseconds: Double,
    val maximumMilliseconds: Double,
) {
    constructor() : this(List(1_002) { 0L }, 0, 0.0, 0.0)

    internal val bucketCount: Int get() = buckets.size
    val averageMilliseconds: Double get() = if (count == 0L) 0.0 else totalMilliseconds / count.toDouble()

    fun add(seconds: Double): PerformanceDurations {
        val milliseconds = seconds * 1_000
        if (!milliseconds.isFinite() || milliseconds < 0) return this
        val bucket = if (milliseconds > 250) 1_001 else ceil(milliseconds * 4).toInt()
        return copy(
            buckets = buckets.mapIndexed { index, count -> if (index == bucket) count + 1 else count },
            count = count + 1,
            totalMilliseconds = totalMilliseconds + milliseconds,
            maximumMilliseconds = max(maximumMilliseconds, milliseconds),
        )
    }

    fun percentile(fraction: Double): Double {
        if (count == 0L || !fraction.isFinite()) return 0.0
        val rank = max(1L, ceil(min(1.0, max(0.0, fraction)) * count.toDouble()).toLong())
        var seen = 0L
        for ((index, count) in buckets.withIndex()) {
            seen += count
            if (seen >= rank) return if (index == 1_001) maximumMilliseconds else index.toDouble() / 4
        }
        return maximumMilliseconds
    }
}

/** Frame cadence includes foreground stalls but excludes explicitly paused gaps. */
@ConsistentCopyVisibility
data class PerformanceFrames private constructor(
    val targetFPS: Double,
    val durations: PerformanceDurations,
    val slowFrames: Long,
    val hitches: Long,
    private val previous: Double?,
) {
    constructor(targetFPS: Double) : this(max(1.0, targetFPS), PerformanceDurations(), 0, 0, null)

    val fps: Double get() =
        if (durations.totalMilliseconds >
            0
        ) {
            durations.count.toDouble() * 1_000 / durations.totalMilliseconds
        } else {
            0.0
        }

    fun observe(at: Double): Pair<PerformanceFrames, Double?> {
        if (!at.isFinite()) return this to null
        val next = copy(previous = at)
        if (previous == null || at <= previous) return next to null
        val seconds = at - previous
        return next.copy(
            durations = durations.add(seconds),
            slowFrames = slowFrames + if (seconds > 1.5 / targetFPS) 1 else 0,
            hitches = hitches + if (seconds > 0.05) 1 else 0,
        ) to seconds
    }

    fun resetClock(): PerformanceFrames = copy(previous = null)
}
