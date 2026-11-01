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
}
