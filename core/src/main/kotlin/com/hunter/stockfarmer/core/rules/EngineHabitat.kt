@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GridPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The two kinds of animal that live on the water. */
enum class Waterfolk { duck, fish }

/** Where the ducks and fish of the map are now, which decides where a newcomer may appear. */
class WaterOccupants {
    private val duckTiles = mutableSetOf<GridPoint>()
    private val fishTiles = mutableSetOf<GridPoint>()

    val ducks: Set<GridPoint> get() = duckTiles
    val fish: Set<GridPoint> get() = fishTiles

    fun tiles(kind: Waterfolk): Set<GridPoint> = if (kind == Waterfolk.duck) duckTiles else fishTiles

    fun remove(
        kind: Waterfolk,
        tile: GridPoint,
    ) {
        if (kind == Waterfolk.duck) duckTiles.remove(tile) else fishTiles.remove(tile)
    }

    fun add(
        kind: Waterfolk,
        tile: GridPoint,
    ) {
        if (kind == Waterfolk.duck) duckTiles.add(tile) else fishTiles.add(tile)
    }

    fun isAnyone(
        tile: GridPoint,
        distance: Int,
        kind: Waterfolk? = null,
    ): Boolean {
        val others = kind?.let(::tiles)
        for (rowOffset in -distance..distance) {
            for (colOffset in -distance..distance) {
                val neighbor = GridPoint(tile.row + rowOffset, tile.col + colOffset)
                val found = if (others != null) neighbor in others else neighbor in duckTiles || neighbor in fishTiles
                if (found) return true
            }
        }
        return false
    }
}

/**
 * Where ducks and fish may appear, and how many there may be. This only decides where newcomers appear: once on the
 * water, animals swim wherever they please, and none is ever moved or taken away for crowding.
 *
 * No other animal of the newcomer's kind may be within [APPEARANCE_SPACING] tiles of it in any direction, and none of the
 * other kind within the smaller distance, so that a narrow stream still has room for both.
 */
private const val APPEARANCE_SPACING = 2
private const val OTHER_KIND_SPACING = 1

/** No five by five square of tiles may hold more than this many ducks, or this many fish. */
private const val WINDOW_SIDE = 5
private const val MOST_OF_A_KIND_IN_A_WINDOW = 2

/** A body of water has room for one more of each kind for every this many interior tiles. */
private const val INTERIOR_TILES_PER_ANIMAL = 20

/** The whole map has room for one more of each kind for every this many water tiles, between these two bounds. */
private const val WATER_TILES_PER_ANIMAL = 24
private const val FEWEST_OF_A_KIND_ON_THE_MAP = 2
internal const val MOST_OF_A_KIND_ON_THE_MAP = 15

/** How many of each kind a body of water has room for, by its interior. */
internal fun GameEngine.animalLimit(body: Int): Int =
    (
        state.waterLayout.bodies[body]
            .interior.size + INTERIOR_TILES_PER_ANIMAL - 1
    ) / INTERIOR_TILES_PER_ANIMAL

/** How many of each kind the whole map has room for, by how much water it has. */
internal val GameEngine.animalLimitOnTheMap: Int
    get() = min(MOST_OF_A_KIND_ON_THE_MAP, max(FEWEST_OF_A_KIND_ON_THE_MAP, state.water.size / WATER_TILES_PER_ANIMAL))

internal val GameEngine.waterOccupants: WaterOccupants
    get() {
        val occupants = WaterOccupants()
        for (origin in pondHerdOrigins) {
            for (duck in state.herds[origin]?.animals ?: emptyList()) occupants.add(Waterfolk.duck, duck.position)
        }
        for (pond in state.fishPonds.values) pond.fish?.let { occupants.add(Waterfolk.fish, it.position) }
        return occupants
    }

/** How many ducks are swimming in a body of water. */
internal fun GameEngine.duckCount(body: Int): Int {
    val layout = state.waterLayout
    return pondHerdOrigins.sumOf { origin ->
        state.herds[origin]?.animals?.count { layout.bodyIndexByTile[it.position] == body } ?: 0
    }
}

/**
 * The tiles of a body of water where one more of a kind may appear: with water on every side, none of its kind within two
 * tiles and none of the other kind adjacent, and no more than two of its kind in any five by five square once it is there.
 */
internal fun GameEngine.openWaterTiles(
    kind: Waterfolk,
    body: Int,
    occupants: WaterOccupants,
): List<GridPoint> {
    val sameKind = occupants.tiles(kind)
    val other = if (kind == Waterfolk.duck) Waterfolk.fish else Waterfolk.duck
    return state.waterLayout.bodies[body].interior.filter { tile ->
        state.decoration(tile) == null &&
            !occupants.isAnyone(tile, APPEARANCE_SPACING, kind) &&
            !occupants.isAnyone(tile, OTHER_KIND_SPACING, other) &&
            !wouldCrowdAWindow(sameKind, tile)
    }
}

private fun wouldCrowdAWindow(
    sameKind: Set<GridPoint>,
    tile: GridPoint,
): Boolean {
    val reach = WINDOW_SIDE - 1
    val nearby = sameKind.filter { abs(it.row - tile.row) <= reach && abs(it.col - tile.col) <= reach }
    if (nearby.size < MOST_OF_A_KIND_IN_A_WINDOW) return false
    for (top in (tile.row - reach)..tile.row) {
        for (left in (tile.col - reach)..tile.col) {
            val inWindow =
                nearby.count {
                    it.row in top until top + WINDOW_SIDE &&
                        it.col in left until left + WINDOW_SIDE
                }
            if (inWindow >= MOST_OF_A_KIND_IN_A_WINDOW) return true
        }
    }
    return false
}
