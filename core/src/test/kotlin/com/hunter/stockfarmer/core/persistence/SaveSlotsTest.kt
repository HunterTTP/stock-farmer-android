package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.addPlayTime
import com.hunter.stockfarmer.core.rules.markSaved
import com.hunter.stockfarmer.core.rules.newGame
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Random

class SaveSlotsTest {
    @TempDir
    lateinit var directory: Path

    private val now = Instant.ofEpochSecond(40_000_000)

    private val slots get() = SaveSlots(directory)

    @Test
    fun `a fresh folder has empty slots and the first is active`() {
        assertEquals(SaveSlots.COUNT, slots.slots().size)
        assertTrue(slots.slots().all { it == null })
        assertEquals(0, slots.activeSlot)
        assertNull(slots.load(0))
    }

    @Test
    fun `ten games can be kept and no more`() {
        assertEquals(10, SaveSlots.COUNT)
        for (slot in 0 until SaveSlots.COUNT) slots.save(stateWith(money = slot.toDouble()), slot, now)
        slots.save(stateWith(money = 99.0), SaveSlots.COUNT, now)
        assertEquals(10, slots.slots().size)
        assertTrue(slots.slots().all { it != null })
        assertNull(slots.load(SaveSlots.COUNT))
    }

    @Test
    fun `saving fills a slot with the default name and a summary`() {
        slots.save(stateWith(money = 250.0, xp = LevelRules.xpToReach(4)), 2, now)
        assertEquals(SaveSlotInfo(2, "Farm 3", now, 4, 250.0), slots.slots()[2])
        assertEquals(250.0, slots.load(2)?.money)
        assertNull(slots.slots()[0])
    }

    @Test
    fun `a slot shows the play time of its game and old saves start at zero`() {
        val engine = GameEngine(stateWith(money = 5.0))
        engine.addPlayTime(90.0)
        engine.addPlayTime(-30.0)
        slots.save(engine.state, 0, now)
        assertEquals(90.0, slots.slots()[0]?.playTime)
        assertEquals(90.0, GameStateJson.decode(GameStateJson.encode(engine.state)).playTime)
        assertEquals(0.0, GameStateJson.decode("""{"money": 1}""").playTime)
    }

    @Test
    fun `creating starts a new game under the given name`() {
        slots.save(stateWith(money = 999.0), 1, now)
        slots.create(1, "  Sunny Acres  ", now = now.advancedBy(60.0))
        val info = slots.slots()[1]!!
        assertEquals("Sunny Acres", info.name)
        assertEquals(GameState().money, info.money)
        assertEquals(now.advancedBy(60.0), info.lastPlayed)
    }

    @Test
    fun `blank names fall back to the default and long ones are cut`() {
        slots.create(0, "   ", now = now)
        assertEquals("Farm 1", slots.slots()[0]?.name)
        slots.rename(0, "x".repeat(60))
        assertEquals(SaveSlots.MAX_NAME_LENGTH, slots.slots()[0]?.name?.length)
    }

    @Test
    fun `renaming keeps the game and only touches filled slots`() {
        slots.save(stateWith(money = 5.0), 0, now)
        slots.rename(0, "Home")
        slots.rename(3, "Nope")
        assertEquals("Home", slots.slots()[0]?.name)
        assertEquals(5.0, slots.load(0)?.money)
        assertNull(slots.slots()[3])
    }

    @Test
    fun `saving again keeps the name but updates the time`() {
        slots.create(0, "Home", now = now)
        slots.save(stateWith(money = 8.0), 0, now.advancedBy(500.0))
        val info = slots.slots()[0]!!
        assertEquals("Home", info.name)
        assertEquals(now.advancedBy(500.0), info.lastPlayed)
    }

    @Test
    fun `deleting empties the slot`() {
        slots.save(stateWith(money = 5.0), 1, now)
        slots.delete(1)
        assertNull(slots.slots()[1])
        assertNull(slots.load(1))
    }

    @Test
    fun `the active slot is remembered`() {
        slots.setActive(3)
        assertEquals(3, slots.activeSlot)
        slots.setActive(SaveSlots.COUNT)
        assertEquals(3, slots.activeSlot)
    }

    @Test
    fun `slots outside the ten are ignored`() {
        slots.save(stateWith(money = 5.0), SaveSlots.COUNT, now)
        slots.create(-1, "x", now = now)
        assertTrue(slots.slots().all { it == null })
    }

    @Test
    fun `the load menu summaries come from the manifest without reading save files`() {
        slots.save(stateWith(money = 77.0, xp = LevelRules.xpToReach(3)), 0, now)
        Files.writeString(directory.resolve("slot-1.json"), "not json")
        assertEquals(SaveSlotInfo(0, "Farm 1", now, 3, 77.0), slots.slots()[0])
    }

