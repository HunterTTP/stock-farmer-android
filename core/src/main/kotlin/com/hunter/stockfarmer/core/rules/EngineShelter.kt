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
): GridPoint? {
    val front = shelterTile(animal)
    if (front == null || current == front) return null
    for (margin in listOf(SHELTER_SEARCH_MARGIN, GameState.GRID_SIZE)) {
        val rows = (min(current.row, front.row) - margin)..(max(current.row, front.row) + margin)
        val cols = (min(current.col, front.col) - margin)..(max(current.col, front.col) + margin)
        val distance = distances(front, current, rows, cols) ?: continue
        val here = distance[current] ?: continue
        val nearer = current.neighbors.filter { (distance[it] ?: Int.MAX_VALUE) < here && canStand(it, animal) }
        return nearer.minByOrNull { distance[it] ?: 0 }?.let { GridPoint(it.row - current.row, it.col - current.col) }
    }
    return null
}

/**
 * How many steps over open ground each tile is from [start], within the rows and columns given, worked out only as far as
 * [target], which is all that is needed to tell which of its neighbors are nearer; null if there is no way there inside them.
 */
private fun GameEngine.distances(
    start: GridPoint,
    target: GridPoint,
    rows: IntRange,
    cols: IntRange,
): Map<GridPoint, Int>? {
    val distance = mutableMapOf(start to 0)
    val queue = mutableListOf(start)
    var index = 0
    while (index < queue.size && target !in distance) {
        val tile = queue[index]
        index += 1
        val next = (distance[tile] ?: 0) + 1
        for (neighbor in tile.neighbors) {
            if (neighbor.row in rows && neighbor.col in cols && neighbor !in distance && isOpenGround(neighbor)) {
                distance[neighbor] = next
                queue += neighbor
            }
        }
    }
    return if (target in distance) distance else null
}
