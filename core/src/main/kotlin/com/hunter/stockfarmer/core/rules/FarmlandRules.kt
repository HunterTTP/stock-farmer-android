package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import kotlin.math.max

object FarmlandRules {
    const val WATER_REACH = 1

    /** From this level on, each level unlocks as many tiles as its own number; below it, twice that. */
    private const val ONE_TILE_PER_LEVEL_FROM = 20

    /** How many farmland tiles may be placed at once: the 4 starting tiles plus everything levels 2 up to this one have unlocked. */
    fun tilesUnlocked(atLevel: Int): Int =
        GameState.startingFarmland.size + (1 until max(1, atLevel)).sumOf { tilesUnlockedByReaching(it + 1) }

    /** The tiles reaching [level] unlocks on its own. */
    fun tilesUnlockedByReaching(level: Int): Int {
        if (level < 2) return 0
        return if (level >= ONE_TILE_PER_LEVEL_FROM) level else 2 * level
    }

    /** Farmland within [WATER_REACH] tiles (including diagonals) of water counts as saturated. */
    fun saturatedTiles(
        farmland: Set<GridPoint>,
        water: Set<GridPoint>,
    ): Set<GridPoint> {
        val saturated = mutableSetOf<GridPoint>()
        for (source in water) saturated += farmlandWithin(WATER_REACH, source, farmland)
        return saturated
    }

    /** The farmland within a number of tiles (including diagonals) of a tile. */
    internal fun farmlandWithin(
        reach: Int,
        source: GridPoint,
        farmland: Set<GridPoint>,
    ): Set<GridPoint> {
        val near = mutableSetOf<GridPoint>()
        for (rowOffset in -reach..reach) {
            for (colOffset in -reach..reach) {
                val neighbor = GridPoint(source.row + rowOffset, source.col + colOffset)
                if (neighbor in farmland) near += neighbor
            }
        }
        return near
    }
}

val GameState.farmlandLimit: Int get() = FarmlandRules.tilesUnlocked(level)

/** The farmland near water, a sprinkler or a well. */
val GameState.saturatedFarmland: Set<GridPoint> get() = farmlandNearWater
