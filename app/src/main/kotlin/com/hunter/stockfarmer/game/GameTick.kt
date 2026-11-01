package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.NightPulse
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.rules.Weather
import com.hunter.stockfarmer.core.rules.addPlayTime
import com.hunter.stockfarmer.core.rules.beginNewDayIfDue
import com.hunter.stockfarmer.core.rules.eatFavoriteCrops
import com.hunter.stockfarmer.core.rules.growTrees
import com.hunter.stockfarmer.core.rules.layEggs
import com.hunter.stockfarmer.core.rules.learnWheatIsRipe
import com.hunter.stockfarmer.core.rules.minuteOfDay
import com.hunter.stockfarmer.core.rules.move
import com.hunter.stockfarmer.core.rules.noteSeenEnvironment
import com.hunter.stockfarmer.core.rules.petTendsCrop
import com.hunter.stockfarmer.core.rules.updateCoops
import com.hunter.stockfarmer.core.rules.updateFish
import com.hunter.stockfarmer.core.rules.updateHerds
import com.hunter.stockfarmer.core.rules.wanderDirection
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.nextUnit
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * What happens in the game as time goes by, once a second: chicks hatch and calves are born, hens lay, fish come and go,
 * pets ripen the crops they stand on, animals graze, trees grow and the day turns over at midnight.
 */
class GameTick(
    private val session: GameSession,
    private val random: RandomSource = SystemRandomSource,
) {
    private val fishSwimming = FishSwimming(random)
    private var lastTend: Instant? = null
    private var lastPlayTick: Instant? = null
    private var petDwell = emptyMap<GridPoint, Double>()
    private var pendingSummary: Pair<DaySummary, Instant>? = null

    /** Forgets the running clocks and any day summary still to come, as when another farm is opened. */
    fun reset() {
        lastTend = null
        lastPlayTick = null
        petDwell = emptyMap()
        fishSwimming.forget()
        pendingSummary = null
    }

    /**
     * Runs one step for the moment [now] and says what happened that the player should be told about. Nothing happens
     * while the app is away or the timelapse viewer is open; the clocks start over when play resumes.
     */
    fun tick(now: Instant = Instant.now()): List<GameEvent> =
        session.act { engine ->
            if (!session.isActive || session.isTimelapseViewerOpen) {
                lastTend = null
                lastPlayTick = null
                return@act emptyList()
            }
            countPlayTime(engine, now)
            noteWeatherSeen(engine, now)
            releaseDaySummary(now)
            val interval = min(MOST_INTERVAL, lastTend?.let { now.secondsSince(it) } ?: 0.0)
            lastTend = now
            if (interval <= 0) return@act emptyList()
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
            val minute = engine.minuteOfDay(now)
            engine.beginNewDayIfDue(now)?.let {
                val view = session.view.snapshot
                val middle = GameState.GRID_SIZE / 2.0
                session.wildGrowth.advance(engine, now, finishing = true)
                session.wildGrowth.begin(
                    engine,
                    engine.state.lastNewDayNight!!,
                    view?.let { GameState.GRID_SIZE - it.centerY / 100.0 } ?: middle,
                    view?.let { it.centerX / 100.0 } ?: middle,
                    random,
                )
                session.beginDay(engine.state.lastNewDayNight)
                pendingSummary = it to now.advancedBy(max(0.0, NightPulse.SUMMARY_MINUTE - minute))
            }
            session.wildGrowth.advance(engine, now)
            engine.learnWheatIsRipe(now)
            walkAnimals(engine, now, random, session.animalGestures::isNudging)
            fishSwimming.advance(engine, now)
            events.filter { it !is GameEvent.Hatched && it !is GameEvent.Born }
        }

    /** Counts the time since the last tick as play, except a gap too long to be play. */
    private fun countPlayTime(
        engine: GameEngine,
        now: Instant,
    ) {
        val gap = lastPlayTick?.let { now.secondsSince(it) }
        lastPlayTick = now
        if (gap != null && gap <= LONGEST_PLAY_GAP) engine.addPlayTime(gap)
    }

    /** Rain, or snow in winter, that falls hard enough to be seen goes in the Career record. */
    private fun noteWeatherSeen(
        engine: GameEngine,
        now: Instant,
    ) {
        if (Weather.rainAmount(now, engine.state.clockOffset) <= VISIBLE_RAIN) return
        engine.noteSeenEnvironment(
            if (engine.state.season ==
                Season.winter
            ) {
                CareerRecord.SNOW_ID
            } else {
                CareerRecord.RAIN_ID
            },
        )
    }

    /** The new day's summary appears once its delay is up, while the midnight chime is still ringing. */
    private fun releaseDaySummary(now: Instant) {
        val (summary, due) = pendingSummary ?: return
        if (now < due) return
        pendingSummary = null
        session.announce(summary)
    }

    private companion object {
        const val MOST_INTERVAL = 2.0
        const val LONGEST_PLAY_GAP = 5.0
        const val VISIBLE_RAIN = 0.15
    }
}

/** Every animal that has somewhere to go takes a step now and then. */
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
