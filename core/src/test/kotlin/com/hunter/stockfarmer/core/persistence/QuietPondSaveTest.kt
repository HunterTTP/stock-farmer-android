package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.addPlayTime
import com.hunter.stockfarmer.core.rules.giveMeal
import com.hunter.stockfarmer.core.rules.markSaved
import com.hunter.stockfarmer.core.rules.sellLivestock
import com.hunter.stockfarmer.core.rules.updateHerds
import com.hunter.stockfarmer.core.rules.updateLivestock
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.secondsSince
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.math.abs

/** A farm's herds are stepped once a second; that alone is no reason to write it, and nothing is lost by not writing. */
class QuietPondSaveTest {
    @TempDir
    lateinit var directory: Path

    private val start = Instant.ofEpochSecond(40_000_000)
    private val pond = GridPoint(20, 20)
    private val hutch = GridPoint(30, 10)
    private val slotFile get() = directory.resolve("slot-1.json")

    private fun pondFarm(): GameEngine {
        val engine = GameEngine(GameState())
        for (row in 19..21) for (col in 19..21) engine.state.water[GridPoint(row, col)] = WaterStyle.deep
        engine.updateHerds(start, SeededRandom(1))
        return engine
    }

    /** A pond with a grown duck and its next duckling a long way off. */
    private fun pondFarmWithDuck(): GameEngine =
        pondFarm().also { engine ->
            val duck = LivestockSpecies.duck
            val grown = Livestock.born(0, duck.variants[0], duck.name, pond, start.advancedBy(-duck.growDuration))
            engine.state.herds[pond] = Herd(listOf(grown), start.advancedBy(3_600.0), start, 1)
        }

    /** A hutch with two young rabbits, a birth due at 200 s and another at 440 s, and a meal starting at 100 s, beside a pond. */
    private fun busyFarm(): GameEngine =
        pondFarm().also { engine ->
            engine.state.putBuilding(hutch, "rabbitHutch")
            val rabbit = LivestockSpecies.all.first { it.id == "rabbit" }
            val rabbits =
                (0..1).map { id ->
                    val place = GridPoint(hutch.row + 2, hutch.col + id)
                    Livestock.born(id, rabbit.variants[0], rabbit.name, place, start.advancedBy(-60.0))
                }
            engine.state.herds[hutch] = Herd(rabbits, start.advancedBy(200.0), start, 2)
            engine.giveMeal(AnimalRef.Livestock(hutch, 0), start.advancedBy(100.0))
        }

    private fun GameEngine.secondPasses(
        at: Instant,
        random: SeededRandom,
    ) {
        addPlayTime(1.0)
        updateHerds(at, random)
        markSaved(at)
    }

    @Test
    fun `a quiet farm with a pond writes nothing however long its herds are stepped`() {
        val saving = SaveSlots(directory)
        val engine = pondFarmWithDuck()
        engine.addPlayTime(1.0)
        assertTrue(saving.saveIfChanged(engine.state, 0, start))
        val file = Files.readAllBytes(slotFile)
        val manifest = Files.readAllBytes(directory.resolve("slots.json"))

        val random = SeededRandom(2)
        for (second in 1..120) {
            val at = start.advancedBy(second.toDouble())
            engine.secondPasses(at, random)
            assertEquals(at, engine.state.herds[pond]?.processedAt)
            assertFalse(saving.saveIfChanged(engine.state.saveSnapshot(), 0, at), "second $second")
        }

        assertArrayEquals(file, Files.readAllBytes(slotFile))
        assertArrayEquals(manifest, Files.readAllBytes(directory.resolve("slots.json")))
        assertFalse(Files.exists(directory.resolve("slot-1.previous.json")))
    }

    @Test
    fun `a duckling born on a quiet pond is written`() {
        val saving = SaveSlots(directory)
        val engine = pondFarm()
        engine.addPlayTime(1.0)
        saving.saveIfChanged(engine.state, 0, start)

        val random = SeededRandom(2)
        for (second in 1..599) engine.secondPasses(start.advancedBy(second.toDouble()), random)
        assertFalse(saving.saveIfChanged(engine.state, 0, start.advancedBy(599.0)))

        val born = start.advancedBy(600.0)
        engine.secondPasses(born, random)
        assertTrue(saving.saveIfChanged(engine.state, 0, born))

        val saved = SaveSlots(directory).load(0)!!.herds.getValue(pond)
        assertEquals(1, saved.animals.size)
        assertEquals(born, saved.processedAt)
    }