    /** The bytes these are compared with were written by the code as it stood before saves stopped re-reading themselves. */
    private fun golden(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/saves/$name")) { "missing $name" }.use { it.readBytes() }

    private val goldenAt = Instant.ofEpochSecond(1_800_000_000)

    private fun seededFarm(): GameState {
        val random = Random(20_261_102)
        val engine = GameEngine(newGame(RandomSource(random::nextLong), goldenAt))
        engine.state.money = 1234.5
        engine.state.storage["wheat"] = 7
        engine.state.plots[GameState.startingFarmland.first()] = Plot("wheat", goldenAt)
        engine.addPlayTime(90.0)
        engine.markSaved(goldenAt)
        return engine.state
    }

    private fun assertWroteGolden() {
        assertArrayEquals(golden("seeded-slot.json"), Files.readAllBytes(directory.resolve("slot-1.json")))
        assertArrayEquals(golden("seeded-manifest.json"), Files.readAllBytes(directory.resolve("slots.json")))
    }

    @Test
    fun `a saved farm is byte for byte what the old path wrote`() {
        slots.save(seededFarm(), 0, goldenAt)
        assertWroteGolden()
    }

    @Test
    fun `saving the snapshot without indexes writes those same bytes`() {
        val farm = seededFarm()
        slots.save(farm.saveSnapshot(), 0, goldenAt)
        assertWroteGolden()
        assertEquals(GameStateJson.encode(farm.copy()), GameStateJson.encode(farm.saveSnapshot()))
    }

    @Test
    fun `an autosave writes those same bytes`() {
        slots.saveIfChanged(seededFarm(), 0, goldenAt)
        assertWroteGolden()
    }

    @Test
    fun `the old path's file loads and saves back byte for byte`() {
        Files.write(directory.resolve("slot-1.json"), golden("seeded-slot.json"))
        val saving = slots
        val loaded = saving.load(0)!!
        Files.delete(directory.resolve("slot-1.json"))
        saving.save(loaded, 0, goldenAt)
        assertWroteGolden()
    }

    @Test
    fun `a snapshot shares nothing that play could change and has no indexes to go stale`() {
        val farm = seededFarm()
        val snapshot = farm.saveSnapshot()
        val before = GameStateJson.encode(snapshot)
        farm.money = 1.0
        farm.storage["wheat"] = 99
        farm.plots.clear()
        farm.career.cropsHarvested = 12
        assertEquals(before, GameStateJson.encode(snapshot))
    }

    @Test
    fun `an autosave of a farm with nothing new writes nothing, whatever time has passed`() {
        val saving = slots
        val farm = stateWith(money = 5.0)
        val engine = GameEngine(farm)
        // The very first second of play starts the Career charts, which is news; the seconds after it are not.
        engine.addPlayTime(1.0)
        assertTrue(saving.saveIfChanged(farm, 0, now))
        val file = Files.readAllBytes(directory.resolve("slot-1.json"))
        val manifest = Files.readAllBytes(directory.resolve("slots.json"))

        for (second in 1L..120L) {
            engine.addPlayTime(1.0)
            engine.markSaved(now.plusSeconds(second))
            assertFalse(saving.saveIfChanged(farm.saveSnapshot(), 0, now.plusSeconds(second)))
        }

        assertArrayEquals(file, Files.readAllBytes(directory.resolve("slot-1.json")))
        assertArrayEquals(manifest, Files.readAllBytes(directory.resolve("slots.json")))
        assertFalse(Files.exists(directory.resolve("slot-1.previous.json")))
    }

    @Test
    fun `a real change writes at once and takes the time played along`() {
        val saving = slots
        val farm = stateWith(money = 5.0)
        val engine = GameEngine(farm)
        engine.addPlayTime(1.0)
        saving.saveIfChanged(farm, 0, now)
        engine.addPlayTime(299.0)
        assertFalse(saving.saveIfChanged(farm, 0, now.plusSeconds(1)))

        farm.money = 6.0
        assertTrue(saving.saveIfChanged(farm, 0, now.plusSeconds(2)))

        val saved = slots.load(0)!!
        assertEquals(6.0, saved.money)
        assertEquals(300.0, saved.playTime)
        assertEquals(300.0, slots.slots()[0]?.playTime)
        assertEquals(5.0, slots.backups(0).first().money)
    }

    @Test
    fun `every part of the farm counts as a change`() {
        val saving = slots
        val farm = stateWith(money = 5.0)
        saving.saveIfChanged(farm, 0, now)
        val changes =
            listOf<(GameState) -> Unit>(
                { it.storage["wheat"] = 1 },
                { it.plots[GridPoint(3, 3)] = Plot("wheat", now) },
                { it.clockOffset = 60.0 },
                { it.career.cropsHarvested = 1 },
                { it.day = 2 },
                { it.fences[GridPoint(4, 4)] = FenceColor.starter.id },
            )
        for (change in changes) {
            change(farm)
            assertTrue(saving.saveIfChanged(farm, 0, now))
            assertFalse(saving.saveIfChanged(farm, 0, now))
        }
    }

    @Test
    fun `the same farm is written again after anything else has touched its slot`() {
        val saving = slots
        val farm = stateWith(money = 5.0)
        saving.save(farm, 0, now)
        assertTrue(saving.saveIfChanged(farm, 0, now), "a plain save does not vouch for the next autosave")
        assertFalse(saving.saveIfChanged(farm, 0, now))

        saving.save(stateWith(money = 6.0), 0, now)
        assertTrue(saving.saveIfChanged(farm, 0, now))
        assertEquals(5.0, slots.load(0)?.money)

        saving.restore(saving.backups(0).first(), now)
        assertTrue(saving.saveIfChanged(farm, 0, now))

        saving.create(0, "Over", stateWith(money = 7.0), now)
        assertTrue(saving.saveIfChanged(farm, 0, now))
        assertEquals(5.0, slots.load(0)?.money)

        saving.delete(0)
        assertTrue(saving.saveIfChanged(farm, 0, now))
        assertEquals(5.0, slots.load(0)?.money)
    }

    @Test
    fun `after a failed write the next autosave tries again though nothing has changed`() {
        val files = OneFailure()
        val saving = SaveSlots(directory, files)
        val farm = stateWith(money = 5.0)
        saving.saveIfChanged(farm, 0, now)

        files.failNextWriteOf = "slot-1.json"
        farm.money = 6.0
        assertThrows(IOException::class.java) { saving.saveIfChanged(farm, 0, now) }
        assertEquals(5.0, slots.load(0)?.money)

        farm.money = 5.0
        assertTrue(saving.saveIfChanged(farm, 0, now))
        farm.money = 6.0
        assertTrue(saving.saveIfChanged(farm, 0, now))
        assertEquals(6.0, slots.load(0)?.money)
    }

    @Test
    fun `a farm that cannot be encoded fails its autosave and the next one tries again`() {
        val saving = slots
        val farm = stateWith(money = 5.0)
        saving.saveIfChanged(farm, 0, now)
        farm.money = Double.NaN
        assertThrows(Exception::class.java) { saving.saveIfChanged(farm, 0, now) }
        farm.money = 5.0
        assertTrue(saving.saveIfChanged(farm, 0, now))
    }

    @Test
    fun `an outside change to the manifest is honoured by an instance that has already read it`() {
        val first = SaveSlots(directory)
        val second = SaveSlots(directory)
        first.save(stateWith(money = 1.0), 0, now)
        assertEquals("Farm 1", first.slots()[0]?.name)

        second.rename(0, "Home")
        second.setActive(4)
        assertEquals("Home", first.slots()[0]?.name)
        assertEquals(4, first.activeSlot)

        first.save(stateWith(money = 2.0), 0, now.plusSeconds(1))
        assertEquals("Home", second.slots()[0]?.name)
        assertEquals(4, second.activeSlot)
        assertEquals(2.0, second.slots()[0]?.money)
    }

    @Test
    fun `a manifest damaged after being read is preserved and rebuilt by the same instance`() {
        val saving = slots
        saving.save(stateWith(money = 1.0), 2, now)
        assertEquals(1.0, saving.slots()[2]?.money)
        val manifest = directory.resolve("slots.json")
        Files.writeString(manifest, "broken")

        assertEquals(0, saving.activeSlot)
        assertEquals(1.0, saving.slots()[2]?.money)
        saving.save(stateWith(money = 2.0), 2, now)

        assertEquals(2.0, slots.slots()[2]?.money)
        val recovered = Files.list(directory.resolve("Recovery")).use { it.toList() }
        assertTrue(recovered.any { Files.readString(it) == "broken" })
    }

    @Test
    fun `a farm file that appears in an empty slot is found by an instance that has already looked`() {
        val saving = slots
        saving.save(stateWith(money = 1.0), 0, now)
        assertNull(saving.slots()[6])
        FileSaveStore(directory.resolve("slot-7.json")).save(stateWith(money = 7.0))
        assertEquals(7.0, saving.slots()[6]?.money)
    }
}

private class OneFailure : LocalSaveFileSystem() {
    var failNextWriteOf: String? = null

    override fun write(
        data: ByteArray,
        to: Path,
    ) {
        if (to.fileName.toString() == failNextWriteOf) {
            failNextWriteOf = null
            throw IOException("Disk full")
        }
        super.write(data, to)
    }
}
