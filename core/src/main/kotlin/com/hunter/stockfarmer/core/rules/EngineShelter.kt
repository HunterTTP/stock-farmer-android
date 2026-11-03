package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/** Animals go in for the night once it is this dark, and for the rain once it is pouring, until it has stopped. */
internal const val SHELTER_DARKNESS = 0.5

/**
 * How far beyond the animal and its home the first search for a way home looks, in tiles. When the rain comes every animal
 * on the farm looks for its way at once, so the search stays near the straight way home, which nearly always has one, and
 * only looks over the whole map when it finds none there, such as round a long fence.
 */
private const val SHELTER_SEARCH_MARGIN = 8

/** Whether it is dark or raining enough that the animals have gone to their homes. */
fun GameEngine.needsShelter(now: Instant): Boolean =
    DayNightCycle.darkness(now, state.clockOffset) >= SHELTER_DARKNESS || Weather.isPouring(now, state.clockOffset)

/** The tile in front of the animal's home, where it goes in and comes out; null for the ducks of a pond, which have no building. */
fun GameEngine.shelterTile(animal: AnimalRef): GridPoint? =
    state.building(animal.home)?.let { if (it.building.needsFrontTile) it.building.frontTile(it.origin) else null }

/** An animal standing at the front of its home while the animals are sheltering is inside, out of sight. */
fun GameEngine.isInside(
    animal: AnimalRef,
    now: Instant,
): Boolean = needsShelter(now) && shelterTile(animal)?.let { it == position(animal) } == true

/**
 * Which way to step to get nearer the front of the animal's home along open ground, if there is a free step that does;
 * null at the front already, or when it is boxed in.
 */
internal fun GameEngine.directionToShelter(
    animal: AnimalRef,
    current: GridPoint,
    walk: AnimalWalk,
): GridPoint? {
    val front = shelterTile(animal)
    if (front == null || current == front) return null
    val waterfowl = isWaterfowl(animal)
    for (margin in listOf(SHELTER_SEARCH_MARGIN, GameState.GRID_SIZE)) {
        val rows = (min(current.row, front.row) - margin)..(max(current.row, front.row) + margin)
        val cols = (min(current.col, front.col) - margin)..(max(current.col, front.col) + margin)
        if (!walk.searchFromHome(front, current, rows, cols)) continue
        val here = walk.stepsFromHome(current)
        val nearer = current.neighbors.filter { walk.stepsFromHome(it) < here && walk.canStand(it, waterfowl) }
        val nearest = nearer.minByOrNull { walk.stepsFromHome(it) } ?: return null
        return GridPoint(nearest.row - current.row, nearest.col - current.col)
    }
    return null
}
