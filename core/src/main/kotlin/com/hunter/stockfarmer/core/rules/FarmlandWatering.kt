package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.DecorationEffect
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint

/** The farmland near water: the usual ponds, and also the sprinklers, which water a tile at a time through their first spray. */
val GameState.farmlandNearWater: Set<GridPoint>
    get() {
        val near = FarmlandRules.saturatedTiles(farmland, water.keys).toMutableSet()
        for ((origin, placed) in decorations) near += wateredFarmland(placed, origin)
        return near + lingeringWater.keys.filter { it in farmland }
    }

/** The farmland a decoration that waters the land saturates now: all it reaches, or what its first spray has got to so far. */
fun GameState.wateredFarmland(
    placed: PlacedDecoration,
    origin: GridPoint,
): Set<GridPoint> {
    val reach = placed.decoration.waterReach ?: return emptySet()
    if (placed.wateringStartsAt != null) return placed.wateredTiles.intersect(farmland)
    return placed.decoration
        .footprint(origin)
        .flatMap { FarmlandRules.farmlandWithin(reach, it, farmland) }
        .toSet()
}

/** The farmland near a scarecrow. */
val GameState.guardedFarmland: Set<GridPoint>
    get() {
        val guarded = mutableSetOf<GridPoint>()
        for ((origin, placed) in decorations) {
            val reach = (placed.decoration.effect as? DecorationEffect.Guards)?.reach ?: continue
            for (tile in placed.decoration.footprint(origin)) {
                guarded += FarmlandRules.farmlandWithin(reach, tile, farmland)
            }
        }
        return guarded
    }
