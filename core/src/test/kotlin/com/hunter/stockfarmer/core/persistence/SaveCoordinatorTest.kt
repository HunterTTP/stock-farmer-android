package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Holds the first write of slot 1 until the test lets it go. */
private class SlowSaveFiles : SaveFileSystem {
    val entered = Semaphore(0)
    val release = Semaphore(0)
    private val writes = AtomicInteger()
    private val local = LocalSaveFileSystem()

    val gameWrites: Int get() = writes.get()

    override fun read(url: Path) = local.read(url)

    override fun createDirectory(url: Path) = local.createDirectory(url)

    override fun remove(url: Path) = local.remove(url)

    override fun write(
        data: ByteArray,
        to: Path,
    ) {
        if (to.fileName.toString() == "slot-1.json" && writes.incrementAndGet() == 1) {
            entered.release()
            if (!release.tryAcquire(10, TimeUnit.SECONDS)) throw IOException("never released")
        }
        local.write(data, to)
    }
}

class SaveCoordinatorTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `closing finishes pending writes and shuts down the worker`() {
        val files = SlowSaveFiles()
        val completed = CountDownLatch(1)
        val saver = SaveCoordinator(SaveSlots(directory, files)) { _, _ -> completed.countDown() }
        try {
            saver.saveSoon(stateWith(money = 42.0), 0)
            assertTrue(files.entered.tryAcquire(5, TimeUnit.SECONDS))
            saver.close()
            assertThrows(RejectedExecutionException::class.java) { saver.whenIdle { } }
            files.release.release()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertEquals(42.0, SaveSlots(directory).load(0)?.money)
        } finally {
            files.release.release()
            saver.close()
        }
    }

    @Test
    fun `a burst coalesces and a flush persists the latest state in each slot`() {
        val files = SlowSaveFiles()
        val slots = SaveSlots(directory, files)
        val saver = SaveCoordinator(slots)
        try {
            saver.saveSoon(stateWith(money = 1.0), 0)
            assertTrue(files.entered.tryAcquire(5, TimeUnit.SECONDS))
            for (value in 2..100) saver.saveSoon(stateWith(money = value.toDouble()), 0)
            saver.saveSoon(stateWith(money = 500.0), 1)
            files.release.release()
            saver.saveNow(stateWith(money = 101.0), 0)
            assertEquals(101.0, saver.whenIdle { it.load(0)?.money })
            assertEquals(500.0, slots.load(1)?.money)
            assertTrue(files.gameWrites <= 3)
        } finally {
            files.release.release()
        }
    }

    @Test
    fun `a flush reports an unsupported save without replacing it`() {
        val file = directory.resolve("slot-1.json")
        val original = """{"schemaVersion":999,"state":{}}""".toByteArray()
        Files.write(file, original)
        val saver = SaveCoordinator(SaveSlots(directory))
        assertThrows(NewerSaveVersionException::class.java) { saver.saveNow(GameState(), 0) }
        assertArrayEquals(original, Files.readAllBytes(file))
    }

    @Test
    fun `an autosave with nothing new writes nothing and reports nothing, and a flush still writes`() {
        val files = SlowSaveFiles().also { it.release.release() }
        val results = AtomicInteger()
        val saver = SaveCoordinator(SaveSlots(directory, files)) { _, _ -> results.incrementAndGet() }
        val farm = stateWith(money = 1.0)
        saver.saveIfChanged(farm, 0)
        saver.whenIdle { }
        assertEquals(1, files.gameWrites)
        assertEquals(1, results.get())

        repeat(5) { saver.saveIfChanged(farm.saveSnapshot().also { copy -> copy.playTime += it }, 0) }
        saver.whenIdle { }
        assertEquals(1, files.gameWrites)
        assertEquals(1, results.get())

        saver.saveNow(farm, 0)
        assertEquals(2, files.gameWrites)
        saver.close()
    }

    @Test
    fun `a flush that coalesces with an autosave of the same farm is still a flush`() {
        val files = SlowSaveFiles()
        val saver = SaveCoordinator(SaveSlots(directory, files))
        val farm = stateWith(money = 1.0)
        saver.saveIfChanged(farm, 0)
        assertTrue(files.entered.tryAcquire(5, TimeUnit.SECONDS))
        // While the first write is held, a flush queues and an autosave arrives on top of it.
        saver.saveSoon(farm, 0)
        saver.saveIfChanged(farm, 0)
        files.release.release()
        saver.whenIdle { }
        assertEquals(2, files.gameWrites)
        saver.close()
    }

    @Test
    fun `a failed autosave reports and is retried by the next though nothing has changed`() {
        val files = RefusingSaveFiles()
        val results = mutableListOf<Boolean>()
        val saver =
            SaveCoordinator(SaveSlots(directory, files)) { _, result ->
                synchronized(results) {
                    results +=
                        result.isSuccess
                }
            }
        val farm = stateWith(money = 1.0)
        saver.saveIfChanged(farm, 0)
        saver.whenIdle { }
        files.refusing = true
        farm.money = 2.0
        saver.saveIfChanged(farm.saveSnapshot(), 0)
        saver.whenIdle { }
        files.refusing = false
        saver.saveIfChanged(farm.saveSnapshot(), 0)
        saver.whenIdle { }
        assertEquals(listOf(true, false, true), synchronized(results) { results.toList() })
        assertEquals(2.0, SaveSlots(directory).load(0)?.money)
        saver.close()
    }

    @Test
    fun `the disk thread yields to the game unless someone is waiting on it`() {
        val files = PriorityRecordingFiles()
        val saver = SaveCoordinator(SaveSlots(directory, files))
        val blocked = saver.whenIdle { Thread.currentThread().priority }
        assertEquals(Thread.NORM_PRIORITY, blocked)

        saver.saveSoon(stateWith(money = 1.0), 0)
        assertTrue(files.written.tryAcquire(5, TimeUnit.SECONDS))
        assertEquals(Thread.MIN_PRIORITY, files.priority)

        saver.saveNow(stateWith(money = 2.0), 0)
        assertEquals(Thread.NORM_PRIORITY, files.priority, "a flush has someone waiting on it")
        assertEquals(Thread.NORM_PRIORITY, saver.whenIdle { Thread.currentThread().priority })
        saver.close()
    }
}

private class RefusingSaveFiles : LocalSaveFileSystem() {
    @Volatile var refusing = false

    override fun write(
        data: ByteArray,
        to: Path,
    ) {
        if (refusing) throw IOException("Disk full")
        super.write(data, to)
    }
}

private class PriorityRecordingFiles : LocalSaveFileSystem() {
    val written = Semaphore(0)

    @Volatile var priority = -1

    override fun write(
        data: ByteArray,
        to: Path,
    ) {
        super.write(data, to)
        if (to.fileName.toString() == "slot-1.json") {
            priority = Thread.currentThread().priority
            written.release()
        }
    }
}
