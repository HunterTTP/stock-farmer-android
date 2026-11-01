package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.bottomRow
import com.hunter.stockfarmer.core.util.advancedBy
import java.time.Instant
import kotlin.math.max

/**
 * A decoration or a tree on its way to the map: one standing that is picked up and carried to a new spot, or a new
 * sprinkler being set down, which takes up its spot when confirmed.
 */
data class ObjectPlacement(
    val obj: Object,
    /** Where it stands until the move is confirmed; null for a new one that has no spot yet. */
    val from: GridPoint?,
    val origin: GridPoint,
) {
    sealed interface Object {
        data class OfDecoration(
            val placed: PlacedDecoration,
        ) : Object

        data class OfTree(
            val placed: PlacedTree,
        ) : Object
    }

    val footprint: List<GridPoint> get() = footprint(origin)

    val isNew: Boolean get() = from == null

    fun footprint(at: GridPoint): List<GridPoint> =
        when (obj) {
            is Object.OfDecoration -> obj.placed.decoration.footprint(at)
            is Object.OfTree -> obj.placed.tree.footprint(at)
        }
}

/** Starts picking up the decoration standing on a tile, to put it somewhere else; it begins where it stands. */
fun GameEngine.beginMovingDecoration(point: GridPoint) {
    val site = state.decoration(point) ?: return
    beginMoving(ObjectPlacement.Object.OfDecoration(site.second), site.first)
}

/** Starts picking up the tree standing on a tile, to put it somewhere else; it begins where it stands and keeps its growth. */
fun GameEngine.beginMovingTree(point: GridPoint) {
    val site = state.tree(point) ?: return
    if (site.second.isNaturallyGenerated) return
    beginMoving(ObjectPlacement.Object.OfTree(site.second), site.first)
}

/** Whether the selected decoration is set down with arrows, OK and Cancel like a building: a sprinkler. */
val GameEngine.isSprinklerSelected: Boolean get() =
    state.mode == PlayMode.decorate &&
        selectedDecoration?.isSprinkler == true

/** Why a new sprinkler can't be started right now (not unlocked, or not enough money), or null if it can. */
val GameEngine.cannotStartPlacingSelectedDecoration: Rejection?
    get() {
        val decoration = selectedDecoration ?: return null
        if (!isUnlocked(decoration)) return Rejection.NeedLevel(decoration.unlockLevel)
        return unaffordableSelectedDecoration(decoration, selectedDecorationSize)
    }

/** Starts setting the selected sprinkler down with its tile on a tile. */
fun GameEngine.beginPlacingSelectedDecoration(aroundTile: GridPoint) {
    val decoration = selectedDecoration
    if (!isSprinklerSelected || decoration == null || !isUnlocked(decoration)) return
    placement = null
    fruitPlacement = null
    val origin = decoration.originSettingBottomLeftOn(aroundTile)
    val placed = PlacedDecoration.of(decoration, selectedDecorationSize)
    objectPlacement = ObjectPlacement(ObjectPlacement.Object.OfDecoration(placed), null, origin)
    moveObjectPlacement(origin)
}

private fun GameEngine.beginMoving(
    obj: ObjectPlacement.Object,
    origin: GridPoint,
) {
    placement = null
    fruitPlacement = null
    objectPlacement = ObjectPlacement(obj, origin, origin)
}

/** Puts the carried object's corner at [origin], shifted to stay inside the map. */
internal fun GameEngine.moveObjectPlacement(origin: GridPoint) {
    val current = objectPlacement ?: return
    val tiles = current.footprint(origin).bottomRow
    val rows = tiles.map { it.row }
    val cols = tiles.map { it.col }
    val last = GameState.GRID_SIZE - 1
    val rowShift = max(0, -(rows.minOrNull() ?: 0)) - max(0, (rows.maxOrNull() ?: 0) - last)
    val colShift = max(0, -(cols.minOrNull() ?: 0)) - max(0, (cols.maxOrNull() ?: 0) - last)
    objectPlacement = current.copy(origin = GridPoint(origin.row + rowShift, origin.col + colShift))
}

internal fun GameEngine.nudgeObjectPlacement(
    rows: Int,
    cols: Int,
) {
    val current = objectPlacement ?: return
    moveObjectPlacement(GridPoint(current.origin.row + rows, current.origin.col + cols))
}

