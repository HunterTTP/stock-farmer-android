package com.hunter.stockfarmer.audio

import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.PetSpecies
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.rules.Weather
import com.hunter.stockfarmer.core.rules.animals
import com.hunter.stockfarmer.core.rules.chicken
import com.hunter.stockfarmer.core.rules.cropGrowthOverview
import com.hunter.stockfarmer.core.rules.isInside
import com.hunter.stockfarmer.core.rules.isWholeFieldGrown
import com.hunter.stockfarmer.core.rules.livestock
import com.hunter.stockfarmer.core.rules.position
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.TILE
import com.hunter.stockfarmer.game.isOnScreen
import com.hunter.stockfarmer.game.screenPosition
import java.time.Instant

/** Builds an immutable snapshot under the session lock; playback happens after releasing it. */
fun GameSession.ambienceSnapshot(now: Instant): AmbienceSnapshot =
    act { engine ->
        val state = engine.state
        val camera = view.snapshot
        val voices =
            engine.animals.mapNotNull { animal ->
                val tile = engine.position(animal) ?: return@mapNotNull null
                if (camera == null || engine.isInside(animal, now)) return@mapNotNull null
                val point =
                    camera.screenPosition(tile, TILE, GameState.GRID_SIZE)
                        ?: return@mapNotNull null
                if (!camera.isOnScreen(point)) return@mapNotNull null
                val cue =
                    when (animal) {
                        is AnimalRef.Pet -> {
                            state.pets[animal.house]?.let {
                                if (it.species == PetSpecies.dog) SoundCue.dogBark else SoundCue.catTrill
                            }
                        }

                        is AnimalRef.Chicken -> {
                            engine.chicken(animal.id, animal.coop)?.let {
                                if (it.isGrown(now) && it.kind.isRooster) SoundCue.roosterCrow else SoundCue.henCluck
                            }
                        }

                        is AnimalRef.Livestock -> {
                            engine.livestock(animal.id, animal.home)?.let {
                                SoundCue.voiceOfLivestock(it.species.id)
                            }
                        }
                    } ?: return@mapNotNull null
                AnimalVoice(cue, this.view.placement(tile, TILE, GameState.GRID_SIZE))
            }
        AmbienceSnapshot(
            rain = Weather.rainAmount(now, state.clockOffset),
            isSnowing = state.season == Season.winter,
            darkness = DayNightCycle.darkness(now, state.clockOffset),
            animals = voices,
            isWholeFieldReady = engine.isWholeFieldGrown(now),
            readyCrops =
                engine
                    .cropGrowthOverview(now)
                    .filter { it.remaining <= 0 }
                    .map { ReadyCrop(it.id, it.crop.name) },
        )
    }

fun GameSession.ambienceDriver(
    sound: SoundCenter,
    onReady: (String) -> Unit = {},
): AmbienceDriver =
    AmbienceDriver(
        snapshot = { now -> if (sound.isActive) ambienceSnapshot(now) else null },
        updateRain = sound::updateRain,
        play = sound::play,
        onReady = onReady,
    ).also { onNightEnded = { sound.stop(SoundCue.nightPulse) } }