    @Test
    fun `every real change to a herd is written`() {
        val saving = SaveSlots(directory)
        val engine = pondFarmWithDuck()
        saving.saveIfChanged(engine.state, 0, start)
        assertFalse(saving.saveIfChanged(engine.state, 0, start))

        val state = engine.state
        val changes =
            listOf<() -> Unit>(
                { engine.updateLivestock(pond, 0) { it.copy(position = GridPoint(19, 19)) } },
                { engine.updateLivestock(pond, 0) { it.copy(name = "Quackers") } },
                { engine.updateLivestock(pond, 0) { it.copy(growthBonus = 1.0) } },
                { state.herds[pond] = state.herds.getValue(pond).copy(nextBirthAt = start.advancedBy(9_000.0)) },
                { state.herds[pond] = state.herds.getValue(pond).copy(nextAnimalID = 2) },
                { engine.sellLivestock(0, pond) },
                { state.herds[GridPoint(5, 5)] = Herd(nextBirthAt = start) },
                { state.herds.remove(GridPoint(5, 5)) },
            )
        for ((index, change) in changes.withIndex()) {
            change()
            assertTrue(saving.saveIfChanged(engine.state, 0, start), "change $index")
            assertFalse(saving.saveIfChanged(engine.state, 0, start), "change $index, again")
        }
    }

    @Test
    fun `how far the herds have got alone is written by a flush though not by an autosave`() {
        val saving = SaveSlots(directory)
        val engine = pondFarmWithDuck()
        engine.addPlayTime(1.0)
        saving.saveIfChanged(engine.state, 0, start)

        val random = SeededRandom(2)
        val later = start.advancedBy(30.0)
        for (second in 1..30) engine.secondPasses(start.advancedBy(second.toDouble()), random)
        assertFalse(saving.saveIfChanged(engine.state, 0, later))
        assertEquals(
            start,
            SaveSlots(directory)
                .load(0)!!
                .herds
                .getValue(pond)
                .processedAt,
        )

        saving.save(engine.state, 0, later)

        val saved = SaveSlots(directory).load(0)!!
        assertEquals(later, saved.herds.getValue(pond).processedAt)
        assertEquals(later, saved.lastSavedAt)
    }

    @Test
    fun `a restart from the older herd time catches up to what the steps it missed would have done`() {
        val seed = 11L
        val reopenedAt = start.advancedBy(500.0)

        // The farm played straight on, a step every second, with nothing unusual: what the restart has to match.
        val played = busyFarm()
        val playing = SeededRandom(seed)
        for (second in 1..500) played.updateHerds(start.advancedBy(second.toDouble()), playing)

        // The same farm, quiet for a minute with nothing written, then gone without a chance to save.
        val saving = SaveSlots(directory)
        val quiet = busyFarm()
        assertTrue(saving.saveIfChanged(quiet.state, 0, start))
        val quietly = SeededRandom(seed)
        for (second in 1..60) {
            val at = start.advancedBy(second.toDouble())
            quiet.updateHerds(at, quietly)
            assertFalse(saving.saveIfChanged(quiet.state, 0, at), "second $second")
        }

        val reopened = GameEngine(SaveSlots(directory).load(0)!!)
        for (herd in reopened.state.herds.values) assertEquals(start, herd.processedAt)
        reopened.updateHerds(reopenedAt, SeededRandom(seed))

        // The two births and the meal all fall after the farm went quiet, so the catch-up has real work to do.
        val expected = played.state.herds.getValue(hutch)
        val actual = reopened.state.herds.getValue(hutch)
        assertEquals(4, expected.animals.size)
        assertEquals(expected.animals.map { it.id }, actual.animals.map { it.id })
        assertEquals(expected.animals.map { it.variant }, actual.animals.map { it.variant })
        assertEquals(expected.animals.map { it.position }, actual.animals.map { it.position })
        assertEquals(expected.animals.map { it.bornAt }, actual.animals.map { it.bornAt })
        assertEquals(expected.nextBirthAt, actual.nextBirthAt)
        assertEquals(expected.nextAnimalID, actual.nextAnimalID)
        assertEquals(reopenedAt, actual.processedAt)
        assertTrue(expected.animals[0].growthBonus > 0.0)
        for ((want, got) in expected.animals.zip(actual.animals)) {
            assertEquals(want.growthBonus, got.growthBonus, ROUNDING)
            assertTrue(abs(want.readyAt.secondsSince(got.readyAt)) < ROUNDING, "${want.readyAt} ${got.readyAt}")
        }
        assertEquals(
            played.state.herds
                .getValue(pond)
                .nextBirthAt,
            reopened.state.herds
                .getValue(pond)
                .nextBirthAt,
        )
    }

    private companion object {
        const val ROUNDING = 1e-3
    }
}
