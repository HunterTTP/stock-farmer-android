package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Serial disk access with at most one pending snapshot per slot. Pending snapshots are lock-protected; write failures are
 * confined to the disk thread.
 */
class SaveCoordinator(
    private val slots: SaveSlots,
    private val onResult: (Int, Result<Unit>) -> Unit = { _, _ -> },
) : AutoCloseable {
    private val queue =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "StockFarmer.saves").apply { isDaemon = true }
        }
    private val lock = ReentrantLock()
    private val pending = mutableMapOf<Int, GameState>()
    private val order = ArrayDeque<Int>()
    private var isDraining = false
    private val failures = mutableMapOf<Int, Throwable>()

    fun saveSoon(
        state: GameState,
        slot: Int,
    ) {
        if (slot !in 0 until SaveSlots.COUNT) return
        lock.withLock {
            if (slot !in pending) order.addLast(slot)
            pending[slot] = state
            if (!isDraining) {
                isDraining = true
                queue.execute { drain() }
            }
        }
    }

    fun saveNow(
        state: GameState,
        slot: Int,
    ) {
        saveSoon(state, slot)
        onQueue { failures[slot]?.let { throw it } }
    }

    fun <T> whenIdle(work: (SaveSlots) -> T): T = onQueue { work(slots) }

    /** Finishes queued writes and releases the disk worker when its app session ends. */
    override fun close() = queue.shutdown()

    private fun <T> onQueue(work: () -> T): T =
        try {
            queue.submit(Callable { work() }).get()
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }

    private fun next(): Pair<Int, GameState>? =
        lock.withLock {
            if (order.isEmpty()) {
                isDraining = false
                return null
            }
            val slot = order.removeFirst()
            pending.remove(slot)?.let { slot to it }
        }

    private fun drain() {
        while (true) {
            val (slot, state) = next() ?: return
            try {
                slots.save(state, slot)
                failures.remove(slot)
                onResult(slot, Result.success(Unit))
            } catch (failure: Exception) {
                failures[slot] = failure
                onResult(slot, Result.failure(failure))
            }
        }
    }
}
