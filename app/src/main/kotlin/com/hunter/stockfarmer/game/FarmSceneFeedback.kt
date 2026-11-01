package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEvent
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

/** Presentation requests are consumed by the scene; no renderer is needed by the gesture decisions. */
class FarmSceneFeedback {
    data class Flash(
        val tiles: List<GridPoint>,
        val allowed: Boolean,
        val at: Instant,
    )

    data class Interaction(
        val origin: GridPoint,
        val building: Boolean,
        val at: Instant,
    )

    private val flashes = ConcurrentLinkedQueue<Flash>()
    private val interactions = ConcurrentLinkedQueue<Interaction>()

    @Volatile
    var catchButterfly: ((GridPoint, Instant) -> List<GameEvent>)? = null

    fun flash(
        tiles: List<GridPoint>,
        allowed: Boolean,
        now: Instant,
    ) {
        flashes.add(Flash(tiles, allowed, now))
    }

    fun interact(
        origin: GridPoint,
        building: Boolean,
        now: Instant,
    ) {
        interactions.add(Interaction(origin, building, now))
    }

    fun drainFlashes(): List<Flash> =
        buildList {
            while (true) add(flashes.poll() ?: break)
        }

    fun drainInteractions(): List<Interaction> =
        buildList {
            while (true) add(interactions.poll() ?: break)
        }

    fun clear() {
        flashes.clear()
        interactions.clear()
    }
}
