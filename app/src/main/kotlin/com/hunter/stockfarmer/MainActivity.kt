package com.hunter.stockfarmer

import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.hunter.stockfarmer.audio.SoundCenter
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.rules.minuteOfDay
import com.hunter.stockfarmer.farm.FarmFragment
import com.hunter.stockfarmer.farm.FarmRenderer
import com.hunter.stockfarmer.game.GameViewModel
import com.hunter.stockfarmer.notifications.FieldReadyNotificationPermission
import com.hunter.stockfarmer.notifications.FieldReadyNotifier
import com.hunter.stockfarmer.notifications.WorkManagerFieldReadyScheduler
import com.hunter.stockfarmer.ui.Hud
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

class MainActivity :
    FragmentActivity(),
    AndroidFragmentApplication.Callbacks {
    private val model: GameViewModel by viewModels()
    private lateinit var sound: SoundCenter
    private lateinit var notifications: FieldReadyNotificationPermission
    private lateinit var fieldReady: FieldReadyNotifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sound = SoundCenter(this)
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
                Box(Modifier.fillMaxSize()) {
                    val persistence = model.persistenceState
                    if (persistence.isReady) {
                        FarmHost()
                        Hud(model.session, sound, Modifier.fillMaxSize())
                        persistence.error?.let { message ->
                            AlertDialog(
                                onDismissRequest = {},
                                title = { Text("Save failed") },
                                text = { Text(message) },
                                confirmButton = { TextButton(onClick = model::retry) { Text("Retry") } },
                            )
                        }
                    } else {
                        Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                            if (persistence.isLoading) {
                                CircularProgressIndicator()
                                Text("Opening your farm…")
                            } else {
                                Text(persistence.error.orEmpty())
                                Button(onClick = model::retry) { Text("Retry") }
                                persistence.backups.forEach { backup ->
                                    TextButton(onClick = { model.restore(backup) }) {
                                        Text("Restore ${backup.kind.name} backup")
                                    }
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    session.events.collect { events ->
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
                    while (true) {
                        val now = Instant.now()
                        val (season, minute, chimed) =
                            session.act { engine ->
                                val minute = engine.minuteOfDay(now)
                                val night = NightPulse.day(now, engine.state.clockOffset)
                                Triple(engine.state.season, minute, engine.state.lastNewDayNight == night)
                            }
                        sound.updateMusic(season, minute, chimed)
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

    override fun onPause() {
        model.pause()
        super.onPause()
    }

    override fun onStop() {
        // Readiness is worked out before the pause holds the farm's clock, as on iOS.
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
                    id = View.generateViewId()
                    if (supportFragmentManager.findFragmentByTag(FARM_TAG) == null) {
                        supportFragmentManager.commit { replace(id, FarmFragment(), FARM_TAG) }
                    }
                }
            },
        )
    }

    override fun exit() = Unit

    private companion object {
        const val MUSIC_REFRESH_MILLIS = 1_000L
        const val FARM_TAG = "farm"
    }
}
