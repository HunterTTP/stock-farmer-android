package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GridPoint
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/** A first-of-its-kind building the farm should set fireworks off around. */
data class BuildingCelebration(
    val origin: GridPoint,
    val width: Int,
    val height: Int,
)

/** Carries celebrations from the game side to the farm scene, which owns the graphics and drains them each frame. */
class FarmCelebrations {
    private val pending = ConcurrentLinkedQueue<BuildingCelebration>()
    private val pendingAcross = AtomicInteger()

    fun celebrateAcross() {
        pendingAcross.incrementAndGet()
    }

    fun drainAcross(handle: () -> Unit) {
        repeat(pendingAcross.getAndSet(0)) { handle() }
    }

    fun clear() {
        pending.clear()
        pendingAcross.set(0)
    }

    fun celebrate(celebration: BuildingCelebration) {
        pending.add(celebration)
    }

    fun drain(handle: (BuildingCelebration) -> Unit) {
        while (true) handle(pending.poll() ?: return)
    }
}
