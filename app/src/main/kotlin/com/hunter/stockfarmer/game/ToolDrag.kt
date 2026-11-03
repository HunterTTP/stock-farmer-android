package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.ToolStroke
import com.hunter.stockfarmer.core.rules.beginPlacingSelection
import com.hunter.stockfarmer.core.rules.cannotStartPlacingSelection
import com.hunter.stockfarmer.core.rules.containsRejection
import com.hunter.stockfarmer.core.rules.firstRejection
import com.hunter.stockfarmer.core.rules.isChopping
import com.hunter.stockfarmer.core.rules.isPlacing
import com.hunter.stockfarmer.core.rules.missingHarvestToolRejection
import com.hunter.stockfarmer.core.rules.movePlacement
import com.hunter.stockfarmer.core.rules.placementRejection
import com.hunter.stockfarmer.core.rules.selectionPlaces
import com.hunter.stockfarmer.core.rules.toolFootprint
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

/**
 * The tool dragged over the farm. Buildings, fruit trees and sprinklers are carried to where the finger is and wait for
 * the player to place them; any other tool is used on every tile the finger passes over.
 */
class ToolDrag(
    private val session: GameSession,
) {
    private var stroke = ToolStroke()
    private var last: GridPoint? = null
    private var lastRejection: Rejection? = null

    /** The finger is over [tile], or over nothing when it is null. */
    fun move(
        tile: GridPoint?,
        now: Instant = Instant.now(),
    ) {
        session.toolStrokes.value = true
        if (tile == null || session.casts.value != null) {
            session.view.toolPreview = null
            return
        }
        val events =
            session.act { engine ->
                if (engine.selectionPlaces) {
                    carry(engine, tile)
                } else {
                    use(engine, tile, now)
                }
            }
        session.publish(events)
        last = tile
    }

    /** Why the tool can not be used where the finger rests, as a direct tap would say: null when it can. */
    fun explain(now: Instant = Instant.now()): Rejection? =
        session.act { engine -> if (engine.selectionPlaces) engine.placementRejection(now) else lastRejection }

    /** The finger lifted: a carried thing stays put awaiting the player's OK, and the stroke is over. */
    fun end() {
        session.act { stroke.end(it) }
        session.toolStrokes.value = false
        stroke = ToolStroke()
        last = null
        lastRejection = null
        session.view.toolPreview = null
    }

    private fun carry(
        engine: GameEngine,
        tile: GridPoint,
    ): List<GameEvent> {
        if (engine.isPlacing) {
            engine.movePlacement(tile)
            return emptyList()
        }
        val isFirst = last == null
        val rejection = engine.cannotStartPlacingSelection
        if (rejection != null) {
            return if (isFirst) listOf(GameEvent.Rejected(rejection)) else emptyList()
        }
        engine.beginPlacingSelection(tile)
        return emptyList()
    }

    private fun use(
        engine: GameEngine,
        tile: GridPoint,
        now: Instant,
    ): List<GameEvent> {
        val tiles = tilesBetween(last ?: tile, tile)
        val butterflies =
            buildList {
                for (t in tiles) {
                    val caught =
                        session.sceneFeedback.catchButterfly
                            ?.invoke(t, now)
                            .orEmpty()
                    if (caught.isNotEmpty()) {
                        addAll(caught)
                        if (caught.containsRejection) break
                    }
                }
            }
        val events = butterflies + stroke.use(engine, tiles, now)
        session.hookFish(engine, events)
        val rejection =
            events.firstRejection
                ?: if (events.isEmpty()) engine.missingHarvestToolRejection(tile, now) else null
        lastRejection = rejection
        val isAllowed = !events.containsRejection && rejection == null
        session.view.toolPreview =
            if (engine.isChopping) {
                null
            } else {
                PlacementPreview(engine.toolFootprint(tile), isAllowed, null)
            }
        return events
    }

    companion object {
        /** Every tile on the way from one tile to another, ending with the second, so a quick drag skips none. */
        fun tilesBetween(
            from: GridPoint,
            to: GridPoint,
        ): List<GridPoint> {
            val steps = max(abs(to.row - from.row), abs(to.col - from.col))
            if (steps == 0) return listOf(to)
            return (1..steps)
                .map { step ->
                    GridPoint(
                        from.row + Math.round((to.row - from.row) * step / steps.toFloat()),
                        from.col + Math.round((to.col - from.col) * step / steps.toFloat()),
                    )
                }.distinct()
        }
    }
}
