package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.ObjectPlacement
import com.hunter.stockfarmer.core.rules.buildingPlacement

/**
 * Where the things being moved stand until the move is confirmed. The picture of the thing follows the finger, so the one at its
 * old spot is left out of the farm.
 */
data class MovingOrigins(
    val building: GridPoint? = null,
    val fruitTree: GridPoint? = null,
    val decoration: GridPoint? = null,
    val tree: GridPoint? = null,
) {
    companion object {
        val NONE = MovingOrigins()

        fun of(engine: GameEngine): MovingOrigins {
            val moving = engine.objectPlacement
            return MovingOrigins(
                building = engine.buildingPlacement?.replacing?.origin,
                fruitTree = engine.fruitPlacement?.replacing?.origin,
                decoration = moving?.from?.takeIf { moving.obj is ObjectPlacement.Object.OfDecoration },
                tree = moving?.from?.takeIf { moving.obj is ObjectPlacement.Object.OfTree },
            )
        }
    }
}
