package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.feedback.SoundPlacement
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoundPlaybackTest {
    private class Output : EffectOutput {
        val played = mutableListOf<Pair<EffectClip, StereoVolume>>()
        val stopped = mutableListOf<Int>()
        var available = true
        var succeeds = true

        override fun clips(cue: SoundCue): List<EffectClip> =
            if (available) listOf(EffectClip(1, 10.0), EffectClip(2, 10.0)) else emptyList()

        override fun play(
            clip: EffectClip,
            volume: StereoVolume,
        ): Int {
            if (!succeeds) return 0
            played += clip to volume
            return played.size
        }

        override fun stop(stream: Int) {
            stopped += stream
        }

        override fun close() = Unit
    }

    private class Harness {
        val output = Output()
        var now = 0.0
        val player = SoundEffectsPlayer(output, { now }, RandomSource { -1L })
    }

    @Test fun `settings default to full and persist the channel keys`() {
        val settings = SoundSettings.from(mapOf("musicVolume" to 0.4, "animalSoundsVolume" to -1.0))
        assertEquals(0.4, settings[AudioChannel.music])
        assertEquals(0.0, settings[AudioChannel.animals])
        assertEquals(1.0, settings[AudioChannel.farm])
        assertEquals(1.0, settings.withLevel(AudioChannel.farm, 2.0)[AudioChannel.farm])
        assertEquals(1.0, settings.withLevel(AudioChannel.farm, Double.NaN)[AudioChannel.farm])
    }

    @Test fun `only animals fall off but every placed cue pans`() {
        val placement = SoundPlacement(1.0, 200.0, 0.0, 400.0, 800.0)
        val animals = StereoVolume.forCue(SoundCue.cowMoo, placement, SoundSettings())!!
        assertEquals(0.4, animals.right.toDouble(), 0.000001)
        assertEquals(0.16, animals.left.toDouble(), 0.000001)
        val farm = StereoVolume.forCue(SoundCue.plant, placement, SoundSettings())!!
        assertEquals(SoundCue.plant.trimGain, farm.right.toDouble(), 0.000001)
        assertEquals(farm.right * 0.4, farm.left.toDouble(), 0.000001)
        val left = StereoVolume.forCue(SoundCue.cowMoo, placement.copy(pan = -0.6), SoundSettings())!!
        assertEquals(animals.left, left.right)
        assertEquals(animals.right, left.left)
    }

    @Test fun `audibility threshold is on distance gain before trim and channel level`() {
        assertEquals(null, StereoVolume.forCue(SoundCue.cowMoo, SoundPlacement(0.019, 0.0, false), SoundSettings()))
        assertTrue(StereoVolume.forCue(SoundCue.cowMoo, SoundPlacement(0.02, 0.0, false), SoundSettings()) != null)
        val quiet = SoundSettings().withLevel(AudioChannel.farm, 0.001)
        assertTrue(StereoVolume.forCue(SoundCue.plant, SoundPlacement.centered, quiet) != null)
        assertEquals(
            null,
            StereoVolume.forCue(
                SoundCue.plant,
                SoundPlacement.centered,
                quiet.withLevel(AudioChannel.farm, 0.0),
            ),
        )
    }

    @Test fun `suspended and unloaded effects are dropped without replay on resume`() {
        val h = Harness()
        assertFalse(h.player.play(SoundCue.plant))
        h.player.resume()
        h.output.available = false
        assertFalse(h.player.play(SoundCue.plant))
        h.output.available = true
        assertEquals(0, h.output.played.size)
        assertTrue(h.player.play(SoundCue.plant))
    }

    @Test fun `cooldown six copies and nonrepeating variants behave as designed`() {
        val h = Harness()
        h.player.resume()
        assertTrue(h.player.play(SoundCue.plant))
        h.now = 0.029
        assertFalse(h.player.play(SoundCue.plant))
        repeat(5) {
            h.now += 0.04
            assertTrue(h.player.play(SoundCue.plant))
        }
        h.now += 0.04
        assertFalse(h.player.play(SoundCue.plant))
        // A rejected request still consumes a variant.
        assertNotEquals(
            h.output.played[2]
                .first.id,
            h.output.played[3]
                .first.id,
        )
        h.now = 11.0
        assertTrue(h.player.play(SoundCue.plant))
    }

    @Test fun `sixteen voices drop newcomers instead of stealing playing streams`() {
        val h = Harness()
        h.player.resume()
        SoundCue.entries.take(16).forEach { assertTrue(h.player.play(it)) }
        assertFalse(h.player.play(SoundCue.entries[16]))
        assertTrue(h.output.stopped.isEmpty())
    }

    @Test fun `failed native playback does not reserve a voice or a cooldown`() {
        val h = Harness()
        h.player.resume()
        h.output.succeeds = false
        assertFalse(h.player.play(SoundCue.plant))
        h.output.succeeds = true
        assertTrue(h.player.play(SoundCue.plant))
    }

    @Test fun `muting cuts that channel and suspension forgets all voices`() {
        val h = Harness()
        h.player.resume()
        h.player.play(SoundCue.plant)
        h.player.play(SoundCue.cowMoo)
        h.player.settings = SoundSettings().withLevel(AudioChannel.animals, 0.0)
        assertEquals(listOf(2), h.output.stopped)
        assertFalse(h.player.play(SoundCue.cowMoo))
        h.player.suspend()
        assertTrue(h.output.stopped.contains(1))
        h.player.resume()
        assertTrue(h.player.play(SoundCue.plant))
        h.player.close()
        h.player.resume()
        assertFalse(h.player.play(SoundCue.plant))
    }

    @Test fun `stopping a previous cue never stops a voice that has been reused`() {
        val h = Harness()
        h.player.resume()
        h.player.play(SoundCue.nightPulse)
        h.now = 11.0
        h.player.play(SoundCue.plant)
        h.player.stop(SoundCue.nightPulse)
        assertFalse(h.output.stopped.contains(2))
        h.player.stop(SoundCue.plant)
        assertTrue(h.output.stopped.contains(2))
    }

    @Test fun `events deduplicate and use tile placement while level up is centered`() {
        val h = Harness()
        h.player.resume()
        val point = GridPoint(3, 4)
        val placement = SoundPlacement(0.4, -0.6, false)
        h.player.react(
            listOf(GameEvent.Planted(point, 1.0), GameEvent.Planted(GridPoint(4, 4), 1.0)),
            LandscapeTool.water,
            { null },
            {
                assertEquals(point, it)
                placement
            },
        )
        assertEquals(1, h.output.played.size)
        assertTrue(
            h.output.played
                .single()
                .second.left >
                h.output.played
                    .single()
                    .second.right,
        )
        h.player.react(
            listOf(GameEvent.Planted(point, 1.0), GameEvent.LeveledUp(2, 0, 1)),
            LandscapeTool.water,
            { null },
            { error("Fanfare must be centered") },
        )
        assertEquals(2, h.output.played.size)
        val fanfare =
            h.output.played
                .last()
                .second
        assertEquals(fanfare.left, fanfare.right)
    }

    @Test fun `tractor gathered event plays crop pickup sound at tile placement`() {
        val h = Harness()
        h.player.resume()
        val point = GridPoint(2, 5)
        val placement = SoundPlacement(0.8, 0.5, false)
        h.player.react(
            listOf(GameEvent.TractorGathered(point)),
            LandscapeTool.farmland,
            { null },
            {
                assertEquals(point, it)
                placement
            },
        )
        assertEquals(1, h.output.played.size)
        assertEquals(
            1,
            h.output.played
                .single()
                .first.id,
        )
    }
}
