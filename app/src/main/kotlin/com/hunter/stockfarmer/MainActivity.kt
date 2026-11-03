package com.hunter.stockfarmer

import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.hunter.stockfarmer.audio.NightChime
import com.hunter.stockfarmer.audio.SoundCenter
import com.hunter.stockfarmer.audio.ambienceDriver
import com.hunter.stockfarmer.core.feedback.HapticCue
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.presentation.LaunchSequence
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.rules.minuteOfDay
import com.hunter.stockfarmer.farm.FarmFragment
import com.hunter.stockfarmer.farm.FarmRenderer
import com.hunter.stockfarmer.farm.FarmWeather
import com.hunter.stockfarmer.game.Feedback
import com.hunter.stockfarmer.game.GameViewModel
import com.hunter.stockfarmer.haptics.Haptics
import com.hunter.stockfarmer.notifications.FieldReadyNotificationPermission
import com.hunter.stockfarmer.notifications.FieldReadyNotifier
import com.hunter.stockfarmer.notifications.WorkManagerFieldReadyScheduler
import com.hunter.stockfarmer.ui.Hud
import com.hunter.stockfarmer.ui.rewards.RewardFlights
import com.hunter.stockfarmer.ui.rewards.RewardFlightsHost
import com.hunter.stockfarmer.ui.screens.LaunchPresentation
import com.hunter.stockfarmer.ui.screens.LaunchScreen
import com.hunter.stockfarmer.ui.screens.PerformanceSampling
import com.hunter.stockfarmer.ui.screens.SaveSlotsScreen
import com.hunter.stockfarmer.ui.timelapse.LocalTimelapseRecorder
import com.hunter.stockfarmer.ui.timelapse.TimelapseRecorder
import com.hunter.stockfarmer.ui.timelapse.rememberTimelapseRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import android.graphics.Color as AndroidColor

class MainActivity :
    FragmentActivity(),
    AndroidFragmentApplication.Callbacks {
    private val model: GameViewModel by viewModels()
    private lateinit var sound: SoundCenter
    private lateinit var haptics: Haptics
    private lateinit var notifications: FieldReadyNotificationPermission
    private lateinit var fieldReady: FieldReadyNotifier
    private val farmContainerId = View.generateViewId()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK))
        sound = SoundCenter(this)
        haptics = Haptics(window.decorView)
        notifications = FieldReadyNotificationPermission(this, model.notificationPolicy)
        fieldReady = FieldReadyNotifier(WorkManagerFieldReadyScheduler(applicationContext))
        lifecycle.addObserver(sound)
        listenForSound()
        // Compose creates the host after loading; a restored fragment still refers to the old host's view ID.
        supportFragmentManager.findFragmentByTag(FARM_TAG)?.let { farm ->
            supportFragmentManager.commitNow { remove(farm) }
        }
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    val persistence = model.persistenceState
                    var launchId by remember { mutableIntStateOf(0) }
                    var launching by remember { mutableStateOf(true) }
                    PerformanceSampling(model.performance, nodeCount = { model.session.view.drawnSprites })
                    // The farm stays mounted through a farm switch: removing its view while the game thread is paused
                    // leaves libGDX waiting for a pause that never completes, and it kills the process.
                    FarmHost()
                    if (persistence.isReady) {
                        val recorder = rememberTimelapseRecorder(model.session)
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
                    LaunchedEffect(persistence.isReady) {
                        if (!persistence.isReady && !launching) {
                            launchId++
                            launching = true
                        }
                    }
                    if (launching || !persistence.isReady) {
                        key(launchId) {
                            var isSceneBuilt by remember { mutableStateOf(false) }
                            var framesDrawn by remember { mutableIntStateOf(0) }
                            var choosingFarm by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    isSceneBuilt = model.session.view.isSceneBuilt
                                    framesDrawn =
                                        model.session.view.framesDrawn
                                            .coerceAtMost(2)
                                    delay(RENDERER_POLL_MILLIS)
                                }
                            }
                            val stage =
                                LaunchPresentation.stage(
                                    persistence.isReady,
                                    persistence.isLoading,
                                    isSceneBuilt,
                                    framesDrawn,
                                )
                            LaunchScreen(
                                stage = stage,
                                saveError = persistence.error,
                                onRetry = model::retry,
                                onChooseFarm = {
                                    if (stage == LaunchSequence.Stage.failed) {
                                        choosingFarm = true
                                    }
                                },
                                onComplete = { launching = false },
                            )
                            if (stage == LaunchSequence.Stage.failed) {
                                Column(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
                                    persistence.backups.forEach { backup ->
                                        TextButton(onClick = { model.restore(backup) }) {
                                            Text(stringResource(R.string.restore_backup, backup.kind.name))
                                        }
                                    }
                                }
                                if (choosingFarm) {
                                    SaveSlotsScreen(
                                        model.farms,
                                        onClose = { choosingFarm = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Turns what happens in the game into sound, and keeps the music in step with the farm's clock. */
    private fun listenForSound() {
        val session = model.session
        val ambience =
            session.ambienceDriver(sound) { message ->
                session.showToast(message, durationMillis = Feedback.TOAST_MILLIS)
            }
        val nightChime = NightChime { sound.play(SoundCue.nightPulse) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    session.events.collect { events ->
                        HapticCue.cue(events)?.let(haptics::play)
                        if (events.any { it is GameEvent.Planted }) notifications.requestIfNeeded(hasCrops = true)
                        sound.react(
                            events,
                            landscapeTool = session.read { it.selectedLandscape },
                            petSpecies = { house -> session.read { it.pets[house]?.species } },
                            placement = { tile ->
                                session.view.placement(tile, FarmRenderer.TILE, GameState.GRID_SIZE)
                            },
                        )
                    }
                }
                launch {
                    session.haptics.collect(haptics::play)
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

    override fun onResume() {
        super.onResume()
        model.resume()
        fieldReady.onActive()
        notifications.requestIfNeeded(model.session.read { it.plots.isNotEmpty() })
    }

    // The game pauses on stop, not pause: a visible but unfocused window (split screen, a dialog) keeps playing.
    override fun onStop() {
        // Readiness is worked out before the pause holds the farm's clock.
        model.session.act { fieldReady.onBackground(it, Instant.now()) }
        model.pause()
        super.onStop()
    }

    @Composable
    private fun FarmHost() {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                FragmentContainerView(context).apply {
                    id = farmContainerId
                    // This view is new each time the host re-enters composition, so the farm is always put into it.
                    supportFragmentManager.commit { replace(id, FarmFragment(), FARM_TAG) }
                }
            },
        )
    }

    override fun exit() = Unit

    private companion object {
        const val MUSIC_REFRESH_MILLIS = 1_000L
        const val RENDERER_POLL_MILLIS = 16L
        const val FARM_TAG = "farm"
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
