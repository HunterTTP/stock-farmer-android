package com.hunter.stockfarmer.game

import android.os.Handler
import com.hunter.stockfarmer.core.persistence.SaveBackup

/** The saved farms as the screens use them: every answer arrives on the main thread. */
class FarmSlots(
    private val persistence: GamePersistence,
    private val main: Handler,
) {
    fun load(done: (SavedGames) -> Unit) = persistence.slots { games -> main.post { done(games) } }

    fun backups(
        slot: Int,
        done: (List<SaveBackup>) -> Unit,
    ) = persistence.backups(slot) { backups -> main.post { done(backups) } }

    fun switchTo(slot: Int) = persistence.switchTo(slot)

    fun startNew(
        slot: Int,
        name: String,
    ) = persistence.startNew(slot, name)

    fun rename(
        slot: Int,
        name: String,
        done: () -> Unit,
    ) = persistence.rename(slot, name) { main.post(done) }

    fun delete(
        slot: Int,
        done: () -> Unit,
    ) = persistence.delete(slot) { main.post(done) }

    fun restore(
        backup: SaveBackup,
        done: () -> Unit,
    ) = persistence.restoreSaved(backup) { main.post(done) }
}
