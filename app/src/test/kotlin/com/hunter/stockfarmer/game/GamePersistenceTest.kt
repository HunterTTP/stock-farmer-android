package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.audio.AmbienceDriver
import com.hunter.stockfarmer.audio.ambienceSnapshot
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.persistence.LocalSaveFileSystem
import com.hunter.stockfarmer.core.persistence.SaveFileSystem
import com.hunter.stockfarmer.core.persistence.SaveSlots
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.util.RandomSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class GamePersistenceTest {
    @TempDir
    lateinit var directory: Path

    private val scheduler = ManualSaveScheduler()
    private val files = TestSaveFiles()
    private val slots get() = SaveSlots(directory, files)
    private var status = PersistenceState()
    private lateinit var persistence: GamePersistence

    private fun start() {
        val random = Random(42)
        persistence =
            GamePersistence(slots, scheduler, { scheduler.now }, RandomSource(random::nextLong)) {
                status = it
            }
        scheduler.runCurrent()
    }

    @AfterEach
    fun close() {
        files.release.countDown()
        if (::persistence.isInitialized) {
            persistence.close()
            scheduler.runCurrent()
        }
    }

    @Test
    fun `switching to a ripe saved farm is silent and clears the old farm's celebrations and pulse`() {
        val tile = GridPoint(24, 24)
        val ripeFarm =
            GameState().apply {
                farmland = mutableSetOf(tile)
                plots[tile] = Plot("wheat", scheduler.now.minusSeconds(1_000))
            }
        slots.save(ripeFarm, 1, scheduler.now)
        start()
        val session = persistence.session
        val cues = mutableListOf<SoundCue>()
        val messages = mutableListOf<String>()
        val random = Random(42)
        val ambience =
            AmbienceDriver(
                snapshot = session::ambienceSnapshot,
                updateRain = { _, _ -> },
                play = { cue, _ ->
                    cues += cue
                    true
                },
                onReady = { messages += it },
                birdRandom = RandomSource(random::nextLong),
                animalRandom = RandomSource(random::nextLong),
            )
        ambience.tick(scheduler.now)
        session.celebrations.celebrate(BuildingCelebration(tile, 2, 2))
        session.publish(listOf(GameEvent.LeveledUp(2, emptyList(), 0, 1)))
        session.beginDay(123)

        ambience.clear()
        persistence.switchTo(1)
        scheduler.runCurrent()
        ambience.tick(scheduler.now)

        assertTrue(status.isReady)
        assertTrue(session.ambienceSnapshot(scheduler.now).isWholeFieldReady)
        assertTrue(messages.isEmpty())
        assertFalse(SoundCue.allCropsReady in cues)
        assertNull(session.nightPulse)
        var celebrations = 0
        session.celebrations.drain { celebrations++ }
        session.celebrations.drainAcross { celebrations++ }
        assertEquals(0, celebrations)

        session.act { it.state.plots[tile] = Plot("wheat", scheduler.now) }
        ambience.tick(scheduler.now)
        ambience.tick(scheduler.now.plusSeconds(31))
        assertEquals(listOf("All crops ready to harvest!"), messages)
        assertEquals(1, cues.count { it == SoundCue.allCropsReady })
    }

    @Test
    fun `empty slot generates a farm but does not save before play`() {
        start()
        assertTrue(status.isReady)
        assertTrue(persistence.session.read { it.trees.isNotEmpty() })
        assertNull(slots.load(0))
        persistence.resume()
        scheduler.runCurrent()
        scheduler.advance(249)
        assertNull(slots.load(0))
        persistence.pause()
        scheduler.runCurrent()
        assertNotNull(slots.load(0)?.timelapse?.historyID)
        assertEquals(scheduler.now, slots.load(0)?.lastSavedAt)
    }

    @Test
    fun `active slot reopens and startup delay and time away hold only the farm clock`() {
        val plantedAt = scheduler.now.minusSeconds(1_000)
        val point = GameState.startingFarmland.first()
        val original =
            GameState().apply {
                money = 1234.0
                lastSavedAt = scheduler.now.minusSeconds(600)
                clockOffset = 120.0
                plots[point] = Plot("wheat", plantedAt)
            }
        slots.save(original, 3, scheduler.now)
        slots.setActive(3)
        val bytes = Files.readAllBytes(directory.resolve("slot-4.json"))
        start()
        assertEquals(1234.0, persistence.session.read { it.money })
        assertEquals(-480.0, persistence.session.read { it.clockOffset })
        assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("slot-4.json")))
        scheduler.advance(5_000)
        persistence.resume()
        scheduler.runCurrent()
        assertEquals(-485.0, persistence.session.read { it.clockOffset })
        persistence.pause()
        scheduler.runCurrent()
        scheduler.advance(10_000)
        persistence.pause() // onStop follows onPause; its snapshot must still represent a stopped clock.
        scheduler.runCurrent()
        assertEquals(-495.0, slots.load(3)?.clockOffset)
        assertEquals(-485.0, persistence.session.read { it.clockOffset })
        scheduler.advance(20_000)
        persistence.resume()
        scheduler.runCurrent()
        assertEquals(-515.0, persistence.session.read { it.clockOffset })
        assertEquals(plantedAt, persistence.session.read { it.plots[point]?.plantedAt })
        assertNull(slots.load(0))
    }

    @ParameterizedTest
    @ValueSource(strings = ["damaged", """{"schemaVersion":999,"state":{}}"""])
    fun `unreadable saves remain blocked and untouched through lifecycle and retry`(contents: String) {
        val file = directory.resolve("slot-1.json")
        Files.writeString(file, contents)
        start()
        assertFalse(status.isReady)
        assertNotNull(status.error)
        persistence.resume()
        scheduler.runCurrent()
        scheduler.advance(5_000)
        persistence.pause()
        persistence.retry()
        scheduler.runCurrent()
        assertFalse(status.isReady)
        assertEquals(contents, Files.readString(file))
        assertEquals(0, files.gameWrites)
    }

    @Test
    fun `backup recovery is explicit and preserves the damaged original`() {
        slots.save(GameState().apply { money = 100.0 }, 0, scheduler.now)
        slots.save(GameState().apply { money = 200.0 }, 0, scheduler.now)
        val file = directory.resolve("slot-1.json")
        Files.writeString(file, "damaged")
        start()
        assertFalse(status.isReady)
        assertEquals("damaged", Files.readString(file))
        val backup = status.backups.first()
        assertEquals(100.0, backup.money)
        persistence.restore(backup)
        scheduler.runCurrent()
        assertTrue(status.isReady)
        assertEquals(100.0, persistence.session.read { it.money })
        Files.list(directory.resolve("Recovery")).use { recovered ->
            assertTrue(recovered.anyMatch { Files.readString(it) == "damaged" })
        }
    }

    @Test
    fun `timer saves an isolated snapshot and pause flushes the latest changes`() {
        slots.save(GameState(), 0, scheduler.now)
        start()
        persistence.resume()
        scheduler.runCurrent()
        files.saved.drainPermits()
        scheduler.advance(250)
        assertTrue(files.saved.tryAcquire(5, TimeUnit.SECONDS))
        // Block disk access before the snapshot is serialized, then mutate the live maps.
        files.blockReads = true
        persistence.session.act {
            it.state.money = 42.0
            it.state.storage["wheat"] = 7
        }
        scheduler.advance(1_000)
        assertTrue(files.entered.await(5, TimeUnit.SECONDS))
        persistence.session.act {
            it.state.money = 99.0
            it.state.storage["wheat"] = 9
        }
        files.release.countDown()
        assertTrue(files.written.await(5, TimeUnit.SECONDS))
        val snapshot = slots.load(0)
        assertEquals(42.0, snapshot?.money)
        assertEquals(7, snapshot?.storage?.get("wheat"))
        persistence.pause()
        scheduler.runCurrent()
        assertEquals(99.0, slots.load(0)?.money)
        assertEquals(9, slots.load(0)?.storage?.get("wheat"))
        val writes = files.gameWrites
        scheduler.advance(10_000)
        assertEquals(writes, files.gameWrites)
    }

    @Test
    fun `a new farm saves the one being left and then becomes the active farm`() {
        start()
        persistence.resume()
        scheduler.runCurrent()
        persistence.session.act { it.state.money = 777.0 }
        persistence.startNew(2, "Second")
        scheduler.runCurrent()
        assertEquals(777.0, slots.load(0)?.money)
        assertEquals(2, slots.activeSlot)
        assertEquals("Second", slots.slots()[2]?.name)
        assertTrue(status.isReady)
        assertTrue(persistence.session.read { it.money != 777.0 })
    }

    @Test
    fun `switching loads the other farm and the farm in play cannot be deleted`() {
        start()
        persistence.resume()
        scheduler.runCurrent()
        persistence.session.act { it.state.money = 321.0 }
        persistence.startNew(1, "Other")
        scheduler.runCurrent()
        persistence.switchTo(0)
        scheduler.runCurrent()
        assertEquals(321.0, persistence.session.read { it.money })
        var deleted = 0
        persistence.delete(0) { deleted++ }
        persistence.delete(1) { deleted++ }
        scheduler.runCurrent()
        assertEquals(2, deleted)
        assertNotNull(slots.slots()[0])
        assertNull(slots.slots()[1])
    }

    @Test
    fun `pause cancels a pending debounce and write failure can be retried`() {
        start()
        persistence.resume()
        scheduler.runCurrent()
        files.failWrites = true
        persistence.pause()
        scheduler.runCurrent()
        assertNotNull(status.error)
        assertTrue(status.isReady)
        files.failWrites = false
        persistence.session.act { it.state.money = 555.0 }
        persistence.retry()
        scheduler.runCurrent()
        assertNull(status.error)
        assertEquals(555.0, slots.load(0)?.money)
        val writes = files.gameWrites
        scheduler.advance(5_000)
        assertEquals(writes, files.gameWrites)
    }

    @Test
    fun `switching and starting new farm is permitted when current farm failed to open`() {
        val file = directory.resolve("slot-1.json")
        Files.writeString(file, "corrupt")
        slots.save(GameState().apply { money = 888.0 }, 1, scheduler.now)
        start()
        assertFalse(status.isReady)
        assertNotNull(status.error)

        persistence.switchTo(1)
        scheduler.runCurrent()
        assertTrue(status.isReady)
        assertNull(status.error)
        assertEquals(888.0, persistence.session.read { it.money })
        assertEquals(1, slots.activeSlot)
    }

    @Test
    fun `starting a new farm is permitted when active farm failed to open`() {
        val file = directory.resolve("slot-1.json")
        Files.writeString(file, "corrupt")
        start()
        assertFalse(status.isReady)

        persistence.startNew(2, "Brand New")
        scheduler.runCurrent()
        assertTrue(status.isReady)
        assertNull(status.error)
        assertEquals(2, slots.activeSlot)
        assertEquals("Brand New", slots.slots()[2]?.name)
    }

    @Test
    fun `restoring active slot invokes completion callback`() {
        slots.save(GameState().apply { money = 100.0 }, 0, scheduler.now)
        slots.save(GameState().apply { money = 200.0 }, 0, scheduler.now)
        start()
        val backup = slots.backups(0).first()
        var completed = false
        persistence.restoreSaved(backup) { completed = true }
        scheduler.runCurrent()
        assertTrue(completed)
        assertEquals(100.0, persistence.session.read { it.money })
    }

    private class TestSaveFiles : SaveFileSystem by LocalSaveFileSystem() {
        private val local = LocalSaveFileSystem()
        var failWrites = false

        @Volatile var blockReads = false

        @Volatile var gameWrites = 0
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val written = CountDownLatch(1)
        val saved = Semaphore(0)

        override fun read(url: Path): ByteArray? {
            if (blockReads && url.fileName.toString() == "slot-1.json") {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            return local.read(url)
        }

        override fun write(
            data: ByteArray,
            to: Path,
        ) {
            if (failWrites) throw IOException("Disk full")
            local.write(data, to)
            if (to.fileName.toString() == "slot-1.json") gameWrites++
            if (blockReads && to.fileName.toString() == "slots.json") written.countDown()
            if (to.fileName.toString() == "slots.json") saved.release()
        }
    }
}

private class ManualSaveScheduler : SaveScheduler {
    private data class Task(
        val at: Long,
        val work: () -> Unit,
        var cancelled: Boolean = false,
    )

    private val tasks = mutableListOf<Task>()
    private var elapsed = 0L
    val now: Instant get() = Instant.ofEpochMilli(1_800_000_000_000 + elapsed)

    @Synchronized
    override fun execute(work: () -> Unit) {
        tasks += Task(elapsed, work)
    }

    @Synchronized
    override fun after(
        milliseconds: Long,
        work: () -> Unit,
    ): () -> Unit {
        val task = Task(elapsed + milliseconds, work)
        tasks += task
        return { task.cancelled = true }
    }

    fun runCurrent() = advance(0)

    fun advance(milliseconds: Long) {
        val target = elapsed + milliseconds
        while (true) {
            val task =
                synchronized(this) {
                    tasks.filter { !it.cancelled && it.at <= target }.minByOrNull { it.at }?.also { tasks.remove(it) }
                } ?: break
            elapsed = task.at
            task.work()
        }
        elapsed = target
    }

    override fun close() {
        synchronized(this) { tasks.clear() }
    }
}
