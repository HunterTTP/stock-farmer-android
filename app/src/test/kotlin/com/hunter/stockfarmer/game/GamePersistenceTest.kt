package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.audio.AmbienceDriver
import com.hunter.stockfarmer.audio.ambienceSnapshot
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.feedback.SoundCue
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.persistence.LocalSaveFileSystem
import com.hunter.stockfarmer.core.persistence.SaveFileSystem
import com.hunter.stockfarmer.core.persistence.SaveSlots
import com.hunter.stockfarmer.core.persistence.TimelapseStore
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.beginMovingFruitTree
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.farm.animals.AnimalGesture
import com.hunter.stockfarmer.farm.animals.AnimalHitTarget
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
import java.util.UUID
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

    private fun start(timelapseStore: TimelapseStore? = null) {
        val random = Random(42)
        persistence =
            GamePersistence(slots, scheduler, { scheduler.now }, RandomSource(random::nextLong), timelapseStore) {
                status = it
            }
        scheduler.runCurrent()
    }

    /** Lets the disk queue finish, then hands over what it reported. */
    private fun settle() {
        persistence.slots { }
        scheduler.runCurrent()
    }

    /** A farm in play that has had its first autosave, so that only real changes are left to write. */
    private fun startPlaying(farm: GameState = GameState().apply { money = 100.0 }) {
        slots.save(farm, 0, scheduler.now)
        start()
        persistence.resume()
        scheduler.runCurrent()
        scheduler.advance(GamePersistence.SAVE_DEBOUNCE_MS)
        settle()
    }

    private val activeFile get() = directory.resolve("slot-1.json")

    private fun ticksPassing(
        tick: GameTick,
        seconds: Int,
    ) = repeat(seconds) {
        tick.tick(scheduler.now)
        scheduler.advance(1_000)
        settle()
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

    @Test
    fun `a quiet farm writes nothing while the clock and the time played run on`() {
        startPlaying()
        val session = persistence.session
        val tick = GameTick(session, RandomSource(Random(7)::nextLong))
        ticksPassing(tick, 3)
        val writes = files.allWrites
        val saved = Files.readAllBytes(activeFile)
        val played = session.read { it.playTime }

        ticksPassing(tick, 60)

        assertEquals(played + 60.0, session.read { it.playTime })
        assertEquals(writes, files.allWrites)
        assertArrayEquals(saved, Files.readAllBytes(activeFile))
        assertNull(status.error)
    }

    @ParameterizedTest
    @ValueSource(longs = [1, 249, 250, 251, 999, 1_000])
    fun `a real change reaches the disk within a second at any point between autosaves`(wait: Long) {
        startPlaying()
        scheduler.advance(wait)
        settle()
        persistence.session.act { it.state.money = 4_321.0 }

        scheduler.advance(1_000)
        settle()

        assertEquals(4_321.0, slots.load(0)?.money)
    }

    @Test
    fun `the time played goes along with the next real change`() {
        startPlaying()
        val session = persistence.session
        ticksPassing(GameTick(session, RandomSource(Random(7)::nextLong)), 30)

        session.act { it.state.money = 4_321.0 }
        scheduler.advance(1_000)
        settle()

        val saved = slots.load(0)!!
        assertEquals(4_321.0, saved.money)
        assertEquals(session.read { it.playTime }, saved.playTime)
    }

    @Test
    fun `stopping writes a change that no autosave has reached yet`() {
        startPlaying()
        persistence.session.act { it.state.money = 4_321.0 }
        persistence.pause()
        scheduler.runCurrent()
        assertEquals(4_321.0, slots.load(0)?.money)
    }

    @Test
    fun `stopping a quiet farm still saves the time played and when it stopped`() {
        startPlaying()
        val session = persistence.session
        ticksPassing(GameTick(session, RandomSource(Random(7)::nextLong)), 10)
        val writes = files.gameWrites

        persistence.pause()
        scheduler.runCurrent()

        assertEquals(writes + 1, files.gameWrites)
        val saved = slots.load(0)!!
        assertEquals(session.read { it.playTime }, saved.playTime)
        assertEquals(scheduler.now, saved.lastSavedAt)
    }

    @Test
    fun `closing flushes a farm that was quiet`() {
        startPlaying()
        persistence.session.act { it.state.playTime = 500.0 }
        persistence.close()
        scheduler.runCurrent()
        assertEquals(500.0, slots.load(0)?.playTime)
    }

    @Test
    fun `coming back writes the held clock once and then the farm is quiet again`() {
        startPlaying()
        persistence.pause()
        scheduler.runCurrent()
        scheduler.advance(20_000)
        val writes = files.gameWrites

        persistence.resume()
        scheduler.runCurrent()
        scheduler.advance(GamePersistence.SAVE_DEBOUNCE_MS)
        settle()
        assertEquals(writes + 1, files.gameWrites)
        assertEquals(persistence.session.read { it.clockOffset }, slots.load(0)?.clockOffset)

        scheduler.advance(10_000)
        settle()
        assertEquals(writes + 1, files.gameWrites)
    }

    @Test
    fun `a save damaged between autosaves is detected at the next write and kept`() {
        startPlaying()
        Files.writeString(activeFile, "damaged")

        // Nothing is being written, so nothing is at risk yet.
        scheduler.advance(3_000)
        settle()
        assertNull(status.error)

        persistence.session.act { it.state.money = 4_321.0 }
        scheduler.advance(1_000)
        settle()

        assertTrue(status.isSaveFailing)
        assertTrue(persistence.session.isSaveBlocked)
        assertEquals("damaged", Files.readString(activeFile))

        persistence.pause()
        scheduler.runCurrent()
        assertEquals("damaged", Files.readString(activeFile))
    }

    @Test
    fun `a farm that is not saved by a failing autosave keeps trying and recovers without a change`() {
        startPlaying()
        files.failWrites = true
        persistence.session.act { it.state.money = 4_321.0 }
        scheduler.advance(1_000)
        settle()
        assertTrue(status.isSaveFailing)

        files.failWrites = false
        scheduler.advance(1_000)
        settle()

        assertFalse(status.isSaveFailing)
        assertNull(status.error)
        assertEquals(4_321.0, slots.load(0)?.money)
    }

    @Test
    fun `a queued save result does not erase why the next farm could not be opened`() {
        slots.save(GameState(), 0, scheduler.now)
        Files.writeString(directory.resolve("slot-2.json"), """{"schemaVersion":999,"state":{}}""")
        start()
        persistence.resume()
        scheduler.runCurrent()

        persistence.switchTo(1)
        scheduler.runCurrent()
        settle()

        assertFalse(status.isReady)
        assertEquals("This farm needs a newer version of Stock Farmer. Its save has been kept safe.", status.error)
    }

    @Test
    fun `a result still queued from an earlier failure does not stop a farm switch that saved first`() {
        startPlaying()
        slots.save(GameState().apply { money = 888.0 }, 1, scheduler.now)
        files.failWrites = true
        persistence.pause()
        scheduler.runCurrent()
        settle()
        assertTrue(status.isSaveFailing)

        files.failWrites = false
        // Both run before the retry's good news is delivered, as it queues behind them.
        persistence.retry()
        persistence.switchTo(1)
        scheduler.runCurrent()
        settle()

        assertEquals(1, slots.activeSlot)
        assertEquals(888.0, persistence.session.read { it.money })
        assertNull(status.error)
    }

    @Test
    fun `a farm switch does not leave a farm whose last changes could not be saved`() {
        startPlaying()
        slots.save(GameState().apply { money = 888.0 }, 1, scheduler.now)
        val generation = persistence.session.read { persistence.session.farmGeneration }
        persistence.session.act { it.state.money = 4_321.0 }
        files.failWrites = true

        persistence.switchTo(1)
        scheduler.runCurrent()
        settle()

        assertTrue(status.isSaveFailing)
        assertEquals(4_321.0, persistence.session.read { it.money })
        assertEquals(generation, persistence.session.read { persistence.session.farmGeneration })
        files.failWrites = false
        assertEquals(0, slots.activeSlot)
    }

    @Test
    fun `a failed change to the saved farms stays up through autosaves without stopping the farm`() {
        startPlaying()
        files.manifestWritesLeft = 0
        persistence.rename(0, "Home") {}
        scheduler.runCurrent()
        assertEquals("The farm could not be renamed. Please retry.", status.error)
        assertFalse(persistence.session.isSaveBlocked, "the tractor and wild growth carry on")
        assertFalse(status.isSaveBlocked)

        files.manifestWritesLeft = Int.MAX_VALUE
        persistence.session.act { it.state.money = 4_321.0 }
        scheduler.advance(2_000)
        settle()
        assertEquals(4_321.0, slots.load(0)?.money, "saving went on")
        assertEquals("The farm could not be renamed. Please retry.", status.error)

        persistence.retry()
        settle()
        assertNull(status.error)
    }

    @Test
    fun `the next change to the saved farms clears a failure of the last one`() {
        startPlaying()
        files.manifestWritesLeft = 0
        persistence.rename(0, "Home") {}
        scheduler.runCurrent()
        assertNotNull(status.error)

        files.manifestWritesLeft = Int.MAX_VALUE
        persistence.rename(0, "Home") {}
        scheduler.runCurrent()
        assertNull(status.error)
        assertEquals("Home", slots.slots()[0]?.name)
    }

    @Test
    fun `each change to the saved farms says what failed, as on iOS`() {
        startPlaying()
        slots.save(GameState().apply { money = 888.0 }, 1, scheduler.now)
        slots.save(GameState().apply { money = 889.0 }, 1, scheduler.now)
        val backup = slots.backups(1).first()

        files.manifestWritesLeft = 0
        persistence.delete(1) {}
        scheduler.runCurrent()
        assertEquals("The farm could not be deleted. Please retry.", status.error)

        files.manifestWritesLeft = 0
        persistence.restoreSaved(backup) {}
        scheduler.runCurrent()
        assertEquals("The backup could not be restored. Please retry.", status.error)

        // These two save the farm being left first, which is allowed through.
        files.manifestWritesLeft = 1
        persistence.startNew(2, "New")
        scheduler.runCurrent()
        assertEquals("The new farm could not be saved. Please retry.", status.error)

        files.manifestWritesLeft = 1
        persistence.switchTo(1)
        scheduler.runCurrent()
        assertEquals(
            "This farm could not be opened. Its save has been kept safe. Retry, or choose another farm.",
            status.error,
        )
        assertTrue(status.isReady, "the farm being played stays in play")
    }

    @Test
    fun `saving clears only its own failure and a failing save is what the player hears about first`() {
        startPlaying()
        files.manifestWritesLeft = 0
        persistence.rename(0, "Home") {}
        scheduler.runCurrent()
        val renameFailure = status.error

        files.manifestWritesLeft = Int.MAX_VALUE
        files.failWrites = true
        persistence.session.act { it.state.money = 4_321.0 }
        scheduler.advance(1_000)
        settle()
        assertEquals("Your latest changes could not be saved. Please retry before leaving this farm.", status.error)
        assertTrue(persistence.session.isSaveBlocked)

        files.failWrites = false
        scheduler.advance(1_000)
        settle()
        assertEquals(renameFailure, status.error)
        assertFalse(persistence.session.isSaveBlocked)
    }

    @Test
    fun `a failed load keeps its message through a failed change made from the failure screen`() {
        Files.writeString(activeFile, "corrupt")
        start()
        val loadFailure = status.error
        assertNotNull(loadFailure)
        files.manifestWritesLeft = 0
        persistence.switchTo(1)
        scheduler.runCurrent()
        assertEquals(
            "This farm could not be opened. Its save has been kept safe. Retry, or choose another farm.",
            status.error,
        )
        assertTrue(persistence.session.isSaveBlocked)

        files.manifestWritesLeft = Int.MAX_VALUE
        persistence.rename(0, "Home") {}
        scheduler.runCurrent()
        assertEquals(loadFailure, status.error)
    }

    @Test
    fun `opening another farm drops what was under way on the last one, however quickly it loads`() {
        val house = GridPoint(10, 10)
        val tree = GridPoint(24, 24)

        fun farm(money: Double) =
            GameState().apply {
                this.money = money
                pets[house] = Pet(breed = Cat.starter.id, name = "Cat", position = GridPoint(20, 20))
                plots[tree] = Plot("apple", scheduler.now)
            }
        slots.save(farm(1.0), 0, scheduler.now)
        slots.save(farm(2.0), 1, scheduler.now)
        start()
        persistence.resume()
        scheduler.runCurrent()
        val session = persistence.session
        val gestures = session.animalGestures
        val pet = AnimalRef.Pet(house)
        gestures.targets = listOf(AnimalHitTarget(pet, 100f, 100f))
        assertTrue(gestures.interact(pet, AnimalGesture.NUDGE, scheduler.now))
        session.act { gestures.advance(it, scheduler.now) }
        assertTrue(gestures.isNudging(pet))
        assertTrue(gestures.interact(pet, AnimalGesture.PET, scheduler.now))
        session.act {
            it.beginMovingFruitTree(tree)
            assertNotNull(it.fruitPlacement)
        }
        val generation = session.read { session.farmGeneration }

        persistence.switchTo(1)
        scheduler.runCurrent()

        assertEquals(2.0, session.read { it.money })
        assertEquals(generation + 1, session.read { session.farmGeneration })
        assertFalse(gestures.isNudging(pet))
        assertTrue(gestures.targets.isEmpty())
        session.act { gestures.advance(it, scheduler.now.plusSeconds(5)) }
        assertEquals(GridPoint(20, 20), session.read { it.pets[house]?.position }, "a pet at the same house stays put")
        assertTrue(gestures.drainPetting().isEmpty())
        session.act { assertNull(it.fruitPlacement) }
    }

    @Test
    fun `replacing a farm removes its timelapse from the store it was given`() {
        val store = TimelapseStore(directory.resolve("Timelapse"))
        val history = UUID(0, 1)
        slots.save(GameState().apply { timelapse = timelapse.copy(historyID = history) }, 1, scheduler.now)
        val archive = store.archive(history)
        store.append(byteArrayOf(1, 2, 3), history, archive.generation, 1, 540, scheduler.now)
        assertEquals(1, store.archive(history).frames.size)
        start(store)

        persistence.startNew(1, "New")
        scheduler.runCurrent()

        assertTrue(store.archive(history).frames.isEmpty())
    }

    private class TestSaveFiles : SaveFileSystem by LocalSaveFileSystem() {
        private val local = LocalSaveFileSystem()

        @Volatile var failWrites = false

        /** Lets this many more manifests be written, then refuses them. */
        @Volatile var manifestWritesLeft = Int.MAX_VALUE

        @Volatile var blockReads = false

        @Volatile var allWrites = 0

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
            if (to.fileName.toString() == "slots.json") {
                if (manifestWritesLeft <= 0) throw IOException("Disk full")
                if (manifestWritesLeft != Int.MAX_VALUE) manifestWritesLeft--
            }
            local.write(data, to)
            allWrites++
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
