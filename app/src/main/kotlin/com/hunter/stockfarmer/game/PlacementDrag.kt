package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.feedback.HapticCue
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.buildingPlacement
import com.hunter.stockfarmer.core.rules.isPlacing
import com.hunter.stockfarmer.core.rules.movePlacement
import java.time.Instant

/** A drag grabs only the placement's footprint, preserving where on it the finger landed. */
class PlacementDrag(
    private val session: GameSession,
) {
    private var offset: GridPoint? = null

    fun begin(
        tile: GridPoint?,
        now: Instant = Instant.now(),
    ): Boolean {
        offset = null
        if (!session.act { it.isPlacing }) return false
        val footprint = session.preview(now)?.footprint ?: return false
        if (tile == null || tile !in footprint) return false
        val origin = GridPoint(footprint.minOf { it.row }, footprint.minOf { it.col })
        offset = GridPoint(tile.row - origin.row, tile.col - origin.col)
        return true
    }

    fun move(tile: GridPoint?): Boolean {
        val grab = offset ?: return false
        if (tile != null) {
            session.act { engine ->
                val before =
                    engine.buildingPlacement?.origin ?: engine.fruitPlacement?.origin ?: engine.objectPlacement?.origin
                engine.movePlacement(GridPoint(tile.row - grab.row, tile.col - grab.col))
                val after =
                    engine.buildingPlacement?.origin ?: engine.fruitPlacement?.origin ?: engine.objectPlacement?.origin
                if (before != after) session.haptic(HapticCue.Light)
            }
        }
        return true
    }

    fun end() {
        offset = null
    }
}
