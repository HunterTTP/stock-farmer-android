package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.beginPlacing
import com.hunter.stockfarmer.core.rules.beginPlacingFruitTree
import com.hunter.stockfarmer.core.rules.beginPlacingSelectedDecoration
import com.hunter.stockfarmer.core.rules.buildingPlacement
import com.hunter.stockfarmer.core.rules.cancelPlacement
import com.hunter.stockfarmer.core.rules.confirmPlacement
import com.hunter.stockfarmer.core.rules.isFruitTreeSelected
import com.hunter.stockfarmer.core.rules.isPlacing
import com.hunter.stockfarmer.core.rules.isSprinklerSelected
import com.hunter.stockfarmer.core.rules.movePlacement
import com.hunter.stockfarmer.core.rules.nudgePlacement
import com.hunter.stockfarmer.core.rules.placementRejection
import java.time.Instant

/** What a touch on a tile does: carry a thing being placed there, start placing the selected one, or use the tool. */
fun GameSession.touch(
    point: GridPoint,
    now: Instant = Instant.now(),
): List<GameEvent> =
    act { engine ->
        when {
            engine.isPlacing -> {
                engine.movePlacement(point)
                emptyList()
            }

            engine.startsPlacing(point) -> {
                emptyList()
            }

            else -> {
                engine.useTool(point, now)
            }
        }
    }.let { publish(it) }

private fun GameEngine.startsPlacing(point: GridPoint): Boolean =
    when {
        state.mode == PlayMode.build -> {
            beginPlacing(point)
            isPlacing
        }

        isFruitTreeSelected -> {
            beginPlacingFruitTree(point)
            isPlacing
        }

        state.mode == PlayMode.decorate && isSprinklerSelected -> {
            beginPlacingSelectedDecoration(point)
            isPlacing
        }

        else -> {
            false
        }
    }

/** Puts down whatever is being placed, or says why it cannot be. */
fun GameSession.confirmPlacement(now: Instant = Instant.now()): List<GameEvent> =
    publish(act { it.confirmPlacement(now) })

fun GameSession.cancelPlacement() = act { it.cancelPlacement() }

fun GameSession.nudgePlacement(
    rows: Int,
    cols: Int,
) = act { it.nudgePlacement(rows, cols) }

/** Where the thing being placed would go, and whether it can go there; null when nothing is being placed. */
fun GameSession.preview(now: Instant = Instant.now()): PlacementPreview? =
    act { engine ->
        val footprint =
            engine.buildingPlacement?.footprint
                ?: engine.fruitPlacement?.footprint
                ?: engine.objectPlacement?.footprint
        footprint?.let {
            PlacementPreview(
                it,
                engine.placementRejection(now) == null,
                engine.buildingPlacement?.building,
            )
        }
    }

/** The tiles a thing being placed would cover, and the building to show over them when it is one. */
class PlacementPreview(
    val footprint: List<GridPoint>,
    val canPlace: Boolean,
    val building: Building?,
)
