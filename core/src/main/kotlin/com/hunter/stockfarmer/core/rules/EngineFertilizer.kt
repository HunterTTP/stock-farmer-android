package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Fertilizer
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.util.advancedBy
import java.time.Instant

fun GameEngine.selectFertilizer() {
    state.mode = PlayMode.fertilize
    state.fertilizesWithSprinklers = false
    state.placesFertilizerFromStorage = false
    placement = null
}

/** Picks up the Fertilize tool holding fertilizer from storage, which uses one up for each tile instead of costing money. */
internal fun GameEngine.equipFertilizer(): Boolean {
    if ((state.storage[StorageItem.Fertilizer.id] ?: 0) <= 0) return false
    selectFertilizer()
    state.placesFertilizerFromStorage = true
    return true
}

/**
 * Fertilizes one farmland tile that has no crop (a tap on a crop is handled before it gets here); the fertilizer is used
 * up along with the crop planted on it.
 */
internal fun GameEngine.tapFertilizer(
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    if (point !in state.farmland) return reject(Rejection.NeedFarmland)
    if (point in state.plots) return reject(Rejection.AlreadyPlanted)
    if (state.isBlockedByDecoration(point)) return reject(Rejection.StructureHere)
    if (point in state.fertilized) return reject(Rejection.AlreadyFertilized)
    if (!state.placesFertilizerFromStorage) {
        spend(Fertilizer.price)?.let { return reject(it) }
        state.fertilized.add(point)
        return listOf(GameEvent.Fertilized(point, Fertilizer.price))
    }
    if ((state.storage[StorageItem.Fertilizer.id] ?: 0) <= 0) {
        val rejection = Rejection.NoneInStorage(StorageItem.Fertilizer.name)
        return if (isQuietAboutStorage(rejection, now)) emptyList() else reject(rejection)
    }
    removeFromStorage(StorageItem.Fertilizer.id, 1)
    storagePlacementQuietUntil = now.advancedBy(STORAGE_PLACEMENT_QUIET)
    state.fertilized.add(point)
    return listOf(GameEvent.Fertilized(point, 0.0))
}

internal fun reject(rejection: Rejection): List<GameEvent> = listOf(GameEvent.Rejected(rejection))
