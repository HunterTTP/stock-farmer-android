package com.hunter.stockfarmer

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.audio.NightChime
import com.hunter.stockfarmer.audio.SoundCenter
import com.hunter.stockfarmer.audio.ambienceDriver
import com.hunter.stockfarmer.core.feedback.HapticCue
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.rules.minuteOfDay
import com.hunter.stockfarmer.farm.FarmRenderer
import com.hunter.stockfarmer.game.Feedback
import com.hunter.stockfarmer.game.GameViewModel
import com.hunter.stockfarmer.game.TILE
import com.hunter.stockfarmer.haptics.Haptics
import com.hunter.stockfarmer.notifications.DeferredPrompt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/** Turns what happens in the game into sound and haptics, and keeps the music in step with the farm's clock. */
class GameFeedbackDriver(
    private val model: GameViewModel,
    private val sound: SoundCenter,
    private val haptics: Haptics,
    private val notificationPrompt: DeferredPrompt,
) {
    /** Listens while [owner] is started. */
    fun start(owner: LifecycleOwner) {
        val session = model.session
        val ambience =
            session.ambienceDriver(sound) { message ->
                session.showToast(message, durationMillis = Feedback.TOAST_MILLIS)
            }
        val nightChime = NightChime { sound.play(SoundCue.nightPulse) }
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    session.events.collect { events ->
                        HapticCue.cue(events)?.let(haptics::play)
                        if (events.any { it is GameEvent.Planted }) notificationPrompt.request()
                        sound.react(
                            events,
                            landscapeTool = session.read { it.selectedLandscape },
                            petSpecies = { house -> session.read { it.pets[house]?.species } },
                            placement = { tile ->
                                session.view.placement(tile, TILE, GameState.GRID_SIZE)
                            },
                        )
                    }
                }
                launch {
                    session.haptics.collect(haptics::play)
                }
                launch {
                    session.cues.collect { sound.play(it) }
                }
                launch {
                    session.toolStrokes.collect { notificationPrompt.whenIdle() }
                }
                launch {
                    session.dayBegins.collect(nightChime::begin)
                }
                launch {
                    // A farm switch reloads through not-ready; the next farm starts with fresh ambient moments.
                    snapshotFlow { model.persistenceState.isReady }.collect { ready -> if (!ready) ambience.clear() }
                }
                launch {
                    while (true) {
                        val now = Instant.now()
                        val (season, minute, night) =
                            session.act { engine ->
                                Triple(
                                    engine.state.season,
                                    engine.minuteOfDay(now),
                                    NightPulse.day(now, engine.state.clockOffset),
                                )
                            }
                        sound.updateMusic(season, minute, nightChime.hushes(night))
                        nightChime.retry(minute)
                        if (model.persistenceState.isReady) ambience.tick(now)
                        delay(MUSIC_REFRESH_MILLIS)
                    }
                }
            }
        }
    }

    private companion object {
        const val MUSIC_REFRESH_MILLIS = 1_000L
    }
}
