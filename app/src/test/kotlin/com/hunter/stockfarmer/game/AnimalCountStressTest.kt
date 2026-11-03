package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.persistence.FarmStartup
import com.hunter.stockfarmer.core.persistence.SaveSlots
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.animals
import com.hunter.stockfarmer.core.rules.countCurrentUnlocksAsSeen
import com.hunter.stockfarmer.core.rules.markSaved
import com.hunter.stockfarmer.core.rules.move
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.nextInt
import com.hunter.stockfarmer.core.util.randomElement
import com.hunter.stockfarmer.ui.sheets.SheetAction
import com.hunter.stockfarmer.ui.sheets.SheetActions
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * An animal only ever leaves a farm because the player released or sold it: not by being stepped, walked or fed, by time
 * passing or going backwards, by a save and load, by a change of farm, or by a tick landing in the middle of any of them.
 */
class AnimalCountStressTest {
    @TempDir
    lateinit var directory: Path

    private class Lcg(
        private var state: Long,
    ) : RandomSource {
        override fun nextLong(): Long {
            state = state * 6364136223846793005L + 1442695040888963407L
            return state xor (state ushr 29)
        }
    }

    private val start = Instant.parse("2026-11-03T04:00:00Z")

    private data class Resident(
        val home: GridPoint,
        val id: Int,
        val isChicken: Boolean,
    )

    private fun residents(state: GameState): Set<Resident> =
        state.herds.flatMap { (home, herd) -> herd.animals.map { Resident(home, it.id, false) } }.toSet() +
            state.coops.flatMap { (home, coop) -> coop.chickens.map { Resident(home, it.id, true) } }.toSet()

    private fun hutchOf(slot: Int) = GridPoint(30, 10 + slot * 12)

    /** Eight rabbits in a level 3 hutch, as on the emulator, with some sheep, hens and ducks; each slot's homes stand elsewhere. */
    private fun farm(
        slot: Int,
        now: Instant,
    ): GameState =
        GameState().apply {
            money = 1_000_000.0 + slot
            val hutch = hutchOf(slot)
            val barn = GridPoint(30, 14 + slot * 12)
            val coop = GridPoint(25, 10 + slot * 12)
            putBuilding(hutch, "rabbitHutch")
            putBuilding(barn, "barn1")
            putBuilding(coop, "chickenCoop")
            homeLevels[hutch] = 3
            herds[hutch] = herdOf("rabbit", 8, hutch, now, birthIn = 240.0)
            herds[barn] = herdOf("sheep", 4, barn, now, birthIn = 480.0)
            val hen = ChickenVariant.available.first()
            val hatched = now.advancedBy(-5_000.0)
            val hens =
                (0..1).map {
                    Chicken(
                        it,
                        hen.id,
                        "Hen",
                        GridPoint(coop.row + 2, coop.col + it),
                        hatchedAt = hatched,
                    )
                }
            coops[coop] = Coop(chickens = hens, nextChickAt = now.advancedBy(300.0), nextChickenID = 2)
            for (row in 10..14) for (col in 10..14) water[GridPoint(row, col + slot * 12)] = WaterStyle.deep
            val pond = GridPoint(12, 12 + slot * 12)
            herds[pond] = herdOf("duck", 3, pond, now, birthIn = 1_800.0)
            for (index in 0..5) replant(GridPoint(35, 10 + slot * 12 + index), now)
        }

    private fun herdOf(
        species: String,
        count: Int,
        home: GridPoint,
        now: Instant,
        birthIn: Double,
    ): Herd {
        val kind = LivestockSpecies.all.first { it.id == species }
        val below = if (species == "duck") 0 else 2
        val animals =
            (0 until count).map { id ->
                val place = GridPoint(home.row + below, home.col + id % 2)
                Livestock.born(id, kind.variants[id % kind.variants.size], kind.name, place, now.advancedBy(-50_000.0))
            }
        return Herd(animals, now.advancedBy(birthIn), now, count)
    }

    private fun GameState.replant(
        tile: GridPoint,
        now: Instant,
    ) {
        farmland.add(tile)
        plots[tile] = Plot("carrot", now.advancedBy(-1_000.0))
    }

