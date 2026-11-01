package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.newGame
import com.hunter.stockfarmer.core.rules.prepareTimelapse
import com.hunter.stockfarmer.core.rules.reopen
import com.hunter.stockfarmer.core.rules.updateCoops
import com.hunter.stockfarmer.core.rules.updateFish
import com.hunter.stockfarmer.core.rules.updateHerds
import com.hunter.stockfarmer.core.util.RandomSource
import java.time.Instant

/** Read-only preparation that can run away from the UI and never replaces a failed save with a new farm. */
class FarmStartup(
    slots: SaveSlots,
    now: Instant,
    using: RandomSource,
) {
    val slot: Int = slots.activeSlot
    val engine: GameEngine
    val preparedAt: Instant = now

    init {
        val state = slots.load(slot) ?: newGame(using, now)
        val prepared = GameEngine(state)
        prepared.reopen(now, using)
        prepared.updateCoops(now, using)
        prepared.updateHerds(now, using)
        prepared.updateFish(now = now, using = using)
        prepared.prepareTimelapse()
        engine = prepared
    }
}
