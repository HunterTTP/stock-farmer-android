package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.feedback.MusicLoudness
import com.hunter.stockfarmer.core.feedback.SoundMix
import com.hunter.stockfarmer.core.rules.Season
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MusicPlayerTest {
    private class Output : MusicOutput {
        var path: String? = null
        var ready: () -> Unit = {}
        var completed: () -> Unit = {}
        var failed: () -> Unit = {}
        var playing = false
        var volume = 0.0
        var fade = 0.0
        var loads = 0
        var closed = false

        override fun load(
            path: String,
            ready: () -> Unit,
            completed: () -> Unit,
            failed: () -> Unit,
        ) {
            this.path = path
            this.ready = ready
            this.completed = completed
            this.failed = failed
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
            path = null
        }

        override fun close() {
            closed = true
        }

        override fun setVolume(
            level: Double,
            fadeSeconds: Double,
        ) {
            volume = level
            fade = fadeSeconds
        }
    }

    private class Harness(
        exists: (String) -> Boolean = { true },
    ) {
        val output = Output()
        val player = MusicPlayer(output, MusicPlayer.playlists(exists))

        fun start() {
            player.setActive(true)
            output.ready()
        }
    }

    @Test fun `music waits for activation and preparation and resumes without reloading`() {
        val h = Harness()
        assertEquals(0, h.output.loads)
        h.player.setActive(true)
        assertFalse(h.output.playing)
        h.output.ready()
        assertTrue(h.output.playing)
        h.player.setActive(false)
        assertFalse(h.output.playing)
        h.player.setActive(true)
        assertTrue(h.output.playing)
        assertEquals(1, h.output.loads)
    }

    @Test fun `the season's first track opens ahead of time and plays once the music can`() {
        val h = Harness()
        h.player.prepare(Season.fall)
        assertEquals(1, h.output.loads)
        assertEquals("Drifting Memories", h.player.trackName)
        assertFalse(h.player.isPrepared)
        h.output.ready()
        assertTrue(h.player.isPrepared)
        assertFalse(h.output.playing)
        h.player.setActive(true)
        assertTrue(h.output.playing)
        assertEquals(1, h.output.loads)
    }

    @Test fun `a track that fails to open or a season with none still lets the launch go on`() {
        val h = Harness()
        h.player.prepare(Season.spring)
        h.output.failed()
        assertTrue(h.player.isPrepared)
        assertNull(h.player.trackName)
        val empty = Harness { false }
        empty.player.prepare(Season.winter)
        assertTrue(empty.player.isPrepared)
        assertEquals(0, empty.output.loads)
        val switched = Harness()
        switched.player.prepare(Season.fall)
        switched.player.setSeason(Season.winter)
        assertTrue(switched.player.isPrepared)
    }

    @Test fun `music follows alphabetical playlist and keeps each season's position`() {
        val h = Harness()
        h.start()
        assertEquals("Chickens In The Meadow", h.player.trackName)
        h.output.completed()
        assertEquals("Ducks In A Pond", h.player.trackName)
        h.player.setSeason(Season.fall)
        assertEquals("Drifting Memories", h.player.trackName)
        h.player.skip()
        assertEquals("Evening Harmony", h.player.trackName)
        h.player.setSeason(Season.spring)
        assertEquals("Ducks In A Pond", h.player.trackName)
        h.player.setSeason(Season.fall)
        assertEquals("Evening Harmony", h.player.trackName)
    }

    @Test fun `playlist wraps and unavailable season falls back to spring`() {
        val h = Harness { it.endsWith("Far Far Away.m4a") }
        h.start()
        h.player.setSeason(Season.winter)
        h.player.skip()
        assertEquals("Far Far Away", h.player.trackName)
        assertEquals(2, h.output.loads)
        val empty = Harness { false }
        empty.start()
        assertNull(empty.player.trackName)
        assertEquals(0, empty.output.loads)
    }

    @Test fun `gain and measured loudness multiply and zero pauses at the same track`() {
        val h = Harness()
        h.start()
        h.player.setGain(0.5)
        assertEquals(0.5 * MusicLoudness.gain("Chickens In The Meadow"), h.output.volume)
        h.player.setGain(0.0)
        assertFalse(h.output.playing)
        h.player.skip()
        assertEquals(1, h.output.loads)
        h.player.setGain(1.0)
        assertTrue(h.output.playing)
        assertEquals(1, h.output.loads)
    }

    @Test fun `a fanfare ducks the music 6 dB until the latest one ends`() {
        val h = Harness()
        h.start()
        val full = h.output.volume
        assertTrue(h.player.duck(2.0, now = 10.0))
        assertEquals(full * SoundMix.musicDuckGain, h.output.volume, 1e-12)
        assertEquals(SoundMix.duckFade, h.output.fade)
        assertFalse(h.player.duck(1.0, now = 10.5))
        assertTrue(h.player.duck(3.0, now = 11.0))
        assertEquals(full * SoundMix.musicDuckGain, h.output.volume, 1e-12)
        assertTrue(h.output.playing)
        h.player.endDuck()
        assertEquals(full, h.output.volume)
        assertEquals(SoundMix.duckFade, h.output.fade)
    }

    @Test fun `night hush fades only for the current chime and lets playback continue`() {
        val h = Harness()
        h.start()
        val full = h.output.volume
        h.player.updateNight(0.0, false)
        assertEquals(full, h.output.volume)
        h.player.updateNight(0.0, true)
        assertEquals(0.0, h.output.volume)
        assertEquals(2.0, h.output.fade)
        assertTrue(h.output.playing)
        h.player.skip()
        h.output.ready()
        assertEquals(0.0, h.output.volume)
        h.player.updateNight(10.0, true)
        assertEquals(MusicLoudness.gain("Ducks In A Pond"), h.output.volume)
        assertEquals(2.0, h.output.fade)
    }

    @Test fun `other audio and lifecycle gate late preparation and skip`() {
        val h = Harness()
        h.player.setOtherAudioPlaying(true)
        h.player.setActive(true)
        assertEquals(0, h.output.loads)
        h.player.setOtherAudioPlaying(false)
        h.player.setActive(false)
        h.output.ready()
        assertFalse(h.output.playing)
        h.player.skip()
        assertEquals(1, h.output.loads)
        h.player.setActive(true)
        assertTrue(h.output.playing)
        h.player.setOtherAudioPlaying(true)
        assertFalse(h.output.playing)
    }

    @Test fun `completion after pause advances without playing in the background`() {
        val h = Harness()
        h.start()
        h.player.setActive(false)
        h.output.completed()
        assertEquals(1, h.output.loads)
        h.player.setActive(true)
        assertEquals("Ducks In A Pond", h.player.trackName)
    }

    @Test fun `stale callbacks cannot start advance or discard a replacement track`() {
        val h = Harness()
        h.player.setActive(true)
        val ready = h.output.ready
        val completed = h.output.completed
        val failed = h.output.failed
        h.player.setSeason(Season.winter)
        val current = h.player.trackName
        ready()
        completed()
        failed()
        assertFalse(h.output.playing)
        assertEquals(current, h.player.trackName)
        assertEquals(2, h.output.loads)
        h.player.close()
        h.output.ready()
        h.output.completed()
        h.player.setActive(true)
        assertFalse(h.output.playing)
        assertTrue(h.output.closed)
    }

    @Test fun `failure clears the track and permits a later retry`() {
        val h = Harness()
        h.start()
        h.output.failed()
        assertNull(h.player.trackName)
        assertFalse(h.output.playing)
        h.player.setActive(true)
        assertEquals(2, h.output.loads)
    }

    @Test fun `fade interpolates for two seconds and reverses from its current level`() {
        val fade = VolumeFade()
        fade.set(1.0, 0.0, 0.0)
        fade.set(0.0, 2.0, 1.0)
        assertEquals(0.5, fade.value(2.0))
        fade.set(1.0, 2.0, 2.0)
        assertEquals(0.5, fade.value(2.0))
        assertEquals(0.75, fade.value(3.0))
        assertEquals(1.0, fade.value(4.0))
        assertTrue(fade.isFinished(4.0))
    }
}
