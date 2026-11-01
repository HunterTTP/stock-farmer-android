package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import java.time.Instant

/** One thing standing on the map, drawn when its turn comes in the back to front order. */
class Standing(
    /** The map row edge the thing's ground ends at, plus a small amount to order things sharing a row. */
    val bottomEdge: Float,
    val draw: () -> Unit,
)

/** A layer of the farm (animals, the tractor, weather props) that adds what it has to the back to front pass. */
interface StandingSource {
    val work: PerformanceWork get() = PerformanceWork.structure

    fun collect(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        out: MutableList<Standing>,
    )
}
