package com.hunter.stockfarmer.core.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MusicLoudnessTest {
    @Test fun `a louder track plays quieter and no track above full volume`() {
        assertTrue(MusicLoudness.gain("Whispering Woods") < MusicLoudness.gain("Polar Lights"))
        for (track in MusicLoudness.measured.keys) assertTrue(MusicLoudness.gain(track) in 0.0..1.0)
    }

    @Test fun `an unmeasured track plays at full volume`() {
        assertEquals(1.0, MusicLoudness.gain("Unknown"))
    }
}
