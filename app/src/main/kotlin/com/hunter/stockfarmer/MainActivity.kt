package com.hunter.stockfarmer

import android.media.AudioManager
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.hunter.stockfarmer.audio.SoundCenter
import com.hunter.stockfarmer.farm.FarmFragment
import com.hunter.stockfarmer.game.GameViewModel
import com.hunter.stockfarmer.haptics.Haptics
import com.hunter.stockfarmer.notifications.DeferredPrompt
import com.hunter.stockfarmer.notifications.FieldReadyNotificationPermission
import com.hunter.stockfarmer.notifications.FieldReadyNotifier
import com.hunter.stockfarmer.notifications.WorkManagerFieldReadyScheduler
import java.time.Instant
import android.graphics.Color as AndroidColor

class MainActivity :
    FragmentActivity(),
    AndroidFragmentApplication.Callbacks {
    private val model: GameViewModel by viewModels()
    private lateinit var sound: SoundCenter
    private lateinit var haptics: Haptics
    private lateinit var notifications: FieldReadyNotificationPermission
    private lateinit var notificationPrompt: DeferredPrompt
    private lateinit var fieldReady: FieldReadyNotifier
    private val farmContainerId = View.generateViewId()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK))
        // libGDX audio is off, and it was what routed the volume keys to the music stream.
        volumeControlStream = AudioManager.STREAM_MUSIC
        sound = SoundCenter(this)
        haptics = Haptics(window.decorView)
        notifications = FieldReadyNotificationPermission(this, model.notificationPolicy)
        // The dialog takes the touch, so it waits for a planting drag to finish rather than cut it short.
        notificationPrompt =
            DeferredPrompt({ model.session.toolStrokes.value }) { notifications.requestIfNeeded(hasCrops = true) }
        fieldReady = FieldReadyNotifier(WorkManagerFieldReadyScheduler(applicationContext))
        lifecycle.addObserver(sound)
        GameFeedbackDriver(model, sound, haptics, notificationPrompt).start(this)
        // A restored fragment still refers to the previous host's view ID, so the new host starts clean.
        supportFragmentManager.findFragmentByTag(FARM_TAG)?.let { farm ->
            supportFragmentManager.commitNow { remove(farm) }
        }
        setContent {
            MaterialTheme {
                GameRoot(model, sound, farmHost = { FarmHost() })
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
        const val FARM_TAG = "farm"
    }
}
