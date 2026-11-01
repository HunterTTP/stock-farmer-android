package com.hunter.stockfarmer.core.presentation

import com.hunter.stockfarmer.core.model.GameState

/**
 * Which of the renderer's derived state and structural layers a change of game state invalidates. [before] is a copy of
 * the state the farm last drew, or null the first time.
 */
class FarmRenderChanges(
    before: GameState?,
    state: GameState,
) {
    val terrain: Boolean
    val growth: Boolean
    val buildings: Boolean
    val nightPulse: Boolean
    val fireflies: Boolean

    init {
        fun <T> changed(property: (GameState) -> T): Boolean = before == null || property(before) != property(state)
        terrain =
            changed { it.groundCover } || changed { it.farmland } || changed { it.water } ||
            changed { it.decorations } || changed { it.lingeringWater }
        growth = terrain || changed { it.fertilized }
        buildings =
            changed { it.buildings } || changed { it.buildingStyles } ||
            before?.tractorRun?.home != state.tractorRun?.home
        nightPulse = terrain || buildings
        fireflies =
            buildings || changed { it.trees } || changed { it.rocks } || changed { it.decorations } ||
            changed { it.plots } || changed { it.releasedButterflies }
    }
}
