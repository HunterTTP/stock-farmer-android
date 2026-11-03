package com.hunter.stockfarmer.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PolledStateTest {
    private class Owner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry

        fun move(event: Lifecycle.Event) = registry.handleLifecycleEvent(event)
    }

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `polling stops when the lifecycle stops and resumes when it starts again`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val owner = Owner()
            var reads = 0
            val seen = mutableListOf<Int>()
            backgroundScope.launch {
                pollWhileStarted(owner.lifecycle, 500, { ++reads }, seen::add)
            }
            runCurrent()
            assertEquals(0, reads)

            owner.move(Lifecycle.Event.ON_CREATE)
            owner.move(Lifecycle.Event.ON_START)
            runCurrent()
            assertEquals(1, reads)
            advanceTimeBy(1_200)
            assertEquals(3, reads)
            assertEquals(listOf(1, 2, 3), seen)

            owner.move(Lifecycle.Event.ON_STOP)
            runCurrent()
            advanceTimeBy(10_000)
            assertEquals(3, reads)

            owner.move(Lifecycle.Event.ON_START)
            runCurrent()
            assertEquals(4, reads)
            advanceTimeBy(500)
            runCurrent()
            assertEquals(5, reads)
        }
}