/** Why the carried object can't go down where it is now, or null if it can: its own old ground counts as free. */
internal fun GameEngine.objectRejection(moving: ObjectPlacement): Rejection? {
    val obj = moving.obj
    if (moving.isNew && obj is ObjectPlacement.Object.OfDecoration) {
        if (!isUnlocked(obj.placed.decoration)) return Rejection.NeedLevel(obj.placed.decoration.unlockLevel)
        unaffordableSelectedDecoration(obj.placed.decoration, obj.placed.size)?.let { return it }
    }
    if (standsOnTheSameTopLeft(moving)) return Rejection.NotEnoughSpace
    val own = moving.from?.let { moving.footprint(it).bottomRow.toSet() } ?: emptySet()
    val isFree: (GridPoint) -> Boolean = { tile ->
        if (obj is ObjectPlacement.Object.OfDecoration &&
            obj.placed.decoration.isSprinkler
        ) {
            isFreeForSprinkler(tile)
        } else {
            isFreeForTree(tile)
        }
    }
    if (!moving.footprint.bottomRow.all {
            GameState.contains(it) && (it in own || isFree(it))
        }
    ) {
        return Rejection.NotEnoughSpace
    }
    return if (blocksAHomesFront(moving.footprint)) Rejection.BlocksAFront else null
}

/**
 * Decorations are kept under their top-left tile, so one set down with another's top-left would replace it, however much
 * of its picture rises above the ground it takes up.
 */
private fun GameEngine.standsOnTheSameTopLeft(moving: ObjectPlacement): Boolean {
    if (moving.obj !is ObjectPlacement.Object.OfDecoration || moving.origin == moving.from) return false
    return moving.origin in state.decorations
}

internal fun GameEngine.confirmObjectPlacement(
    moving: ObjectPlacement,
    now: Instant,
): List<GameEvent> {
    objectRejection(moving)?.let { return reject(it) }
    try {
        when (val obj = moving.obj) {
            is ObjectPlacement.Object.OfDecoration -> {
                val from = moving.from
                if (from == null) {
                    val paid = if (state.placesDecorationsFromStorage) 0.0 else obj.placed.decoration.cost
                    payForSelectedDecoration()
                    if (state.placesDecorationsFromStorage) {
                        storagePlacementQuietUntil =
                            now.advancedBy(STORAGE_PLACEMENT_QUIET)
                    }
                    notePlaced(obj.placed.decorationID)
                    state.putDecoration(moving.origin, startingToWater(obj.placed, now))
                    return listOf(GameEvent.Decorated(moving.origin, paid))
                }
                dryOut(obj.placed, from, now)
                state.removeDecoration(from)
                state.putDecoration(moving.origin, startingToWater(obj.placed, now))
            }

            is ObjectPlacement.Object.OfTree -> {
                moving.from?.let { state.removeTree(it) }
                state.putTree(moving.origin, obj.placed)
            }
        }
        return listOf(GameEvent.Moved(moving.origin))
    } finally {
        objectPlacement = null
    }
}

/** A sprinkler set down, or moved, sprays at once and waters its land tile by tile through that first spray. */
internal fun startingToWater(
    placed: PlacedDecoration,
    now: Instant,
): PlacedDecoration =
    PlacedDecoration.of(
        placed.decoration,
        placed.size,
        wateringStartsAt =
            if (placed.decoration.waterReach ==
                null
            ) {
                null
            } else {
                now.advancedBy(com.hunter.stockfarmer.core.catalog.Decoration.SPRAY_DURATION)
            },
        placedAt = now,
    )

/**
 * Destroys the carried object where it stands: a decoration gives back 80% of its price unless that would leave storage short,
 * a tree gives nothing (the Axe is what turns one into logs).
 */
fun GameEngine.destroyPlacedObject(now: Instant): List<GameEvent> {
    val moving = objectPlacement ?: return emptyList()
    val from = moving.from ?: return emptyList()
    return when (moving.obj) {
        is ObjectPlacement.Object.OfDecoration -> {
            val events = destroyDecoration(from, now)
            if (!events.containsRejection) objectPlacement = null
            events
        }

        is ObjectPlacement.Object.OfTree -> {
            state.removeTree(from)
            objectPlacement = null
            listOf(GameEvent.Undecorated(from, 0.0))
        }
    }
}
