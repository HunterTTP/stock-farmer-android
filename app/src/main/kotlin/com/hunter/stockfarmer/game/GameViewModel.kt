package com.hunter.stockfarmer.game

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hunter.stockfarmer.core.persistence.SaveBackup
import com.hunter.stockfarmer.core.persistence.SaveSlots
import com.hunter.stockfarmer.core.persistence.TimelapseStore
import com.hunter.stockfarmer.diagnostics.PerformanceMonitor
import com.hunter.stockfarmer.notifications.NotificationPermissionPolicy
import kotlinx.coroutines.Dispatchers

/** Keeps the game alive across screen rotations, loads and saves it, and lets time pass in it. */
class GameViewModel(
    application: Application,
) : AndroidViewModel(application) {
    var persistenceState by mutableStateOf(PersistenceState())
        private set

    private val main = Handler(Looper.getMainLooper())

    /** The one store over its directory: it takes turns only with itself, and both persistence and capture write there. */
    val timelapseStore = TimelapseStore(application.filesDir.toPath().resolve("Timelapse"))
    private val persistence =
        GamePersistence(
            SaveSlots(application.filesDir.toPath().resolve("Farms")),
            timelapseStore = timelapseStore,
            onState = { state -> main.post { persistenceState = state } },
        )
    val session get() = persistence.session
    val farms = FarmSlots(persistence, main)
    val performance = PerformanceMonitor()
    val notificationPolicy = NotificationPermissionPolicy()
    private val tick = GameTick(persistence.session)

    /**
     * The tick catches up on time away and plans each night, so it runs off the main thread. Everything it touches is
     * behind the session lock, a thread-safe flow or an atomic, and its events are handed on from here.
     */
    private val tickLoop =
        TickLoop(viewModelScope, Dispatchers.Default, TICK_MILLIS, onStart = tick::forgetClocks) {
            if (persistenceState.isReady) session.publish(tick.tick())
        }

    fun resume() {
        persistence.resume()
        tickLoop.start()
    }

    fun pause() {
        tickLoop.stop()
        persistence.pause()
    }

    fun retry() = persistence.retry()

    fun restore(backup: SaveBackup) = persistence.restore(backup)

    override fun onCleared() {
        persistence.close()
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}
