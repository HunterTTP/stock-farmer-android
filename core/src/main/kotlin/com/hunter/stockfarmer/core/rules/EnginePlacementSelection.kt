package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode

/** Whether what is selected is set down with arrows, OK and Cancel: a building, a fruit tree or a sprinkler. */
val GameEngine.selectionPlaces: Boolean get() =
    state.mode == PlayMode.build || isFruitTreeSelected || isSprinklerSelected

/** Why the selection can't start being placed right now, or null when it can (or when it places nothing). */
val GameEngine.cannotStartPlacingSelection: Rejection?
    get() =
        when {
            state.mode == PlayMode.build -> cannotStartPlacingSelectedBuilding
            isFruitTreeSelected -> cannotStartPlacingSelectedFruitTree
            isSprinklerSelected -> cannotStartPlacingSelectedDecoration
            else -> null
        }

/** Starts placing the selection around [point]; true when something is being placed afterwards. */
fun GameEngine.beginPlacingSelection(point: GridPoint): Boolean {
    when {
        state.mode == PlayMode.build -> beginPlacing(point)
        isFruitTreeSelected -> beginPlacingFruitTree(point)
        isSprinklerSelected -> beginPlacingSelectedDecoration(point)
        else -> return false
    }
    return isPlacing
}

/** Where the thing being placed has its origin; null when nothing is being placed. */
val GameEngine.currentPlacementOrigin: GridPoint?
    get() = buildingPlacement?.origin ?: fruitPlacement?.origin ?: objectPlacement?.origin

/** The tiles the thing being placed covers; null when nothing is being placed. */
val GameEngine.currentPlacementFootprint: List<GridPoint>?
    get() = buildingPlacement?.footprint ?: fruitPlacement?.footprint ?: objectPlacement?.footprint
