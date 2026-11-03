package com.hunter.stockfarmer.farm

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.rules.GrowthConditions

/**
 * The watered, fertilized and guarded tiles, worked out again only when something they come from changes. Finding the
 * watered farmland scans every water tile and its neighbors, which is too much to repeat for each of the two readers a frame.
 *
 * What they come from is read as a hash and a size each frame, which walks the sets without allocating.
 */
internal class GrowthConditionsCache {
    private val fingerprint = IntArray(SOURCES * 2)
    private val current = IntArray(SOURCES * 2)
    private var conditions: GrowthConditions? = null

    fun of(state: GameState): GrowthConditions {
        read(state, current)
        conditions?.let { if (current.contentEquals(fingerprint)) return it }
        current.copyInto(fingerprint)
        return GrowthConditions(state).also { conditions = it }
    }

    private fun read(
        state: GameState,
        into: IntArray,
    ) {
        into[0] = state.farmland.hashCode()
        into[1] = state.farmland.size
        into[2] = state.water.keys.hashCode()
        into[3] = state.water.size
        into[4] = state.decorations.hashCode()
        into[5] = state.decorations.size
        into[6] = state.lingeringWater.keys.hashCode()
        into[7] = state.lingeringWater.size
        into[8] = state.fertilized.hashCode()
        into[9] = state.fertilized.size
    }

    private companion object {
        const val SOURCES = 5
    }
}
