package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.util.advancedBy
import java.time.Instant

val GameEngine.selectedFenceColor: FenceColor get() = FenceColor.named(state.selectedFenceColorID) ?: FenceColor.starter

/** Chooses a fence color and switches to placing fences. */
fun GameEngine.select(color: FenceColor) {
    state.selectedFenceColorID = color.id
    state.selectedDecorationID = null
    state.placesDecorationsFromStorage = false
    state.heldItemID = null
    state.lastDecorationID = null
    state.mode = PlayMode.decorate
}

/** Takes a fence away and refunds its price. */
fun GameEngine.destroyFence(point: GridPoint): List<GameEvent> {
    if (state.fences.remove(point) == null) return emptyList()
    credit(FenceColor.price)
    return listOf(GameEvent.Unfenced(point, FenceColor.price))
}

/**
 * Tapping empty grass puts a fence down; tapping a fence of the same color takes it away and refunds it, and tapping one
 * of another color repaints it.
 */
internal fun GameEngine.tapFence(
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val color = selectedFenceColor
    val existing = state.fences[point]
    if (existing != null) {
        return if (existing == color.id) destroyFence(point) else placeFence(color, point, now)
    }
    val canHold =
        GameState.contains(point) && point !in state.water && point !in state.farmland &&
            !state.hasObstacle(point) && state.buildingBlocking(point) == null && animalStandingAt(point) == null
    if (!canHold) return listOf(GameEvent.Rejected(Rejection.NotEnoughSpace))
    if (point in frontTilesOfHomes) return listOf(GameEvent.Rejected(Rejection.BlocksAFront))
    cropInTheWay(point, now)?.let { return listOf(GameEvent.Rejected(it)) }
    return placeFence(color, point, now)
}

/** A fence costs its price, or one from storage when fences are being set down from storage. */
private fun GameEngine.placeFence(
    color: FenceColor,
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val fromStorage = state.placesDecorationsFromStorage
    if (fromStorage) {
        val item = StorageItem.Fence(color)
        if ((state.storage[item.id] ?: 0) <= 0) {
            val rejection = Rejection.NoneInStorage(item.name)
            return if (isQuietAboutStorage(rejection, now)) emptyList() else listOf(GameEvent.Rejected(rejection))
        }
        removeFromStorage(item.id, 1)
        storagePlacementQuietUntil = now.advancedBy(STORAGE_PLACEMENT_QUIET)
    } else {
        spend(FenceColor.price)?.let { return listOf(GameEvent.Rejected(it)) }
    }
    state.fences[point] = color.id
    return listOf(GameEvent.Fenced(point, if (fromStorage) 0.0 else FenceColor.price))
}
