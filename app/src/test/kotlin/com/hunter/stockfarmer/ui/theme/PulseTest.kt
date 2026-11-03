package com.hunter.stockfarmer.ui.theme

import androidx.compose.ui.graphics.ClipOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PulseTest {
    @Test
    fun swellRunsFromZeroToOneAndBack() {
        assertEquals(0.0, Pulse.swell(0.0), 1e-9)
        assertEquals(1.0, Pulse.swell(Pulse.PERIOD_SECONDS / 2), 1e-9)
        assertEquals(0.0, Pulse.swell(Pulse.PERIOD_SECONDS), 1e-9)
    }

    @Test
    fun swellAtFollowsTheFrameClockAndHoldsStillWhenMotionIsReduced() {
        assertEquals(0f, Pulse.swellAt(0L, reduceMotion = false), 1e-6f)
        val halfPeriodNanos = (Pulse.PERIOD_SECONDS / 2 * 1e9).toLong()
        assertEquals(1f, Pulse.swellAt(halfPeriodNanos, reduceMotion = false), 1e-6f)
        assertEquals(Pulse.swell(0.37).toFloat(), Pulse.swellAt(370_000_000L, reduceMotion = false), 1e-6f)
        assertEquals(0.5f, Pulse.swellAt(0L, reduceMotion = true), 1e-6f)
        assertEquals(0.5f, Pulse.swellAt(halfPeriodNanos, reduceMotion = true), 1e-6f)
    }

    @Test
    fun scaleSwellsUnlessMotionIsReduced() {
        assertEquals(1.05f, Pulse.scale(1f, reduceMotion = false), 1e-6f)
        assertEquals(1f, Pulse.scale(1f, reduceMotion = true), 1e-6f)
    }

    @Test
    fun insideGlowStaysInsideAndDoesNotScale() {
        assertEquals(ClipOp.Intersect, Pulse.clipOp(inside = true))
        assertEquals(ClipOp.Difference, Pulse.clipOp(inside = false))
        assertEquals(1f, Pulse.scale(1f, reduceMotion = false, inside = true), 1e-6f)
        assertEquals(1.05f, Pulse.scale(1f, reduceMotion = false, inside = false), 1e-6f)
    }

    @Test
    fun glowRadiusFollowsSwellAndDensity() {
        assertEquals(0f, Pulse.glowRadius(0f, 3f), 1e-6f)
        assertEquals(14f, Pulse.glowRadius(1f, 2f), 1e-6f)
    }

    @Test
    fun ringsShrinkFromWidestAndFadeWithSwell() {
        assertEquals(20f, Pulse.ringWidth(0, 10f), 1e-6f)
        assertTrue(Pulse.ringWidth(1, 10f) < Pulse.ringWidth(0, 10f))
        assertTrue(Pulse.ringWidth(Pulse.GLOW_STEPS - 1, 10f) > 0f)
        assertEquals(0f, Pulse.ringAlpha(0f), 1e-6f)
        assertTrue(Pulse.ringAlpha(1f) <= 1f)
    }
}
