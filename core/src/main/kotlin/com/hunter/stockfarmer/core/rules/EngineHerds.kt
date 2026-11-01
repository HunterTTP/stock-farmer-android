package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LivestockHome
import com.hunter.stockfarmer.core.catalog.LivestockWork
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.AnimalMeal
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.randomElement
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/** How long a herd is brought up to date after the game has been closed; anything longer is treated as this long. */
private const val LONGEST_HERD_CATCH_UP = 3.0 * 24 * 60 * 60

/** Growth and production are worked out in steps of this size, so meal expiry and maturity boundaries are respected. */
private const val HERD_STEP = 5.0

fun GameEngine.animals(home: GridPoint): List<Livestock> = state.herds[home]?.animals ?: emptyList()

fun GameEngine.livestock(
    id: Int,
    home: GridPoint,
): Livestock? = animals(home).firstOrNull { it.id == id }

/**
 * Brings every building that holds livestock, and every pond with ducks, up to date: animals grow and produce faster after
 * eating their favorite crops and babies are born as they come due. A new building gets its first baby at once and the
 * rest follow, up to the building's limit.
 */
fun GameEngine.updateHerds(
    now: Instant,
    using: RandomSource,
): List<GameEvent> {
    val events = mutableListOf<GameEvent>()
    for (placed in state.placedBuildings) {
        val home = placed.building.livestockHome ?: continue
        if (state.herds[placed.origin] == null) state.herds[placed.origin] = Herd(nextBirthAt = now)
        events += advanceHerd(placed.origin, home, now, using)
    }
    settleDucksOnPonds(now)
    for (origin in pondHerdOrigins) events += advanceHerd(origin, LivestockHome.pond, now, using)
    return events
}

private fun GameEngine.advanceHerd(
    origin: GridPoint,
    home: LivestockHome,
    now: Instant,
    using: RandomSource,
): List<GameEvent> {
    val events = mutableListOf<GameEvent>()
    val earliest = now.advancedBy(-LONGEST_HERD_CATCH_UP)
    var time = maxOf(state.herds[origin]?.processedAt ?: now, earliest)
    do {
        val previous = time
        time = minOf(now, time.advancedBy(HERD_STEP))
        growHerd(origin, time.secondsSince(previous), time)
        events += bearDueBabies(origin, home, time, using)
    } while (time < now)
    state.herds[origin]?.let { state.herds[origin] = it.copy(processedAt = now) }
    return events
}

/** Applies only the meal time overlapping this step, to growth first and then production. */
private fun GameEngine.growHerd(
    origin: GridPoint,
    step: Double,
    time: Instant,
) {
    val herd = state.herds[origin] ?: return
    state.herds[origin] =
        herd.copy(
            animals =
                herd.animals.map { animal ->
                    val meal = animal.meal ?: return@map animal
                    val start = maxOf(time.advancedBy(-max(0.0, step)), animal.bornAt)
                    val boosted = meal.boostedSeconds(start, time)
                    val boostStart = maxOf(start, meal.eatenAt)
                    val remainingGrowth = max(0.0, animal.species.growDuration - animal.age(boostStart))
                    val growthBoost = min(boosted, remainingGrowth / AnimalMeal.MULTIPLIER)
                    val extraGrowth = growthBoost * (AnimalMeal.MULTIPLIER - 1)
                    var readyAt = animal.readyAt.advancedBy(-extraGrowth)
                    if (animal.species.work is LivestockWork.Gives) {
                        val workStart = maxOf(boostStart.advancedBy(growthBoost), animal.lastWorkedAt ?: boostStart)
                        val extraWork = meal.boostedSeconds(workStart, time) * (AnimalMeal.MULTIPLIER - 1)
                        readyAt = readyAt.advancedBy(-extraWork)
                    }
                    animal.copy(growthBonus = animal.growthBonus + extraGrowth, readyAt = readyAt)
                },
        )
}

private fun GameEngine.bearDueBabies(
    origin: GridPoint,
    home: LivestockHome,
    time: Instant,
    using: RandomSource,
): List<GameEvent> {
    if (home == LivestockHome.pond) return bearDucklings(origin, time, using)
    val events = mutableListOf<GameEvent>()
    // A full building has no more babies; the timer starts afresh when an animal leaves.
    state.herds[origin]?.let { herd ->
        if (herd.animals.size >= homeCapacity(origin)) {
            state.herds[origin] = herd.copy(nextBirthAt = time.advancedBy(home.birthInterval))
            return events
        }
    }
    while (true) {
        val herd = state.herds[origin] ?: break
        if (herd.animals.size >= homeCapacity(origin) || time < herd.nextBirthAt) break
        // A grown animal standing where babies appear doesn't stop one being born: it appears on the nearest free tile.
        val spot = babyTile(origin) ?: break
        bearBaby(origin, home, spot, herd.nextBirthAt, using)
        events += GameEvent.Born(spot)
    }
    return events
}

/**
 * A duckling appears when its herd's time has come, on open water in the body of water the herd's tile is in, as long as
 * that body and the map have room for another duck. With no room it tries again after a birth interval, as a full building does.
 */
