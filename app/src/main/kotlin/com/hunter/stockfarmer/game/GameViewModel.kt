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
import com.hunter.stockfarmer.notifications.NotificationPermissionPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps the game alive across screen rotations, loads and saves it, and lets time pass in it. */
class GameViewModel(
    application: Application,
) : AndroidViewModel(application) {
    var persistenceState by mutableStateOf(PersistenceState())
        private set

    private val main = Handler(Looper.getMainLooper())
    private val persistence =
        GamePersistence(
            SaveSlots(application.filesDir.toPath().resolve("Farms")),
            onState = { state -> main.post { persistenceState = state } },
        )
    val session get() = persistence.session
    val farms = FarmSlots(persistence, main)
    val notificationPolicy = NotificationPermissionPolicy()
    private val tick = GameTick(persistence.session)

    init {
        viewModelScope.launch {
            while (true) {
                if (persistenceState.isReady) session.publish(tick.tick())
                delay(TICK_MILLIS)
            }
        }
    }

    fun resume() = persistence.resume()

    fun pause() = persistence.pause()

    fun retry() = persistence.retry()

    fun restore(backup: SaveBackup) = persistence.restore(backup)

    override fun onCleared() {
        persistence.close()
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}
