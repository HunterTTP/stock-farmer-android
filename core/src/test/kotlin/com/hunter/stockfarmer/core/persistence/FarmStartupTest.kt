package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class FarmStartupTest {
    @TempDir
    lateinit var directory: Path

    private val now = Instant.ofEpochSecond(1_800_000_000)

    private val slots get() = SaveSlots(directory)

    @Test
    fun `opens the active farm and prepares it without writing`() {
        val state = stateWith(money = 1234.0)
        state.lastSavedAt = now.advancedBy(-600.0)
        slots.save(state, 3, now)
        slots.setActive(3)
        val file = directory.resolve("slot-4.json")
        val original = Files.readAllBytes(file)
        val startup = FarmStartup(slots, now, SeededRandom(42))
        assertEquals(3, startup.slot)
        assertEquals(1234.0, startup.engine.state.money)
        assertEquals(state.clockOffset - 600, startup.engine.state.clockOffset)
        assertNotNull(startup.engine.state.timelapse.historyID)
        assertEquals(now, startup.preparedAt)
        assertArrayEquals(original, Files.readAllBytes(file))
    }

    @Test
    fun `an empty slot generates a real farm without saving it`() {
        val startup = FarmStartup(slots, now, SeededRandom(42))
        assertEquals(0, startup.slot)
        assertTrue(
            startup.engine.state.trees
                .isNotEmpty(),
        )
        assertNotNull(startup.engine.state.timelapse.historyID)
        assertNull(slots.load(0))
    }

    @Test
    fun `overdue animals are present before the farm is revealed`() {
        val home = GridPoint(20, 20)
        val state = stateWith(money = 1000.0)
        state.putBuilding(home, "chickenCoop")
        state.coops[home] = Coop.hatchingAt(now.advancedBy(-Coop.HATCH_INTERVAL))
        slots.save(state, 0, now)
        val startup = FarmStartup(slots, now, SeededRandom(42))
        assertEquals(
            2,
            startup.engine.state.coops[home]
                ?.chickens
                ?.size,
        )
        assertEquals(
            true,
            slots
                .load(0)
                ?.coops
                ?.get(home)
                ?.chickens
                ?.isEmpty(),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["damaged", """{"schemaVersion":999,"state":{}}"""])
    fun `failed loads preserve the original save`(contents: String) {
        val file = directory.resolve("slot-1.json")
        val original = contents.toByteArray()
        Files.write(file, original)
        assertThrows(Exception::class.java) { FarmStartup(slots, now, SeededRandom(42)) }
        assertArrayEquals(original, Files.readAllBytes(file))
    }
}
