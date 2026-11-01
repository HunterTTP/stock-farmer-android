package com.hunter.stockfarmer.haptics

import android.view.HapticFeedbackConstants
import com.hunter.stockfarmer.core.feedback.HapticCue
import com.hunter.stockfarmer.game.HapticRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HapticsTest {
    private var now = 0L
    private val feedback = mutableListOf<Int>()
    private val haptics = Haptics({ feedback += it }, { now })

    @Test
    fun `reel pulses accelerate from 140 to 40 milliseconds independently of taps and alerts`() {
        haptics.play(HapticCue.Light)
        haptics.play(HapticRequest.Reeling(0.0))
        haptics.play(HapticRequest.Cue(HapticCue.Success))
        assertEquals(3, feedback.size)

        now = 139
        haptics.play(HapticRequest.Reeling(0.0))
        assertEquals(3, feedback.size)
        now = 140
        haptics.play(HapticRequest.Reeling(0.0))
        assertEquals(4, feedback.size)

        now = 179
        haptics.play(HapticRequest.Reeling(1.0))
        assertEquals(4, feedback.size)
        now = 180
        haptics.play(HapticRequest.Reeling(1.0))
        assertEquals(5, feedback.size)
        assertEquals(HapticFeedbackConstants.LONG_PRESS, feedback.last())
    }

    @Test
    fun `reeling clamps progress and interpolates pulse intervals`() {
        haptics.play(HapticRequest.Reeling(-1.0))
        now = 139
        haptics.play(HapticRequest.Reeling(-1.0))
        assertEquals(1, feedback.size)
        now = 140
        haptics.play(HapticRequest.Reeling(-1.0))
        now = 229
        haptics.play(HapticRequest.Reeling(0.5))
        assertEquals(2, feedback.size)
        now = 230
        haptics.play(HapticRequest.Reeling(0.5))
        now = 269
        haptics.play(HapticRequest.Reeling(2.0))
        assertEquals(3, feedback.size)
        now = 270
        haptics.play(HapticRequest.Reeling(2.0))
        assertEquals(4, feedback.size)
    }

    @Test
    fun `tap and alert throttles remain separate`() {
        haptics.play(HapticCue.Light)
        haptics.play(HapticCue.Medium)
        haptics.play(HapticCue.Warning)
        haptics.play(HapticCue.Success)
        assertEquals(2, feedback.size)
        now = 70
        haptics.play(HapticCue.Medium)
        haptics.play(HapticCue.Success)
        assertEquals(3, feedback.size)
        now = 150
        haptics.play(HapticCue.Success)
        assertEquals(4, feedback.size)
    }
}