    /** One seeded run through everything that could lose an animal, checking after every step. */
    private inner class Run(
        seed: Long,
    ) {
        val choices = Lcg(seed)
        val slots = SaveSlots(directory.resolve("seed-$seed"))
        var at = start
        var slot = 0
        val session: GameSession
        val tick: GameTick
        val alive = mutableMapOf<Int, Set<Resident>>()
        val tally = mutableMapOf<String, Int>()

        init {
            Files.createDirectories(directory.resolve("seed-$seed"))
            for (each in 0..1) {
                slots.save(farm(each, at), each, at)
                alive[each] = residents(slots.load(each)!!)
            }
            slots.setActive(0)
            session = GameSession(FarmStartup(slots, at, Lcg(seed + 1)).engine, Lcg(seed + 2))
            tick = GameTick(session, Lcg(seed + 3))
        }

        private fun count(what: String) {
            tally[what] = (tally[what] ?: 0) + 1
        }

        private fun check(what: String) {
            val now = session.read { residents(it) }
            val gone = alive.getValue(slot) - now
            assertTrue(gone.isEmpty(), "$what took $gone from farm $slot at $at")
            if ((now - alive.getValue(slot)).isNotEmpty()) count("births")
            alive[slot] = now
        }

        private fun saveAndReopen(
            target: Int,
            openAt: Instant,
        ) {
            val snapshot =
                session.act {
                    it.markSaved(at)
                    it.state.saveSnapshot()
                }
            slots.save(snapshot, slot, at)
            slots.setActive(target)
            slot = target
            at = openAt
            val startup = FarmStartup(slots, at, Lcg(choices.nextLong()))
            session.act {
                it.open(startup.engine.state)
                it.countCurrentUnlocksAsSeen()
                session.resetForNewFarm()
            }
        }

        private fun letGo() {
            val animal =
                session.act { engine ->
                    engine.animals.filter { it !is AnimalRef.Pet }.randomElement(choices)
                } ?: return
            val action = if (animal is AnimalRef.Livestock && isDuck(animal)) SheetAction.Sell else SheetAction.Release
            val events = session.act { SheetActions.perform(it, SheetTarget.Animal(animal), action, at).events }
            assertTrue(
                events.any { it is GameEvent.AnimalReleased || it is GameEvent.AnimalSold },
                "$action of $animal: $events",
            )
            val left =
                when (animal) {
                    is AnimalRef.Livestock -> Resident(animal.home, animal.id, false)
                    is AnimalRef.Chicken -> Resident(animal.coop, animal.id, true)
                    is AnimalRef.Pet -> return
                }
            alive[slot] = alive.getValue(slot) - left
            count("released")
        }

        private fun isDuck(animal: AnimalRef.Livestock) =
            session.read { state ->
                state.herds[animal.home]
                    ?.animals
                    ?.firstOrNull { it.id == animal.id }
                    ?.species == LivestockSpecies.duck
            }

        fun step() {
            val roll = choices.nextInt(0..99)
            val what =
                when {
                    roll < 70 -> {
                        at = at.advancedBy(1.0)
                        tick.tick(at)
                        "tick"
                    }

                    roll < 74 -> {
                        at = at.advancedBy(choices.nextInt(2..300).toDouble())
                        tick.tick(at)
                        "gap"
                    }

                    roll < 78 -> {
                        val away = listOf(300.0, 8 * 3_600.0, 72 * 3_600.0, 96 * 3_600.0).randomElement(choices)!!
                        saveAndReopen(slot, at.advancedBy(away))
                        "jump"
                    }

                    roll < 80 -> {
                        // The clock put back while away, as the emulator's was.
                        saveAndReopen(slot, at.advancedBy(-(80 * 3_600.0)))
                        "clock back while away"
                    }

                    roll < 81 -> {
                        at = at.advancedBy(-(80 * 3_600.0))
                        tick.tick(at)
                        "clock back while playing"
                    }

                    roll < 85 -> {
                        saveAndReopen(slot, at)
                        "round trip"
                    }

                    roll < 89 -> {
                        saveAndReopen(1 - slot, at)
                        "switch"
                    }

                    roll < 91 -> {
                        letGo()
                        "release"
                    }

                    roll < 94 -> {
                        val column = 10 + slot * 12 + choices.nextInt(0..5)
                        session.act { it.state.replant(GridPoint(35, column), at) }
                        "replant"
                    }

                    roll < 96 -> {
                        // Lowering a level never turns anyone out: a home keeps whoever it already holds.
                        val hutch = hutchOf(slot)
                        session.act {
                            if (choices.nextInt(0..1) ==
                                0
                            ) {
                                it.state.homeLevels.remove(hutch)
                            } else {
                                it.state.homeLevels[hutch] = 3
                            }
                        }
                        "level"
                    }

                    roll < 98 -> {
                        val tile = GridPoint(10 + choices.nextInt(0..4), 10 + slot * 12 + choices.nextInt(0..4))
                        session.act {
                            if (it.state.water.remove(tile) == null) it.state.water[tile] = WaterStyle.deep
                        }
                        "water"
                    }

                    else -> {
                        session.act { engine ->
                            val animal = engine.animals.randomElement(choices)
                            val direction =
                                listOf(
                                    GridPoint(0, 1),
                                    GridPoint(1, 0),
                                    GridPoint(0, -1),
                                    GridPoint(-1, 0),
                                ).randomElement(choices)
                            if (animal != null && direction != null) engine.move(animal, direction)
                        }
                        "nudge"
                    }
                }
            count(what)
            check(what)
        }
    }