private fun GameEngine.bearDucklings(
    origin: GridPoint,
    time: Instant,
    using: RandomSource,
): List<GameEvent> {
    val events = mutableListOf<GameEvent>()
    val home = LivestockHome.pond
    val layout = state.waterLayout
    val body = layout.bodyIndexByTile[origin] ?: return events
    if (layout.bodies[body].interior.isEmpty()) return events
    while (true) {
        val herd = state.herds[origin] ?: break
        if (time < herd.nextBirthAt) break
        val occupants = waterOccupants
        val spot =
            if (duckCount(body) < animalLimit(body) && occupants.ducks.size < animalLimitOnTheMap) {
                openWaterTiles(Waterfolk.duck, body, occupants).randomElement(using)
            } else {
                null
            }
        if (spot == null) {
            state.herds[origin] = herd.copy(nextBirthAt = time.advancedBy(home.birthInterval))
            break
        }
        bearBaby(origin, home, spot, herd.nextBirthAt, using)
        events += GameEvent.Born(spot)
    }
    return events
}

private fun GameEngine.babyTile(origin: GridPoint): GridPoint? =
    state.building(origin)?.let { spawnTile(it, AnimalRef.Livestock(origin, -1)) }

private fun GameEngine.bearBaby(
    origin: GridPoint,
    home: LivestockHome,
    spot: GridPoint,
    due: Instant,
    using: RandomSource,
) {
    val herd = state.herds[origin] ?: return
    val species = home.species.randomElement(using) ?: return
    val variant = species.variants.randomElement(using) ?: return
    val baby = Livestock.born(herd.nextAnimalID, variant, species.name, spot, due)
    state.herds[origin] =
        herd.copy(
            animals = herd.animals + baby,
            nextAnimalID = herd.nextAnimalID + 1,
            nextBirthAt = due.advancedBy(home.birthInterval),
        )
}

/** The player lets an animal go: it is gone, for no money or XP, and the building has another baby in its own time. */
fun GameEngine.releaseLivestock(
    id: Int,
    home: GridPoint,
): List<GameEvent> {
    val animal = livestock(id, home) ?: return emptyList()
    val herd = state.herds.getValue(home)
    state.herds[home] = herd.copy(animals = herd.animals.filter { it.id != id })
    return listOf(GameEvent.AnimalReleased(animal.position))
}

/** The player sells an animal that has a price: it is gone and the price is paid, and its pond or building has another baby in its own time. */
fun GameEngine.sellLivestock(
    id: Int,
    home: GridPoint,
): List<GameEvent> {
    val animal = livestock(id, home) ?: return emptyList()
    val price = animal.species.salePrice ?: return emptyList()
    val herd = state.herds.getValue(home)
    state.herds[home] = herd.copy(animals = herd.animals.filter { it.id != id })
    credit(price)
    recordSale(animal.species.id, animal.species.name, 1, price)
    return listOf(GameEvent.AnimalSold(animal.position, price))
}

/** How long until an animal grows up, counting any remaining meal boost. */
fun GameEngine.timeUntilGrown(
    animal: Livestock,
    now: Instant,
): Double = animal.timeUntilGrown(now)

/** The animals following their building when it is moved all appear on the tile in front of it, to walk out from there. */
internal fun GameEngine.moveHerd(
    oldHome: GridPoint,
    newHome: GridPoint,
) {
    moveHomeLevel(oldHome, newHome)
    val herd = state.herds.remove(oldHome) ?: return
    state.herds[newHome] = herd
    state.eggs = state.eggs.map { if (it.coop == oldHome) it.copy(coop = newHome) else it }
    val placed = state.building(newHome) ?: return
    val front = placed.building.frontTile(placed.origin)
    val landing =
        if (com.hunter.stockfarmer.core.model.GameState
                .contains(front) &&
            state.building(front) == null
        ) {
            front
        } else {
            spawnTile(placed)
        }
    landing ?: return
    state.herds[newHome] = herd.copy(animals = herd.animals.map { it.copy(position = landing) })
}

/** The tool takes what the animals on a tile have ready: milk, goat's milk and fertilizer with the bucket, wool with the scythe. */
internal fun GameEngine.collectProduce(
    point: GridPoint,
    tool: HarvestTool,
    now: Instant,
): List<GameEvent> {
    val events = mutableListOf<GameEvent>()
    val levelBefore = state.level
    for ((home, herd) in state.herds.entries.toList()) {
        for (animal in herd.animals) {
            if (animal.position != point || isInside(AnimalRef.Livestock(home, animal.id), now)) continue
            val produce = animal.readyProduce(now) ?: continue
            if (produce.second != tool) continue
            if (!hasRoom(1, tool)) {
                return events + levelUpEvents(levelBefore) +
                    GameEvent.Rejected(Rejection.StorageFull)
            }
            updateLivestock(
                home,
                animal.id,
            ) { it.copy(readyAt = now.advancedBy(animal.species.workInterval), lastWorkedAt = now) }
            noteWorked(animal.variant)
            events += gatherWithBonus(produce.first, point, tool, earnXPForProducing(produce.first))
        }
    }
    return events + levelUpEvents(levelBefore)
}

/** What is ready to be collected from the animal standing on a tile, if anything. */
fun GameEngine.readyProduce(
    point: GridPoint,
    now: Instant,
): Pair<StorageItem, HarvestTool>? {
    for ((home, herd) in state.herds) {
        for (animal in herd.animals) {
            if (animal.position == point && !isInside(AnimalRef.Livestock(home, animal.id), now)) {
                animal.readyProduce(now)?.let { return it }
            }
        }
    }
    return null
}
