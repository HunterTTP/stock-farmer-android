package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.addPlayTime
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

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
}
