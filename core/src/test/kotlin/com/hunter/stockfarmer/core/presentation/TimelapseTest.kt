package com.hunter.stockfarmer.core.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelapseTest {
    @Test fun `scrubbing resumes only previously playing timelines and playback stops at the end`() {
        var playback = TimelapsePlayback(3)
        assertTrue(playback.isPlaying)
        playback = playback.advance()
        assertEquals(1, playback.index)
        playback = playback.beginScrubbing().seek(0).advance()
        assertTrue(playback.index == 0 && !playback.isPlaying)
        playback = playback.endScrubbing()
        assertTrue(playback.isPlaying)
        playback = playback.advance().advance()
        assertTrue(playback.index == 2 && !playback.isPlaying)
        playback = playback.beginScrubbing().seek(1).endScrubbing()
        assertFalse(playback.isPlaying)
        playback = playback.togglePlaying().advance().togglePlaying()
        assertTrue(playback.index == 0 && playback.isPlaying)
    }

    @Test fun `empty and single image timelines cannot play or seek out of bounds`() {
        for (count in listOf(0, 1)) {
            val playback = TimelapsePlayback(count).togglePlaying().seek(100).advance()
            assertTrue(!playback.isPlaying && playback.index == 0)
        }
    }
}
