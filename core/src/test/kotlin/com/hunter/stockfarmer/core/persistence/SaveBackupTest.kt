package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class SaveBackupTest {
    @TempDir
    lateinit var directory: Path

    private val start = Instant.ofEpochSecond(40_000_000)

    private val slots get() = SaveSlots(directory)

    private fun minutes(count: Double): Instant = start.advancedBy(count * 60)

    private fun game(
        money: Double,
        day: Int,
    ): GameState = stateWith(money = money).also { it.day = day }

    @Test
    fun `a single save has no backups`() {
        slots.save(stateWith(money = 1.0), 0, start)
        assertTrue(slots.backups(0).isEmpty())
    }

    @Test
    fun `the latest backup is the save before the current one`() {
        slots.save(stateWith(money = 1.0), 0, start)
        slots.save(stateWith(money = 2.0), 0, minutes(1.0))
        assertEquals(listOf(1.0), slots.backups(0).map { it.money })
        assertEquals(listOf(SaveBackup.Kind.latest), slots.backups(0).map { it.kind })
    }

    @Test
    fun `autosaves inside the interval do not advance the older backup`() {
        for (money in 1..5) slots.save(stateWith(money = money.toDouble()), 0, minutes(money.toDouble()))
        assertEquals(listOf(SaveBackup.Kind.latest), slots.backups(0).map { it.kind })
        assertEquals(4.0, slots.backups(0).first().money)
    }

    @Test
    fun `the older backup trails behind once the interval has passed`() {
        slots.save(stateWith(money = 1.0), 0, start)
        slots.save(stateWith(money = 2.0), 0, minutes(1.0))
        slots.save(stateWith(money = 3.0), 0, minutes(12.0))
        slots.save(stateWith(money = 4.0), 0, minutes(13.0))
        assertEquals(listOf(3.0, 1.0), slots.backups(0).map { it.money })
        assertEquals(listOf(SaveBackup.Kind.latest, SaveBackup.Kind.older), slots.backups(0).map { it.kind })
    }

    @Test
    fun `restoring brings back the backup and keeps the current game as the latest backup`() {
        slots.save(stateWith(money = 1.0), 0, start)
        slots.save(stateWith(money = 2.0), 0, minutes(1.0))
        slots.save(stateWith(money = 3.0), 0, minutes(12.0))
        val older = slots.backups(0).last()
        val restored = slots.restore(older, minutes(13.0))
        assertEquals(1.0, restored.money)
        assertEquals(1.0, slots.load(0)?.money)
        assertEquals(1.0, slots.slots()[0]?.money)
        assertEquals(listOf(3.0, 2.0), slots.backups(0).map { it.money })
    }

    @Test
    fun `restoring the latest backup can be undone by restoring again`() {
        slots.save(stateWith(money = 1.0), 0, start)
        slots.save(stateWith(money = 2.0), 0, minutes(1.0))
        slots.restore(slots.backups(0).first(), minutes(2.0))
        assertEquals(1.0, slots.load(0)?.money)
        slots.restore(slots.backups(0).first(), minutes(3.0))
        assertEquals(2.0, slots.load(0)?.money)
    }

    @Test
    fun `restoring over a damaged game keeps both good backups and the damaged file`() {
        slots.save(stateWith(money = 1.0), 0, start)
        slots.save(stateWith(money = 2.0), 0, minutes(1.0))
        slots.save(stateWith(money = 3.0), 0, minutes(12.0))
        val damaged = "damaged".toByteArray()
        Files.write(directory.resolve("slot-1.json"), damaged)
        val latest = slots.backups(0).first()
        assertEquals(2.0, slots.restore(latest, minutes(13.0)).money)
        assertEquals(listOf(2.0, 1.0), slots.backups(0).map { it.money })
        val recovered = Files.list(directory.resolve("Recovery")).use { it.toList() }
        assertTrue(recovered.any { Files.readAllBytes(it).contentEquals(damaged) })
    }

    @Test
    fun `a damaged backup is not offered`() {
        slots.save(stateWith(money = 1.0), 0, start)
        slots.save(stateWith(money = 2.0), 0, minutes(1.0))
        Files.writeString(directory.resolve("slot-1.previous.json"), "damaged")
        assertTrue(slots.backups(0).isEmpty())
    }

    @Test
    fun `restoring a missing backup fails without changing the game`() {
        slots.save(stateWith(money = 1.0), 0, start)
        val missing = SaveBackup(0, SaveBackup.Kind.older, null, 1, 0.0)
        assertThrows(SaveRestoreException::class.java) { slots.restore(missing) }
        assertEquals(1.0, slots.load(0)?.money)
    }

    @Test
    fun `the daily backup holds the last save of the day before once the day rolls`() {
        slots.save(game(1.0, 1), 0, start)
        slots.save(game(2.0, 1), 0, minutes(1.0))
        assertFalse(slots.backups(0).map { it.kind }.contains(SaveBackup.Kind.daily))
        slots.save(game(3.0, 2), 0, minutes(2.0))
        assertEquals(2.0, slots.backups(0).first { it.kind == SaveBackup.Kind.daily }.money)
    }

    @Test
    fun `the daily backup stays put through the rest of the day and is restorable`() {
        slots.save(game(1.0, 1), 0, start)
        slots.save(game(2.0, 2), 0, minutes(1.0))
        for (money in 3..6) slots.save(game(money.toDouble(), 2), 0, minutes(money * 30.0))
        val daily = slots.backups(0).first { it.kind == SaveBackup.Kind.daily }
        assertEquals(1.0, daily.money)
        assertEquals(1.0, slots.restore(daily, minutes(300.0)).money)
        slots.save(game(7.0, 2), 0, minutes(301.0))
        assertEquals(1.0, slots.backups(0).first { it.kind == SaveBackup.Kind.daily }.money)
    }

    @Test
    fun `backups rotate the same through one long-lived instance that remembers its file`() {
        val saving = slots
        saving.save(game(1.0, 1), 0, start)
        saving.save(game(2.0, 1), 0, minutes(1.0))
        saving.save(game(3.0, 1), 0, minutes(12.0))
        saving.save(game(4.0, 2), 0, minutes(13.0))
        assertEquals(4.0, saving.load(0)?.money)
        assertEquals(
            listOf(SaveBackup.Kind.latest to 3.0, SaveBackup.Kind.older to 1.0, SaveBackup.Kind.daily to 3.0),
            saving.backups(0).map { it.kind to it.money },
        )
    }

    @Test
    fun `quiet autosaves do not wear the backups away`() {
        val saving = slots
        val farm = game(1.0, 1)
        saving.saveIfChanged(farm, 0, start)
        farm.money = 2.0
        saving.saveIfChanged(farm, 0, minutes(1.0))
        for (minute in 2..60) saving.saveIfChanged(farm, 0, minutes(minute.toDouble()))
        assertEquals(listOf(SaveBackup.Kind.latest to 1.0), saving.backups(0).map { it.kind to it.money })
    }

    @Test
    fun `a damaged game refuses to save rather than becoming the backup of the next one`() {
        slots.save(game(1.0, 1), 0, start)
        slots.save(game(2.0, 1), 0, minutes(1.0))
        val damaged = "damaged".toByteArray()
        Files.write(directory.resolve("slot-1.json"), damaged)
        assertThrows(Exception::class.java) { slots.save(game(3.0, 2), 0, minutes(20.0)) }
        assertEquals(listOf(SaveBackup.Kind.latest to 1.0), slots.backups(0).map { it.kind to it.money })
        assertTrue(Files.readAllBytes(directory.resolve("slot-1.json")).contentEquals(damaged))
    }
}
