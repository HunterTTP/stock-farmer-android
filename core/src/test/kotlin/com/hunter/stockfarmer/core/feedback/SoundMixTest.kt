package com.hunter.stockfarmer.core.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.pow

class SoundMixTest {
    @Test
    fun `a slider is linear in decibels`() {
        assertEquals(1.0, SoundMix.gain(forSlider = 1.0))
        assertEquals(10.0.pow(-10.0 / 20), SoundMix.gain(forSlider = 0.75), 1e-12)
        assertEquals(10.0.pow(-20.0 / 20), SoundMix.gain(forSlider = 0.5), 1e-12)
    }

    @Test
    fun `a slider at zero is muted and out of range is clamped`() {
        assertEquals(0.0, SoundMix.gain(forSlider = 0.0))
        assertEquals(0.0, SoundMix.gain(forSlider = -1.0))
        assertEquals(1.0, SoundMix.gain(forSlider = 2.0))
    }

    @Test
    fun `the duck and the rain are in decibels`() {
        assertEquals(10.0.pow(-6.0 / 20), SoundMix.musicDuckGain, 1e-12)
        assertEquals(10.0.pow(-10.0 / 20), SoundMix.rainTrimGain, 1e-12)
        assertEquals(0.25, SoundMix.duckFade)
    }
}
