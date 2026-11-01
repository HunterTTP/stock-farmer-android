package com.hunter.stockfarmer.ui.timelapse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelapsePlayerTest {
    @Test
    fun `playback advances one frame per second and stops on the final frame`() {
        val player = TimelapsePlayer(3)
        player.advance(0.5, imageReady = true)
        assertEquals(0, player.playback.index)
        player.advance(0.5, imageReady = true)
        assertEquals(1, player.playback.index)
        player.advance(1.0, imageReady = true)
        assertEquals(2, player.playback.index)
        assertFalse(player.playback.isPlaying)
        player.togglePlaying()
        assertEquals(0, player.playback.index)
        assertTrue(player.playback.isPlaying)
    }

    @Test
    fun `unloaded frames and long delays do not skip screenshots`() {
        val player = TimelapsePlayer(10)
        player.advance(0.9, imageReady = true)
        player.advance(50.0, imageReady = false)
        player.advance(0.2, imageReady = true)
        assertEquals(0, player.playback.index)
        player.advance(30.0, imageReady = true)
        assertEquals(1, player.playback.index)
        player.advance(0.5, imageReady = true)
        assertEquals(1, player.playback.index)
    }

    @Test
    fun `scrubbing pauses advances and resumes only a previously playing timeline`() {
        val player = TimelapsePlayer(4)
        player.beginScrubbing()
        player.seek(2)
        player.advance(10.0, imageReady = true)
        assertEquals(2, player.playback.index)
        player.endScrubbing()
        assertTrue(player.playback.isPlaying)
        player.pause()
        player.beginScrubbing()
        player.seek(1)
        player.endScrubbing()
        assertFalse(player.playback.isPlaying)
        player.togglePlaying()
        player.beginScrubbing()
        player.seek(3)
        player.endScrubbing()
        assertFalse(player.playback.isPlaying)
    }

    @Test
    fun `pause clears elapsed time and the scrubbing resume intent on backgrounding`() {
        val player = TimelapsePlayer(4)
        player.advance(0.9, imageReady = true)
        player.beginScrubbing()
        player.pause()
        player.endScrubbing()
        assertFalse(player.playback.isPlaying)
        player.togglePlaying()
        player.advance(0.2, imageReady = true)
        assertEquals(0, player.playback.index)
    }

    @Test
    fun `only automatic frame changes crossfade with motion and crossfade settings enabled`() {
        val player = TimelapsePlayer(3)
        assertEquals(0, player.transitionMillis(crossfades = true, reduceMotion = false))
        player.advance(1.0, imageReady = true)
        assertEquals(200, player.transitionMillis(crossfades = true, reduceMotion = false))
        assertEquals(0, player.transitionMillis(crossfades = false, reduceMotion = false))
        assertEquals(0, player.transitionMillis(crossfades = true, reduceMotion = true))
        player.beginScrubbing()
        player.seek(0)
        assertEquals(0, player.transitionMillis(crossfades = true, reduceMotion = false))
    }

    @Test
    fun `empty and single frame archives stay paused and clamp seeks`() {
        for (count in listOf(0, 1)) {
            val player = TimelapsePlayer(count)
            player.togglePlaying()
            player.seek(100)
            player.advance(10.0, imageReady = true)
            assertFalse(player.playback.isPlaying)
            assertEquals(0, player.playback.index)
        }
    }
}
