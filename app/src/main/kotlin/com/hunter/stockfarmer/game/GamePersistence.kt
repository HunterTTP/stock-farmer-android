package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.persistence.FarmStartup
import com.hunter.stockfarmer.core.persistence.NewerSaveVersionException
import com.hunter.stockfarmer.core.persistence.SaveBackup
import com.hunter.stockfarmer.core.persistence.SaveCoordinator
import com.hunter.stockfarmer.core.persistence.SaveSlotInfo
import com.hunter.stockfarmer.core.persistence.SaveSlots
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.holdClockWhileAway
import com.hunter.stockfarmer.core.rules.markSaved
import com.hunter.stockfarmer.core.rules.newGame
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import java.time.Instant

/** The saved games, by slot (null where nothing is saved), and the slot being played. */
data class SavedGames(
    val slots: List<SaveSlotInfo?>,
    val active: Int,
)

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

    /** The saved games and which one is being played, for the load screen. */
    fun slots(done: (SavedGames) -> Unit) =
        scheduler.execute {
            if (!closed) done(saver.whenIdle { SavedGames(it.slots(), it.activeSlot) })
        }

    fun backups(
        slot: Int,
        done: (List<SaveBackup>) -> Unit,
    ) = scheduler.execute {
        if (!closed) done(saver.whenIdle { it.backups(slot) })
    }

    /** Leaves the farm being played, saved, for another saved game. */
    fun switchTo(target: Int) = changeFarm { it.setActive(target) }

    /** Starts a new farm under a name in a slot, replacing anything saved there. */
    fun startNew(
        target: Int,
        name: String,
    ) = changeFarm {
        it.create(target, name, newGame(random, now()), now())
        it.setActive(target)
    }

    fun rename(
        target: Int,
        name: String,
        done: () -> Unit,
    ) = changeSlots(done) { it.rename(target, name) }

    /** The farm being played cannot be deleted. */
    fun delete(
        target: Int,
        done: () -> Unit,
    ) = changeSlots(done) { if (target != slot) it.delete(target) }

    /** Brings a backup back as its slot's farm; the farm it replaces stays as the latest backup. */
    fun restoreSaved(
        backup: SaveBackup,
        done: () -> Unit,
    ) = if (backup.slot ==
        slot
    ) {
        changeFarm { it.restore(backup, now()) }
    } else {
        changeSlots(done) { it.restore(backup, now()) }
    }

    private fun changeFarm(change: (SaveSlots) -> Unit) =
        scheduler.execute {
            if (closed || !state.isReady) return@execute
            save(flush = true)
            try {
                saver.whenIdle(change)
            } catch (_: Exception) {
                publish(state.copy(error = SLOT_ERROR))
                return@execute
            }
            load()
        }

    private fun changeSlots(
        done: () -> Unit,
        change: (SaveSlots) -> Unit,
    ) = scheduler.execute {
        if (closed) return@execute
        try {
            saver.whenIdle(change)
        } catch (_: Exception) {
            publish(state.copy(error = SLOT_ERROR))
        }
        done()
    }

    fun resume() =
        scheduler.execute {
            if (closed || active) return@execute
            active = true
            session.isActive = state.isReady
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
            session.isActive = false
            cancelTimer?.invoke()
            cancelTimer = null
            if (state.isReady) {
                session.act { session.wildGrowth.advance(it, now(), finishing = true) }
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
            session.act {
                it.state = startup.engine.state
                session.wildGrowth.clear()
                session.tractor.clear()
                session.sceneFeedback.clear()
                session.casts.value = null
                session.sheets.value = null
                session.confirmations.value = null
            }
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
        session.isSaveBlocked = !value.isReady || value.error != null
        session.isActive = active && value.isReady
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
        private const val SLOT_ERROR = "The farm could not be changed. Please try again."
    }
}
