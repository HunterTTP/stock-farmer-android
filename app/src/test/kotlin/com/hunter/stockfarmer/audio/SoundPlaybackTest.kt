package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.feedback.SoundCategory
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.feedback.SoundMix
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
        val volumes = mutableMapOf<Int, StereoVolume>()
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
            volumes[played.size] = volume
            return played.size
        }

        override fun setVolume(
            stream: Int,
            volume: StereoVolume,
        ) {
            volumes[stream] = volume
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
        val settings = SoundSettings.from(mapOf("musicVolume" to 0.4, "effectsVolume" to -1.0))
        assertEquals(0.4, settings[AudioChannel.music])
        assertEquals(0.0, settings[AudioChannel.effects])
        assertEquals(1.0, settings[AudioChannel.ambience])
        assertEquals(1.0, settings[AudioChannel.master])
        assertEquals(1.0, settings.withLevel(AudioChannel.ambience, 2.0)[AudioChannel.ambience])
        assertEquals(1.0, settings.withLevel(AudioChannel.ambience, Double.NaN)[AudioChannel.ambience])
    }

    @Test fun `every kind of sound but the ambience is an effect`() {
        for (category in SoundCategory.entries) {
            val channel = if (category == SoundCategory.ambience) AudioChannel.ambience else AudioChannel.effects
            assertEquals(channel, AudioChannel.of(category))
        }
    }

    @Test fun `a slider is in decibels and every channel follows the master`() {
        val settings = SoundSettings().withLevel(AudioChannel.effects, 0.5).withLevel(AudioChannel.master, 0.75)
        assertEquals(SoundMix.gain(forSlider = 0.5), settings.gain(AudioChannel.effects))
        assertEquals(SoundMix.gain(forSlider = 0.75), settings.mixedGain(AudioChannel.master))
        assertEquals(
            SoundMix.gain(forSlider = 0.5) * SoundMix.gain(forSlider = 0.75),
            settings.mixedGain(AudioChannel.effects),
            1e-12,
        )
        val silent = settings.withLevel(AudioChannel.master, 0.0)
        assertTrue(AudioChannel.entries.none(silent::isAudible))
        assertTrue(AudioChannel.entries.all(SoundSettings()::isAudible))
    }

    @Test fun `only animals fall off but every placed cue pans`() {
        val placement = SoundPlacement(1.0, 200.0, 0.0, 400.0, 800.0)
        val animals = StereoVolume.forCue(SoundCue.cowMoo, placement, SoundSettings())!!
        assertEquals(0.4 * SoundCue.cowMoo.trimGain, animals.right.toDouble(), 0.000001)
        assertEquals(0.16 * SoundCue.cowMoo.trimGain, animals.left.toDouble(), 0.000001)
        val farm = StereoVolume.forCue(SoundCue.plant, placement, SoundSettings())!!
        assertEquals(SoundCue.plant.trimGain, farm.right.toDouble(), 0.000001)
        assertEquals(farm.right * 0.4, farm.left.toDouble(), 0.000001)
        val left = StereoVolume.forCue(SoundCue.cowMoo, placement.copy(pan = -0.6), SoundSettings())!!
        assertEquals(animals.left, left.right)
        assertEquals(animals.right, left.left)
    }

    @Test fun `a distant animal is dropped once its trim, distance and sliders leave it inaudible`() {
        // The cow's trim leaves 63% of full volume, so a distance gain of 3.2% is just over the 2% worth a voice.
        assertEquals(null, StereoVolume.forCue(SoundCue.cowMoo, SoundPlacement(0.031, 0.0, false), SoundSettings()))
        assertTrue(StereoVolume.forCue(SoundCue.cowMoo, SoundPlacement(0.032, 0.0, false), SoundSettings()) != null)
        val half = SoundSettings().withLevel(AudioChannel.effects, 0.5)
        assertEquals(null, StereoVolume.forCue(SoundCue.cowMoo, SoundPlacement(0.3, 0.0, false), half))
        assertTrue(StereoVolume.forCue(SoundCue.cowMoo, SoundPlacement(0.4, 0.0, false), half) != null)
        val quiet = SoundSettings().withLevel(AudioChannel.effects, 0.001)
        assertTrue(StereoVolume.forCue(SoundCue.plant, SoundPlacement.centered, quiet) != null)
        val muted = quiet.withLevel(AudioChannel.effects, 0.0)
        assertEquals(null, StereoVolume.forCue(SoundCue.plant, SoundPlacement.centered, muted))
        val masterMuted = quiet.withLevel(AudioChannel.master, 0.0)
        assertEquals(null, StereoVolume.forCue(SoundCue.plant, SoundPlacement.centered, masterMuted))
    }

    @Test fun `a cue plays at its trim times its channel and the master`() {
        val settings = SoundSettings().withLevel(AudioChannel.effects, 0.75).withLevel(AudioChannel.master, 0.5)
        val volume = StereoVolume.forCue(SoundCue.plant, SoundPlacement.centered, settings)!!
        val expected = SoundCue.plant.trimGain * settings.mixedGain(AudioChannel.effects)
        assertEquals(expected, volume.left.toDouble(), 1e-6)
        assertEquals(expected, volume.right.toDouble(), 1e-6)
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

    @Test fun `twelve effect voices and four ambience voices drop newcomers instead of stealing playing streams`() {
        val h = Harness()
        h.player.resume()
        val effects = SoundCue.entries.filter { it.category != SoundCategory.ambience }
        effects.take(12).forEach { assertTrue(h.player.play(it)) }
        assertFalse(h.player.play(effects[12]))
        repeat(2) {
            assertTrue(h.player.play(SoundCue.birdChirp))
            assertTrue(h.player.play(SoundCue.nightPulse))
            h.now += 0.04
        }
        assertFalse(h.player.play(SoundCue.birdChirp))
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
        h.player.play(SoundCue.birdChirp)
        h.player.settings = SoundSettings().withLevel(AudioChannel.ambience, 0.0)
        assertEquals(listOf(2), h.output.stopped)
        assertFalse(h.player.play(SoundCue.birdChirp))
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
        h.player.play(SoundCue.birdChirp)
        h.player.stop(SoundCue.nightPulse)
        assertFalse(h.output.stopped.contains(2))
        h.player.stop(SoundCue.birdChirp)
        assertTrue(h.output.stopped.contains(2))
    }

    @Test fun `a volume change is heard at once on what is playing`() {
        val h = Harness()
        h.player.resume()
        h.player.play(SoundCue.plant)
        h.player.play(SoundCue.birdChirp)
        val quieter = SoundSettings().withLevel(AudioChannel.effects, 0.5).withLevel(AudioChannel.master, 0.75)
        h.player.settings = quieter
        assertEquals(StereoVolume.forCue(SoundCue.plant, SoundPlacement.centered, quieter), h.output.volumes[1])
        assertEquals(StereoVolume.forCue(SoundCue.birdChirp, SoundPlacement.centered, quieter), h.output.volumes[2])
        assertTrue(h.output.stopped.isEmpty())
    }

    @Test fun `only a fanfare ducks the music, for as long as its clip plays`() {
        val h = Harness()
        val ducks = mutableListOf<Double>()
        h.player.onMusicDuck = { ducks += it }
        h.player.resume()
        h.player.play(SoundCue.plant)
        assertTrue(ducks.isEmpty())
        h.player.play(SoundCue.levelUp)
        assertEquals(listOf(10.0), ducks)
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
