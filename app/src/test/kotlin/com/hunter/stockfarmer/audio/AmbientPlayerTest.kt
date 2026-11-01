package com.hunter.stockfarmer.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AmbientPlayerTest {
    private class Output : MusicOutput {
        var ready: () -> Unit = {}
        var playing = false
        var loads = 0
        var volume = 0.0
        var fade = 0.0

        override fun load(
            path: String,
            ready: () -> Unit,
            completed: () -> Unit,
            failed: () -> Unit,
        ) {
            assertEquals("audio/Effects/Ambience/steady-rain-loop.wav", path)
            this.ready = ready
            loads++
        }

        override fun start() {
            playing = true
        }

        override fun pause() {
            playing = false
        }

        override fun stop() {
            playing = false
        }

        override fun close() {
            playing = false
        }

        override fun setVolume(
            level: Double,
            fadeSeconds: Double,
        ) {
            volume = level
            fade = fadeSeconds
        }
    }

    @Test fun `rain fades for a second at half volume times amount and channel level`() {
        val output = Output()
        val player = AmbientPlayer(output)
        player.update(0.6, false, 0.8)
        assertEquals(0, output.loads)
        player.setActive(true)
        output.ready()
        assertTrue(output.playing)
        assertEquals(0.24, output.volume)
        assertEquals(1.0, output.fade)
        player.update(0.0, false, 0.8)
        assertFalse(output.playing)
        player.update(0.6, false, 0.8)
        assertTrue(output.playing)
        assertEquals(1, output.loads)
    }

    @Test fun `snow mute pause and close suppress rain including late preparation`() {
        val output = Output()
        val player = AmbientPlayer(output)
        player.setActive(true)
        player.update(1.0, true, 1.0)
        assertEquals(0, output.loads)
        player.update(1.0, false, 1.0)
        player.setActive(false)
        output.ready()
        assertFalse(output.playing)
        player.setActive(true)
        assertTrue(output.playing)
        player.update(1.0, false, 0.0)
        assertFalse(output.playing)
        player.close()
        output.ready()
        player.update(1.0, false, 1.0)
        assertFalse(output.playing)
    }
}
