package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.persistence.FarmStartup
import com.hunter.stockfarmer.core.persistence.NewerSaveVersionException
import com.hunter.stockfarmer.core.persistence.SaveBackup
import com.hunter.stockfarmer.core.persistence.SaveCoordinator
import com.hunter.stockfarmer.core.persistence.SaveSlots
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.holdClockWhileAway
import com.hunter.stockfarmer.core.rules.markSaved
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import java.time.Instant

data class PersistenceState(
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val error: String? = null,
    val backups: List<SaveBackup> = emptyList(),
)

/** Owns startup and lifecycle saves. Only the scheduler touches this class's mutable state. */
class GamePersistence(
    slots: SaveSlots,
    private val scheduler: SaveScheduler = BackgroundSaveScheduler(),
    private val now: () -> Instant = Instant::now,
    private val random: RandomSource = SystemRandomSource,
    private val onState: (PersistenceState) -> Unit = {},
) : AutoCloseable {
    val session = GameSession(GameEngine())
    private var state = PersistenceState()
    private var active = false
    private var closed = false
    private var slot = 0
    private var leftAt: Instant? = null
    private var cancelTimer: (() -> Unit)? = null
    private var cancelSave: (() -> Unit)? = null
    private val saver =
        SaveCoordinator(slots) { _, result ->
            scheduler.execute {
                if (!closed) publish(state.copy(error = if (result.isFailure) SAVE_ERROR else null))
            }
        }

    init {
        retry()
    }

    fun retry() =
        scheduler.execute {
            if (closed) return@execute
            if (state.isReady) save(flush = true) else load()
        }

    /** Recovery is an explicit choice, as on iOS; a failed load never creates a replacement farm. */
    fun restore(backup: SaveBackup) =
        scheduler.execute {
            if (!closed && !state.isReady && backup in state.backups) load(backup)
        }

    fun resume() =
        scheduler.execute {
            if (closed || active) return@execute
            active = true
            if (state.isReady) {
                holdClock()
                requestSave()
                startTimer()
            }
        }

    /** Pause and stop both bypass the debounce and flush through the disk queue. */
    fun pause() =
        scheduler.execute {
            if (closed) return@execute
            active = false
            cancelTimer?.invoke()
            cancelTimer = null
            if (state.isReady) {
                if (leftAt == null) leftAt = now()
                save(flush = true)
            }
        }

    private fun load(backup: SaveBackup? = null) {
        publish(PersistenceState())
        try {
            val startup =
                saver.whenIdle { slots ->
                    if (backup != null) slots.restore(backup, now())
                    FarmStartup(slots, now(), random)
                }
            slot = startup.slot
            session.act { it.state = startup.engine.state }
            leftAt = startup.preparedAt
            if (active) holdClock()
            publish(PersistenceState(isLoading = false, isReady = true))
            if (active) {
                requestSave()
                startTimer()
            }
        } catch (failure: Exception) {
            val backups = runCatching { saver.whenIdle { it.backups(it.activeSlot) } }.getOrDefault(emptyList())
            val message =
                if (failure is NewerSaveVersionException) {
                    "This farm needs a newer version of Stock Farmer. Its save has been kept safe."
                } else {
                    "This farm could not be opened. Its save has been kept safe. Retry, or restore a backup."
                }
            publish(PersistenceState(isLoading = false, error = message, backups = backups))
        }
    }

    private fun holdClock() {
        leftAt?.let { left -> session.act { it.holdClockWhileAway(left, now()) } }
        leftAt = null
    }

    private fun startTimer() {
        cancelTimer?.invoke()
        cancelTimer =
            scheduler.after(AUTOSAVE_INTERVAL_MS) {
                if (active && !closed) {
                    requestSave()
                    startTimer()
                }
            }
    }

    private fun requestSave() {
        cancelSave?.invoke()
        cancelSave = scheduler.after(SAVE_DEBOUNCE_MS) { save(flush = false) }
    }

    private fun save(flush: Boolean) {
        cancelSave?.invoke()
        cancelSave = null
        if (!state.isReady) return
        val date = now()
        val snapshot =
            session.act { engine ->
                engine.markSaved(date)
                engine
                    .copy()
                    .also { copy ->
                        // A second inactive save must not count the pause-to-stop delay as playing time on relaunch.
                        leftAt?.let { copy.holdClockWhileAway(it, date) }
                    }.state
            }
        try {
            if (flush) saver.saveNow(snapshot, slot) else saver.saveSoon(snapshot, slot)
        } catch (_: Exception) {
            publish(state.copy(error = SAVE_ERROR))
        }
    }

    private fun publish(value: PersistenceState) {
        state = value
        onState(value)
    }

    override fun close() =
        scheduler.execute {
            if (closed) return@execute
            cancelTimer?.invoke()
            save(flush = true)
            closed = true
            saver.close()
            scheduler.close()
        }

    companion object {
        const val AUTOSAVE_INTERVAL_MS = 1_000L
        const val SAVE_DEBOUNCE_MS = 250L
        private const val SAVE_ERROR = "Your latest changes could not be saved. Please retry before leaving this farm."
    }
}
