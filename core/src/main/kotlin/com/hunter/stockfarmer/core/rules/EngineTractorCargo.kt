package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.UnlockOrder
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.tractorOccupiedTiles
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

data class TractorCropSummary(
    val crop: Crop,
    val plantedCount: Int,
    val readyCount: Int,
    val remaining: Double?,
    val gathered: Int,
) {
    val id: String get() = crop.id
}

/** The crops planted or in cargo, the one that pays most for each crop first, and in unlock order among those that pay the same. */
fun GameEngine.tractorCropOverview(
    home: GridPoint,
    now: Instant,
): List<TractorCropSummary> {
    val planted = cropGrowthOverview(now).associateBy { it.id }
    val cargo = state.tractorCargo[home] ?: emptyMap()
    val rewards = Crop.all.associate { it.id to saleValue(StorageItem.CropItem(it), 1) }
    return UnlockOrder
        .sorted(Crop.all) { it.unlockLevel }
        .withIndex()
        .sortedWith { first, second ->
            val a = rewards[first.value.id] ?: 0.0
            val b = rewards[second.value.id] ?: 0.0
            if (a != b) b.compareTo(a) else first.index.compareTo(second.index)
        }.map { it.value }
        .mapNotNull { crop ->
            val growth = planted[crop.id]
            val gathered = cargo[crop.id] ?: 0
            if (growth == null && gathered <= 0) return@mapNotNull null
            TractorCropSummary(crop, growth?.count ?: 0, growth?.readyCount ?: 0, growth?.remaining, gathered)
        }
}

fun GameEngine.storeTractorCargo(
    cropID: String,
    home: GridPoint,
    from: GridPoint? = null,
): List<GameEvent> {
    tractorUnloadRejection(home)?.let { return reject(it) }
    val held = state.tractorCargo[home]?.get(cropID) ?: 0
    if (held <= 0) return emptyList()
    val quantity = min(held, max(0, storageCapacity - storedCount))
    if (quantity <= 0) return reject(Rejection.StorageFull)
    val levelBefore = state.level
    val xp = Crop.named(cropID)?.let { earnXPForHarvesting(it, quantity) } ?: 0
    state.storage[cropID] = (state.storage[cropID] ?: 0) + quantity
    removeTractorCargo(cropID, quantity, home)
    return listOf<GameEvent>(GameEvent.TractorCargoStored(cropID, quantity, from, xp)) + levelUpEvents(levelBefore)
}

fun GameEngine.sellTractorCargo(
    cropID: String,
    home: GridPoint,
    from: GridPoint? = null,
): List<GameEvent> {
    tractorUnloadRejection(home)?.let { return reject(it) }
    val crop = Crop.named(cropID) ?: return emptyList()
    val quantity = state.tractorCargo[home]?.get(cropID) ?: return emptyList()
    if (quantity <= 0) return emptyList()
    val item = StorageItem.CropItem(crop)
    val value = saleValue(item, quantity)
    val levelBefore = state.level
    val xp = earnXPForHarvesting(crop, quantity)
    credit(value)
    recordSale(item, quantity, value)
    removeTractorCargo(cropID, quantity, home)
    return listOf<GameEvent>(GameEvent.TractorCargoSold(cropID, quantity, value, from, xp)) + levelUpEvents(levelBefore)
}

internal fun GameEngine.moveTractorCargo(
    old: GridPoint,
    new: GridPoint,
) {
    if (old == new) return
    val cargo = state.tractorCargo.remove(old) ?: return
    val merged = (state.tractorCargo[new] ?: emptyMap()).toMutableMap()
    for ((id, count) in cargo) merged[id] = (merged[id] ?: 0) + count
    state.tractorCargo[new] = merged
}

/**
 * The Scythe empties the Tractor's cargo from any tile of it, parked or driving: storing it, or selling it where the Scythe
 * is set to sell. The XP of the crops is earned as they are stored or sold, and so is the cash.
 */
internal fun GameEngine.collectTractorCargo(point: GridPoint): List<GameEvent> {
    val home = tractorHome(point) ?: return emptyList()
    val cargo = state.tractorCargo[home] ?: return emptyList()
    return cargo.keys.sorted().flatMap {
        if (isSelling(HarvestTool.scythe)) sellTractorCargo(it, home, point) else storeTractorCargo(it, home, point)
    }
}

/** Whether a tile is the Tractor's with a load aboard, which only the Scythe takes. */
internal fun GameEngine.holdsTractorLoad(point: GridPoint): Boolean =
    tractorHome(point)?.let { state.tractorCargo[it] }?.isEmpty() == false

/** The home of the Tractor that takes up a tile, whether it stands there or is driving over it. */
private fun GameEngine.tractorHome(point: GridPoint): GridPoint? {
    val run = state.tractorRun
    if (run != null && point in state.tractorOccupiedTiles) return run.home
    val placed = state.building(point) ?: return null
    return if (state.buildings[placed.origin] == "tractor1") placed.origin else null
}

/** Its cargo can be unloaded at any time, even while it is driving. */
private fun GameEngine.tractorUnloadRejection(home: GridPoint): Rejection? =
    if (state.buildings[home] == "tractor1") null else Rejection.NothingHere

private fun GameEngine.removeTractorCargo(
    cropID: String,
    quantity: Int,
    home: GridPoint,
) {
    val cargo = state.tractorCargo[home] ?: return
    val remaining = (cargo[cropID] ?: 0) - quantity
    val updated = if (remaining > 0) cargo + (cropID to remaining) else cargo - cropID
    if (updated.isEmpty()) state.tractorCargo.remove(home) else state.tractorCargo[home] = updated
}
