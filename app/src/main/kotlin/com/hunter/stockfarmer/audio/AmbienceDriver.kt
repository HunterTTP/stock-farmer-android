package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.feedback.RandomMomentTrigger
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.feedback.SoundPlacement
import com.hunter.stockfarmer.core.rules.Weather
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import java.time.Instant

data class AmbienceSnapshot(
    val rain: Double,
    val isSnowing: Boolean,
    val darkness: Double,
    val animals: List<AnimalVoice> = emptyList(),
    val isWholeFieldReady: Boolean = false,
    val readyCrops: List<ReadyCrop> = emptyList(),
    val birdPlacement: SoundPlacement = SoundPlacement.centered,
)

/** Call on the main thread while playing. A null snapshot suspends scheduling without reading the paused farm clock. */
class AmbienceDriver(
    private val snapshot: (Instant) -> AmbienceSnapshot?,
    private val updateRain: (Double, Boolean) -> Unit,
    private val play: (SoundCue, SoundPlacement) -> Boolean,
    private val onReady: (String) -> Unit = {},
    private val birdRandom: RandomSource = SystemRandomSource,
    animalRandom: RandomSource = SystemRandomSource,
) {
    private var birds = RandomMomentTrigger(80.0)
    private val animals = AnimalVoices(animalRandom)
    private val ready = ReadyChime()

    fun clear() {
        birds = RandomMomentTrigger(80.0)
        animals.clear()
        ready.clear()
    }

    fun tick(now: Instant) {
        val state = snapshot(now) ?: return
        val seconds = now.epochSecond + now.nano / 1_000_000_000.0
        updateRain(Weather.eased(state.rain), state.isSnowing)
        val (next, due) = birds.isDue(seconds, birdRandom)
        birds = next
        if (due && state.darkness < 0.05 && state.rain < 0.05) play(SoundCue.birdChirp, state.birdPlacement)
        animals.next(seconds, state.animals)?.let { play(it.cue, it.placement) }
        ready.update(state.isWholeFieldReady, state.readyCrops)?.let { message ->
            play(SoundCue.allCropsReady, SoundPlacement.centered)
            onReady(message)
        }
    }
}
