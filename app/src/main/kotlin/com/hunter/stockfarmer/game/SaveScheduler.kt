package com.hunter.stockfarmer.game

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Serial app persistence work; the clock and scheduling can be controlled by JVM tests. */
interface SaveScheduler : AutoCloseable {
    fun execute(work: () -> Unit)

    fun after(
        milliseconds: Long,
        work: () -> Unit,
    ): () -> Unit
}

class BackgroundSaveScheduler : SaveScheduler {
    private val executor =
        ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "StockFarmer.persistence").apply { isDaemon = true }
        }.apply {
            removeOnCancelPolicy = true
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
        }

    override fun execute(work: () -> Unit) = executor.execute(work)

    override fun after(
        milliseconds: Long,
        work: () -> Unit,
    ): () -> Unit {
        val future = executor.schedule(work, milliseconds, TimeUnit.MILLISECONDS)
        return {
            future.cancel(false)
            Unit
        }
    }

    override fun close() = executor.shutdown()
}
