package com.hunter.stockfarmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.audio.SoundCenter
import com.hunter.stockfarmer.farm.FarmWeather
import com.hunter.stockfarmer.game.GameViewModel
import com.hunter.stockfarmer.ui.Hud
import com.hunter.stockfarmer.ui.rewards.RewardFlights
import com.hunter.stockfarmer.ui.rewards.RewardFlightsHost
import com.hunter.stockfarmer.ui.screens.PerformanceSampling
import com.hunter.stockfarmer.ui.timelapse.LocalTimelapseRecorder
import com.hunter.stockfarmer.ui.timelapse.TimelapseRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant

/** The whole screen: the farm underneath, the HUD above it once the save is loaded, and the launch screen on top. */
@Composable
fun GameRoot(
    model: GameViewModel,
    sound: SoundCenter,
    farmHost: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val persistence = model.persistenceState
        PerformanceSampling(model.performance, nodeCount = { model.session.view.drawnSprites })
        // The farm stays mounted through a farm switch: removing its view while the game thread is paused
        // leaves libGDX waiting for a pause that never completes, and it kills the process.
        farmHost()
        if (persistence.isReady) {
            val recorder = remember(model) { TimelapseRecorder(model.session, model.timelapseStore) }
            TimelapseCapture(recorder, ready = !persistence.isSaveBlocked)
            FarmWeather(model.session) {
                RewardFlightsHost(model.session) {
                    CompositionLocalProvider(LocalTimelapseRecorder provides recorder) {
                        Hud(
                            model.session,
                            sound,
                            model.farms,
                            model.performance,
                            Modifier.fillMaxSize(),
                            saveError = persistence.error,
                            onRetrySave = model::retry,
                        )
                    }
                    RewardFlights(model.session, Modifier.fillMaxSize())
                }
            }
        }
        LaunchOverlay(model)
    }
}

/** Ticks the recorder once a second while the app is resumed and the farm is saveable; stops it otherwise. */
@Composable
private fun TimelapseCapture(
    recorder: TimelapseRecorder,
    ready: Boolean,
) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(recorder, owner, ready) {
        recorder.reset()
        if (!ready) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            recorder.reset()
            try {
                while (isActive) {
                    recorder.tick(Instant.now())
                    delay(1_000)
                }
            } finally {
                recorder.reset()
            }
        }
    }
}
