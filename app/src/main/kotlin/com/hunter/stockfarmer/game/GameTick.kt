package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.addPlayTime
import com.hunter.stockfarmer.core.rules.beginNewDayIfDue
import com.hunter.stockfarmer.core.rules.eatFavoriteCrops
import com.hunter.stockfarmer.core.rules.growTrees
import com.hunter.stockfarmer.core.rules.layEggs
import com.hunter.stockfarmer.core.rules.move
import com.hunter.stockfarmer.core.rules.petTendsCrop
import com.hunter.stockfarmer.core.rules.updateCoops
import com.hunter.stockfarmer.core.rules.updateFish
import com.hunter.stockfarmer.core.rules.updateHerds
import com.hunter.stockfarmer.core.rules.wanderDirection
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.core.util.nextUnit
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant
import kotlin.math.min

/**
 * What happens in the game as time goes by, once a second: chicks hatch and calves are born, hens lay, fish come and go,
 * pets ripen the crops they stand on, animals graze, trees grow and the day turns over at midnight.
 */
class GameTick(
    private val session: GameSession,
    private val random: RandomSource = SystemRandomSource,
) {
    private var lastTend: Instant? = null
    private var petDwell = emptyMap<GridPoint, Double>()

    /** Runs one step for the moment [now] and says what happened that the player should be told about. */
    fun tick(now: Instant = Instant.now()): List<GameEvent> =
        session.act { engine ->
            val interval = min(MOST_INTERVAL, lastTend?.let { now.secondsSince(it) } ?: 0.0)
            lastTend = now
            if (interval <= 0) return@act emptyList()
            engine.addPlayTime(interval)
            val events =
                (engine.updateCoops(now, random) + engine.updateHerds(now, random)).toMutableList()
            engine.layEggs(interval, now, random)
            engine.updateFish(
                now,
                onTheLine =
                    session.casts.value
                        ?.let { setOf(it.pond) }
                        .orEmpty(),
                using = random,
            )
            val dwell = mutableMapOf<GridPoint, Double>()
            for ((house, pet) in engine.state.pets) {
                if (engine.state.plots[pet.position] == null) continue
                dwell[house] = (petDwell[house] ?: 0.0) + interval
                events += engine.petTendsCrop(house, dwell.getValue(house), interval, now, random)
            }
            petDwell = dwell
            events += engine.eatFavoriteCrops(now)
            engine.growTrees(now)
            engine.beginNewDayIfDue(now)?.let {
                val view = session.view.snapshot
                val middle = com.hunter.stockfarmer.core.model.GameState.GRID_SIZE / 2.0
                session.wildGrowth.advance(engine, now, finishing = true)
                session.wildGrowth.begin(
                    engine,
                    engine.state.lastNewDayNight!!,
                    view?.let { com.hunter.stockfarmer.core.model.GameState.GRID_SIZE - it.centerY / 100.0 } ?: middle,
                    view?.let { it.centerX / 100.0 } ?: middle,
                    random,
                )
                session.announce(it)
            }
            session.wildGrowth.advance(engine, now)
            walkAnimals(engine, now, random, session.animalGestures::isNudging)
            events.filter { it !is GameEvent.Hatched && it !is GameEvent.Born }
        }

    private companion object {
        const val MOST_INTERVAL = 2.0
    }
}

/** Every animal that has somewhere to go takes a step now and then, the way the iOS scene walks them. */
internal fun walkAnimals(
    engine: GameEngine,
    now: Instant,
    random: RandomSource,
    isNudging: (AnimalRef) -> Boolean = { false },
) {
    val state = engine.state
    val animals =
        state.pets.keys.map { AnimalRef.Pet(it) } +
            state.coops.flatMap { (coop, value) -> value.chickens.map { AnimalRef.Chicken(coop, it.id) } } +
            state.herds.flatMap { (home, herd) -> herd.animals.map { AnimalRef.Livestock(home, it.id) } }
    for (animal in animals) {
        if (isNudging(animal)) continue
        if (random.nextUnit() > STEP_CHANCE) continue
        engine.wanderDirection(animal, now, random)?.let { engine.move(animal, it) }
    }
}

private const val STEP_CHANCE = 0.6
