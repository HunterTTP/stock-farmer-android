package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.feedback.SoundPlacement
import com.hunter.stockfarmer.core.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AmbienceDriverTest {
    private class SeededRandom(
        private var state: Long,
    ) : RandomSource {
        override fun nextLong(): Long {
            state = state * 6364136223846793005L + 1442695040888963407L
            return state
        }
    }

    private class Harness {
        var state: AmbienceSnapshot? = AmbienceSnapshot(0.0, false, 0.0)
        val played = mutableListOf<Pair<SoundCue, SoundPlacement>>()
        val rain = mutableListOf<Pair<Double, Boolean>>()
        val messages = mutableListOf<String>()
        val driver =
            AmbienceDriver(
                { state },
                { amount, snow -> rain += amount to snow },
                { cue, placement ->
                    played += cue to placement
                    true
                },
                { messages += it },
                SeededRandom(42),
                SeededRandom(84),
            )

        fun tick(seconds: Long) = driver.tick(Instant.ofEpochSecond(seconds))
    }

    @Test fun `birds call once per window only in light dry weather and wet windows are consumed`() {
        val h = Harness()
        h.tick(0)
        h.tick(79)
        assertEquals(listOf(SoundCue.birdChirp), h.played.map { it.first })
        h.tick(79)
        assertEquals(1, h.played.size)
        h.state = h.state!!.copy(darkness = 0.05)
        h.tick(80)
        h.tick(159)
        h.state = h.state!!.copy(darkness = 0.0)
        h.tick(159)
        assertEquals(1, h.played.size)
        h.state = h.state!!.copy(rain = 0.05)
        h.tick(160)
        h.tick(239)
        h.state = h.state!!.copy(rain = 0.0)
        h.tick(239)
        assertEquals(1, h.played.size)
        h.tick(240)
        h.tick(319)
        assertEquals(2, h.played.size)
    }

    @Test fun `rain is eased and winter is passed to the existing silent snow player`() {
        val h = Harness()
        h.state = h.state!!.copy(rain = 0.25)
        h.tick(0)
        assertEquals(0.15625 to false, h.rain.last())
        h.state = h.state!!.copy(isSnowing = true)
        h.tick(1)
        assertEquals(0.15625 to true, h.rain.last())
        h.state = null
        h.tick(79)
        assertEquals(2, h.rain.size)
        assertTrue(h.played.isEmpty())
    }

    @Test fun `animal scheduling excludes hidden and distant animals and preserves placement`() {
        val voices = AnimalVoices(SeededRandom(84))
        val placement = SoundPlacement(1.0, -0.4, true)
        val audible = AnimalVoice(SoundCue.cowMoo, placement)
        val choices =
            listOf(
                AnimalVoice(SoundCue.catTrill, placement, isVisible = false),
                AnimalVoice(SoundCue.dogBark, SoundPlacement(0.3, 0.2, false)),
                audible,
                AnimalVoice(SoundCue.henCluck, SoundPlacement.centered),
            )
        assertNull(voices.next(0.0, choices))
        assertEquals(audible, voices.next(179.0, choices))
        assertNull(voices.next(179.0, choices))
        assertNull(voices.next(180.0, choices))
        val next = voices.next(359.0, choices)
        assertEquals(SoundCue.cowMoo, next!!.cue)
        assertEquals(placement, next.placement)
        voices.clear()
        assertNull(voices.next(0.0, emptyList()))
    }

    @Test fun `ready transitions play the chime and publish the matching message once`() {
        val h = Harness()
        h.tick(0)
        h.state = h.state!!.copy(isWholeFieldReady = true, readyCrops = listOf(ReadyCrop("beetroot", "Beetroot")))
        h.tick(1)
        h.tick(2)
        assertEquals(listOf("All crops ready to harvest!"), h.messages)
        assertEquals(1, h.played.count { it.first == SoundCue.allCropsReady })
        h.driver.clear()
        h.tick(3)
        assertEquals(2, h.messages.size)
    }
}
