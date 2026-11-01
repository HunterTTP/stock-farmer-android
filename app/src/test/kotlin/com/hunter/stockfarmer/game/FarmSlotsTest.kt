package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.persistence.SaveBackup
import com.hunter.stockfarmer.core.persistence.SaveSlots
import com.hunter.stockfarmer.core.util.RandomSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.Random

class FarmSlotsTest {
    @TempDir
    lateinit var directory: Path

    private val scheduler = SlotsTestScheduler()
    private val slots get() = SaveSlots(directory)
    private lateinit var persistence: GamePersistence
    private lateinit var farmSlots: FarmSlots

    @BeforeEach
    fun setUp() {
        persistence =
            GamePersistence(
                slots = slots,
                scheduler = scheduler,
                now = { scheduler.now },
                random = RandomSource(Random(42)::nextLong),
            )
        scheduler.runCurrent()
        farmSlots =
            FarmSlots(persistence) { runnable ->
                runnable.run()
                true
            }
    }

    @AfterEach
    fun tearDown() {
        persistence.close()
        scheduler.runCurrent()
    }

    @Test
    fun `load delivers saved games on callback`() {
        var result: SavedGames? = null
        farmSlots.load { result = it }
        scheduler.runCurrent()
        assertNotNull(result)
        assertEquals(0, result?.active)
        assertEquals(SaveSlots.COUNT, result?.slots?.size)
    }

    @Test
    fun `backups delivers backup list for slot`() {
        slots.save(GameState().apply { money = 100.0 }, 1, scheduler.now)
        slots.save(GameState().apply { money = 200.0 }, 1, scheduler.now)
        var backups: List<SaveBackup>? = null
        farmSlots.backups(1) { backups = it }
        scheduler.runCurrent()
        assertNotNull(backups)
        assertTrue(backups!!.isNotEmpty())
    }

    @Test
    fun `switchTo changes active slot`() {
        slots.save(GameState().apply { money = 500.0 }, 2, scheduler.now)
        farmSlots.switchTo(2)
        scheduler.runCurrent()
        assertEquals(2, slots.activeSlot)
        assertEquals(500.0, persistence.session.read { it.money })
    }

    @Test
    fun `startNew creates a fresh game and switches active slot`() {
        farmSlots.startNew(3, "My Farm")
        scheduler.runCurrent()
        assertEquals(3, slots.activeSlot)
        assertEquals("My Farm", slots.slots()[3]?.name)
    }

    @Test
    fun `rename changes farm name and invokes callback`() {
        slots.save(GameState(), 0, scheduler.now)
        var called = false
        farmSlots.rename(0, "Renamed Farm") { called = true }
        scheduler.runCurrent()
        assertTrue(called)
        assertEquals("Renamed Farm", slots.slots()[0]?.name)
    }

    @Test
    fun `delete removes farm and invokes callback`() {
        slots.save(GameState(), 1, scheduler.now)
        var called = false
        farmSlots.delete(1) { called = true }
        scheduler.runCurrent()
        assertTrue(called)
        assertNull(slots.slots()[1])
    }

    @Test
    fun `restore restores backup and invokes callback`() {
        slots.save(GameState().apply { money = 100.0 }, 1, scheduler.now)
        slots.save(GameState().apply { money = 200.0 }, 1, scheduler.now)
        val backup = slots.backups(1).first()
        var called = false
        farmSlots.restore(backup) { called = true }
        scheduler.runCurrent()
        assertTrue(called)
        assertEquals(100.0, slots.load(1)?.money)
    }
}

private class SlotsTestScheduler : SaveScheduler {
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
