package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Serial disk access with at most one pending snapshot per slot. Pending snapshots are lock-protected; write failures are
 * confined to the disk thread, which yields to the game unless someone is waiting on it.
 */
class SaveCoordinator(
    private val slots: SaveSlots,
    private val onResult: (Int, Result<Unit>) -> Unit = { _, _ -> },
) : AutoCloseable {
    companion object {
        /** Yields to the game, yet Android maps it to background nice 10: its minimum, nice 19, can starve saves past a second. */
        internal const val YIELDING_PRIORITY = 4
    }

    private class Pending(
        val state: GameState,
        val alwaysWrite: Boolean,
    )

    private val waiting = AtomicInteger()

    @Volatile private var worker: Thread? = null
    private val queue =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "StockFarmer.saves").apply {
                isDaemon = true
                priority = if (waiting.get() > 0) Thread.NORM_PRIORITY else YIELDING_PRIORITY
                worker = this
            }
        }
    private val lock = ReentrantLock()
    private val pending = mutableMapOf<Int, Pending>()
    private val order = ArrayDeque<Int>()
    private var isDraining = false
    private val failures = mutableMapOf<Int, Throwable>()

    fun saveSoon(
        state: GameState,
        slot: Int,
    ) = enqueue(state, slot, alwaysWrite = true)

    /** An autosave: skipped on the disk thread if the game holds nothing new. Failures still report to [onResult]. */
    fun saveIfChanged(
        state: GameState,
        slot: Int,
    ) = enqueue(state, slot, alwaysWrite = false)

    fun saveNow(
        state: GameState,
        slot: Int,
    ) = waitedOn {
        saveSoon(state, slot)
        onQueue { failures[slot]?.let { throw it } }
    }

    fun <T> whenIdle(work: (SaveSlots) -> T): T = onQueue { work(slots) }

    /** Finishes queued writes and releases the disk worker when its app session ends. */
    override fun close() = queue.shutdown()

    private fun enqueue(
        state: GameState,
        slot: Int,
        alwaysWrite: Boolean,
    ) {
        if (slot !in 0 until SaveSlots.COUNT) return
        lock.withLock {
            val replaced = pending[slot]
            if (replaced == null) order.addLast(slot)
            // A flush coalesced with an autosave is still a flush, whichever came first.
            pending[slot] = Pending(state, alwaysWrite || replaced?.alwaysWrite == true)
            if (!isDraining) {
                isDraining = true
                queue.execute { drain() }
            }
        }
    }

    /** Whoever blocks on the disk thread is a load or a lifecycle flush, which must not crawl behind the game's threads. */
    private fun <T> waitedOn(work: () -> T): T {
        if (waiting.getAndIncrement() == 0) worker?.priority = Thread.NORM_PRIORITY
        return try {
            work()
        } finally {
            if (waiting.decrementAndGet() == 0) worker?.priority = YIELDING_PRIORITY
        }
    }

    private fun <T> onQueue(work: () -> T): T =
        waitedOn {
            try {
                queue.submit(Callable { work() }).get()
            } catch (failure: ExecutionException) {
                throw failure.cause ?: failure
            }
        }

    private fun next(): Pair<Int, Pending>? =
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
            val (slot, save) = next() ?: return
            try {
                if (save.alwaysWrite) {
                    slots.save(save.state, slot)
                } else if (!slots.saveIfChanged(save.state, slot)) {
                    // After any failure the slot forgets what it wrote, so nothing unreported can be skipped here.
                    continue
                }
                failures.remove(slot)
                onResult(slot, Result.success(Unit))
            } catch (failure: Exception) {
                failures[slot] = failure
                onResult(slot, Result.failure(failure))
            }
        }
    }
}
