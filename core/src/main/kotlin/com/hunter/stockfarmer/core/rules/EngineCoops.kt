package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.PlacedBuilding
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.randomElement
import java.time.Instant

fun GameEngine.chooseChickVariant(
    id: String?,
    home: GridPoint,
): List<GameEvent> {
    if (state.coops[home] == null || (id != null && ChickenVariant.available.none { it.id == id })) {
        return reject(Rejection.NothingHere)
    }
    state.coops[home] = state.coops.getValue(home).copy(nextVariantID = id)
    if (id != null) countAsSeen(setOf("chicken:$id"))
    return emptyList()
}

fun GameEngine.chickens(coop: GridPoint): List<Chicken> = state.coops[coop]?.chickens ?: emptyList()

fun GameEngine.chicken(
    id: Int,
    coop: GridPoint,
): Chicken? = chickens(coop).firstOrNull { it.id == id }

internal fun GameEngine.chickenIndex(
    id: Int,
    coop: GridPoint,
): Int? = state.coops[coop]?.chickens?.indexOfFirst { it.id == id }?.takeIf { it >= 0 }

/**
 * Brings every coop up to date: chicks hatch as they come due. A new coop gets its first chick at once and the rest
 * follow over time, up to the coop's limit.
 */
fun GameEngine.updateCoops(
    now: Instant,
    using: RandomSource,
): List<GameEvent> {
    val events = mutableListOf<GameEvent>()
    for (placed in state.placedBuildings.filter { it.building.isChickenCoop }) {
        if (state.coops[placed.origin] == null) state.coops[placed.origin] = Coop.hatchingAt(now)
        events += hatchDueChicks(placed, now, using)
    }
    return events
}

private fun GameEngine.hatchDueChicks(
    placed: PlacedBuilding,
    time: Instant,
    using: RandomSource,
): List<GameEvent> {
    val events = mutableListOf<GameEvent>()
    // A full coop hatches nothing; the timer starts afresh when a chicken leaves.
    state.coops[placed.origin]?.let { coop ->
        if (coop.chickens.size >= homeCapacity(placed.origin)) {
            state.coops[placed.origin] = coop.copy(nextChickAt = time.advancedBy(Coop.HATCH_INTERVAL))
            return events
        }
    }
    while (true) {
        val coop = state.coops[placed.origin] ?: break
        if (coop.chickens.size >= homeCapacity(placed.origin) || time < coop.nextChickAt) break
        val spot = hatchTile(placed, AnimalRef.Chicken(placed.origin, -1)) ?: break
        hatchChick(placed.origin, spot, coop.nextChickAt, using)
        events += GameEvent.Hatched(spot)
    }
    return events
}

private fun GameEngine.hatchChick(
    coop: GridPoint,
    spot: GridPoint,
    due: Instant,
    using: RandomSource,
) {
    val current = state.coops[coop] ?: return
    val variant =
        current.nextVariantID?.let(ChickenVariant::named) ?: ChickenVariant.all.randomElement(using) ?: ChickenVariant.all[0]
    state.coops[coop] =
        current.copy(
            chickens = current.chickens + Chicken.hatched(current.nextChickenID, variant, Chicken.DEFAULT_NAME, spot, due),
            nextChickenID = current.nextChickenID + 1,
            nextChickAt = due.advancedBy(Coop.HATCH_INTERVAL),
        )
}

/** The player lets a chicken go: it is gone, for no money or XP, and the coop hatches a replacement in its own time. */
fun GameEngine.releaseChicken(
    id: Int,
    coop: GridPoint,
): List<GameEvent> {
    val chicken = takeChicken(id, coop) ?: return emptyList()
    return listOf(GameEvent.AnimalReleased(chicken.position))
}

/** Removes a chicken from its coop and hands it back. */
internal fun GameEngine.takeChicken(
    id: Int,
    coop: GridPoint,
): Chicken? {
    val current = state.coops[coop] ?: return null
    val chicken = current.chickens.firstOrNull { it.id == id } ?: return null
    state.coops[coop] = current.copy(chickens = current.chickens.filter { it.id != id })
    return chicken
}

/** The chickens follow their coop when it is moved, all appearing on the tile in front of it to walk out from there. */
internal fun GameEngine.moveCoop(
    oldCoop: GridPoint,
    newCoop: GridPoint,
) {
    moveHomeLevel(oldCoop, newCoop)
    val coop = state.coops.remove(oldCoop) ?: return
    state.coops[newCoop] = coop
    state.eggs = state.eggs.map { if (it.coop == oldCoop) it.copy(coop = newCoop) else it }
    val placed = state.building(newCoop) ?: return
    val front = placed.building.frontTile(placed.origin)
    val landing = if (GameState.contains(front) && state.building(front) == null) front else spawnTile(placed)
    landing ?: return
    state.coops[newCoop] = coop.copy(chickens = coop.chickens.map { it.copy(position = landing) })
}
