package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.CareerRecord
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.ReleasedButterfly
import com.hunter.stockfarmer.core.util.advancedBy
import java.time.Instant
import java.util.UUID

/**
 * Catches a butterfly with the Bucket: it goes into storage, which must have room, and is never sold on the spot. A wild
 * one earns XP by its wings and is counted in Career; one the player let go ([releasedID]) leaves the farm and earns
 * nothing, like a placed fish.
 */
fun GameEngine.catchButterfly(
    wings: Butterflies.Wings,
    releasedID: UUID?,
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    if (releasedID != null &&
        state.releasedButterflies.none { it.id == releasedID }
    ) {
        return reject(Rejection.NothingHere)
    }
    if (storedCount >= storageCapacity) return reject(Rejection.StorageFull)
    val levelBefore = state.level
    val xp = if (releasedID == null) LevelRules.butterflyXP(wings, levelBefore) else 0
    state.xp += xp
    if (releasedID == null) noteSeenEnvironment(CareerRecord.environmentID(wings))
    state.releasedButterflies = state.releasedButterflies.filter { it.id != releasedID }
    val item = StorageItem.Butterfly(wings)
    state.storage[item.id] = (state.storage[item.id] ?: 0) + 1
    return listOf<GameEvent>(GameEvent.Stored(point, item.id, xp)) + levelUpEvents(levelBefore)
}

/** Lets a stored butterfly go on a tile, where it flutters about until it is caught again; only so many may be out at once. */
internal fun GameEngine.release(
    wings: Butterflies.Wings,
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    if (state.releasedButterflies.size >= Butterflies.MOST_RELEASED) return reject(Rejection.TooManyButterflies)
    removeFromStorage(StorageItem.Butterfly(wings).id, 1)
    storagePlacementQuietUntil = now.advancedBy(STORAGE_PLACEMENT_QUIET)
    state.releasedButterflies = state.releasedButterflies + ReleasedButterfly(wings = wings, home = point)
    return listOf(GameEvent.Placed(point))
}
