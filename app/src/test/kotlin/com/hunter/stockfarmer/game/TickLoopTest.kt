package com.hunter.stockfarmer.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class TickLoopTest {
    private fun TestScope.loop(
        log: MutableList<String>,
        interval: Long = 1_000L,
    ) = TickLoop(
        backgroundScope,
        StandardTestDispatcher(testScheduler),
        interval,
        onStart = { log += "start" },
        step = { log += "step" },
    )

    @Test
    fun `nothing runs until started, then the clocks are forgotten before every step`() =
        runTest {
            val log = mutableListOf<String>()
            val loop = loop(log)
            runCurrent()
            assertTrue(log.isEmpty())

            loop.start()
            runCurrent()
            assertEquals(listOf("start", "step"), log)
            advanceTimeBy(2_500)
            assertEquals(listOf("start", "step", "step", "step"), log)
        }

    @Test
    fun `stopping cancels the loop and starting again restarts it with forgotten clocks`() =
        runTest {
            val log = mutableListOf<String>()
            val loop = loop(log)
            loop.start()
            runCurrent()
            loop.stop()
            advanceTimeBy(5_000)
            assertEquals(listOf("start", "step"), log)

            loop.start()
            runCurrent()
            assertEquals(listOf("start", "step", "start", "step"), log)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(listOf("start", "step", "start", "step", "step"), log)
        }

    @Test
    fun `starting twice runs one loop`() =
        runTest {
            val log = mutableListOf<String>()
            val loop = loop(log)
            loop.start()
            loop.start()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(listOf("start", "step", "step"), log)
        }

    @Test
    fun `stopped before the first step, nothing ever runs`() =
        runTest {
            val log = mutableListOf<String>()
            val loop = loop(log)
            loop.start()
            loop.stop()
            advanceTimeBy(3_000)
            assertTrue(log.isEmpty())
        }

    @Test
    fun `a restart waits for the step still running so two never overlap`() {
        val scope = CoroutineScope(SupervisorJob())
        val running = AtomicInteger()
        val mostAtOnce = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val steps = AtomicInteger()
        val second = CountDownLatch(1)
        val loop =
            TickLoop(scope, Dispatchers.Default, 1L, onStart = {}) {
                mostAtOnce.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                if (steps.incrementAndGet() == 1) {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                } else {
                    second.countDown()
                }
                running.decrementAndGet()
            }
        try {
            loop.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            loop.stop()
            loop.start()
            // The first step is still held: the restarted loop must not have begun one.
            Thread.sleep(100)
            assertEquals(1, steps.get())
            release.countDown()
            assertTrue(second.await(5, TimeUnit.SECONDS))
            loop.stop()
            assertEquals(1, mostAtOnce.get())
        } finally {
            scope.cancel()
        }
    }
}
