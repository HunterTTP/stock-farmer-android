package com.hunter.stockfarmer.core.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PerformanceMetricsTest {
    @Test fun `durations summarize all samples with bounded storage`() {
        var samples = PerformanceDurations()
        repeat(10_000) { samples = samples.add(0.010) }
        repeat(1_000) { samples = samples.add(0.040) }
        samples = samples.add(2.0)
        assertEquals(11_001L, samples.count)
        assertEquals(142_000.0, samples.totalMilliseconds, 0.001)
        assertEquals(10.0, samples.percentile(0.50))
        assertEquals(40.0, samples.percentile(0.95))
        assertEquals(2_000.0, samples.maximumMilliseconds)
        assertEquals(2_000.0, samples.percentile(1.0))
        assertEquals(1_002, samples.bucketCount)
    }

    @Test fun `invalid durations do not pollute report`() {
        var samples = PerformanceDurations()
        for (value in listOf(-1.0, Double.POSITIVE_INFINITY, Double.NaN)) samples = samples.add(value)
        assertEquals(0L, samples.count)
        assertEquals(0.0, samples.averageMilliseconds)
        assertEquals(0.0, samples.percentile(0.95))
        samples = samples.add(0.0)
        assertEquals(1L, samples.count)
        assertEquals(0.0, samples.percentile(0.95))
    }

    @Test fun `last regular bucket does not absorb long stalls`() {
        var samples = PerformanceDurations()
        repeat(99) { samples = samples.add(0.2499) }
        samples = samples.add(2.0)
        assertEquals(250.0, samples.percentile(0.95))
        assertEquals(2_000.0, samples.percentile(1.0))
    }

    @Test fun `frame rate uses elapsed time and keeps foreground stalls`() {
        var frames = PerformanceFrames(60.0)
        for (time in listOf(100.0, 100.01, 100.05, 102.05)) frames = frames.observe(time).first
        assertEquals(3L, frames.durations.count)
        assertEquals(3 / 2.05, frames.fps, 0.0001)
        assertEquals(2L, frames.slowFrames)
        assertEquals(1L, frames.hitches)
    }

    @Test fun `pauses do not count as slow frames and resume needs two frames`() {
        var frames =
            PerformanceFrames(60.0)
                .observe(10.0)
                .first
                .observe(10.016)
                .first
                .resetClock()
        frames = frames.observe(500.0).first
        assertEquals(1L, frames.durations.count)
        frames = frames.observe(500.016).first
        assertEquals(2L, frames.durations.count)
        assertEquals(0L, frames.hitches)
        assertEquals(62.5, frames.fps, 0.001)
    }

    @Test fun `slow threshold follows requested frame rate`() {
        val frames =
            PerformanceFrames(120.0)
                .observe(0.0)
                .first
                .observe(0.016)
                .first
        assertEquals(1L, frames.slowFrames)
        assertEquals(0L, frames.hitches)
    }
}