    @Test
    @Timeout(240)
    fun `no animal goes missing through stepping, walking, jumps, saves and changes of farm`() {
        val tally = mutableMapOf<String, Int>()
        for (seed in 1L..12L) {
            val run = Run(seed)
            repeat(1_000) { run.step() }
            for ((what, times) in run.tally) tally[what] = (tally[what] ?: 0) + times
        }
        // A run that never did these would prove nothing.
        for (what in listOf(
            "tick",
            "gap",
            "jump",
            "clock back while away",
            "clock back while playing",
            "round trip",
            "switch",
            "released",
            "births",
        )) {
            assertTrue((tally[what] ?: 0) > 0, "never did $what: $tally")
        }
    }

    @Test
    @Timeout(240)
    fun `a tick on its own thread takes nothing from a farm being switched and played`() {
        val slots = SaveSlots(directory)
        Files.createDirectories(directory)
        for (each in 0..1) slots.save(farm(each, start), each, start)
        slots.setActive(0)
        val seeded = (0..7).toSet()

        val clock = AtomicLong(start.toEpochMilli())
        val now = { Instant.ofEpochMilli(clock.get()) }
        val background = BackgroundSaveScheduler()
        val closed = CountDownLatch(1)
        val scheduler =
            object : SaveScheduler by background {
                override fun close() {
                    background.close()
                    closed.countDown()
                }
            }
        val persistence = GamePersistence(slots, scheduler, now, Lcg(1), null)
        persistence.resume()
        val session = persistence.session

        val failure = AtomicReference<Throwable?>(null)
        val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, thrown -> failure.set(thrown) })
        val tick = GameTick(session, Lcg(2))
        val loop =
            TickLoop(scope, Dispatchers.Default, 1, tick::forgetClocks) {
                clock.addAndGet(250)
                tick.tick(now())
            }
        loop.start()

        fun rabbitsOf(
            state: GameState,
            slot: Int,
        ) = state.herds[hutchOf(slot)]
            ?.animals
            ?.map { it.id }
            ?.toSet()

        val nudges = Lcg(3)
        var looks = 0
        repeat(60) { round ->
            val target = round % 2
            val done = CountDownLatch(1)
            persistence.switchTo(target)
            persistence.slots { done.countDown() }
            assertTrue(done.await(WAIT_SECONDS, TimeUnit.SECONDS))
            repeat(25) {
                session.act { engine ->
                    val rabbits = rabbitsOf(engine.state, target)!!
                    assertTrue(rabbits.containsAll(seeded), "farm $target has rabbits $rabbits")
                    assertNull(rabbitsOf(engine.state, 1 - target), "the other farm's hutch is on farm $target")
                    engine.animals.randomElement(nudges)?.let { engine.move(it, GridPoint(0, 1)) }
                    looks++
                }
                Thread.yield()
            }
        }

        loop.stop()
        scope.cancel()
        persistence.close()
        assertTrue(closed.await(WAIT_SECONDS, TimeUnit.SECONDS))
        assertNull(failure.get())
        assertEquals(60 * 25, looks)
        for (each in 0..1) {
            val saved = SaveSlots(directory).load(each)!!
            assertTrue(rabbitsOf(saved, each)!!.containsAll(seeded), "slot $each saved ${rabbitsOf(saved, each)}")
            assertNull(rabbitsOf(saved, 1 - each), "slot $each saved the other farm's hutch")
        }
    }

    private companion object {
        const val WAIT_SECONDS = 60L
    }
}
