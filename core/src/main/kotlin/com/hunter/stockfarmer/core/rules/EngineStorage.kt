package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.PlacedBuilding
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.roundedToCents
import java.time.Instant

/** How many items the backpack holds before any storage building is placed. */
const val BACKPACK_CAPACITY = 100

/** How many items can be stored: the backpack, and whatever the storage buildings add to it. */
val GameEngine.storageCapacity: Int
    get() =
        state.placedBuildings.fold(BACKPACK_CAPACITY) { total, placed ->
            total +
                (placed.building.storageCapacity ?: 0)
        }

/** How many items are in storage, of all kinds together. */
val GameEngine.storedCount: Int get() = state.storage.values.sum()

/** What is in storage, ordered by when each item first becomes available. */
val GameEngine.storedItems: List<Pair<StorageItem, Int>>
    get() =
        StorageItem.menuOrder.mapNotNull { item ->
            state.storage[item.id]?.takeIf { it > 0 }?.let { item to it }
        }

/** Picks up the Harvest tool holding one of its implements. */
fun GameEngine.select(tool: HarvestTool) {
    state.harvestTool = tool
    setMode(PlayMode.harvest)
    if (tool == HarvestTool.scythe) learn(Lesson.pickHarvest)
}

fun GameEngine.isSelling(tool: HarvestTool): Boolean = tool.canSell && tool in state.sellingTools

fun GameEngine.setSelling(
    isSelling: Boolean,
    tool: HarvestTool,
) {
    if (isSelling && tool.canSell) state.sellingTools.add(tool) else state.sellingTools.remove(tool)
}

/** Whether the Harvest tool is in hand holding this implement. */
internal fun GameEngine.isHolding(tool: HarvestTool): Boolean =
    state.mode == PlayMode.harvest && state.harvestTool == tool

/**
 * What to tell a player over something only a harvest implement can gather, when it isn't the one in hand: a ripe crop or
 * fruit tree, a tree, a rock, a workshop with something ready, an animal that has milk or wool ready (the Bucket), an egg.
 */
fun GameEngine.missingHarvestToolRejection(
    point: GridPoint,
    now: Instant,
): Rejection? {
    val site = state.plant(point)
    val crop = site?.let { Crop.named(it.second.cropID) }
    val needed: HarvestTool
    val rejection: Rejection
    if (site != null && crop != null && GrowthConditions(state).isReady(site.second, crop, site.first, now)) {
        needed = crop.harvestTool
        rejection = if (crop.isFruitTree) Rejection.UseTheBucketForFruit else Rejection.UseTheScythe
    } else if (state.tree(point) != null) {
        needed = HarvestTool.axe
        rejection = Rejection.UseTheAxe
    } else if (state.rocks[point] != null) {
        needed = HarvestTool.pickaxe
        rejection = Rejection.UseThePickaxe
    } else if (holdsTractorLoad(point)) {
        needed = HarvestTool.scythe
        rejection = Rejection.UseTheScythe
    } else if (finishedWorkshop(point, now) != null) {
        needed = HarvestTool.bucket
        rejection = Rejection.UseTheBucketForGoods
    } else {
        val produce = readyProduce(point, now)
        if (produce != null) {
            needed = produce.second
            rejection =
                if (produce.second ==
                    HarvestTool.scythe
                ) {
                    Rejection.UseTheScytheForWool
                } else {
                    Rejection.UseTheBucketForAnimals
                }
        } else if (state.eggs.any { it.point == point }) {
            needed = HarvestTool.bucket
            rejection = Rejection.UseTheBucketForEggs
        } else {
            return null
        }
    }
    return if (isHolding(needed)) null else rejection
}

/** Takes in one of what an implement gathered: stored, or sold on the spot if that implement is set to sell. */
internal fun GameEngine.gather(
    item: StorageItem,
    point: GridPoint,
    tool: HarvestTool,
    xp: Int = 0,
): GameEvent {
    recordQuestProgress(
        com.hunter.stockfarmer.core.model.QuestGoal
            .Collect(item.id),
    )
    markHarvested()
    if (!isSelling(tool)) {
        state.storage[item.id] = (state.storage[item.id] ?: 0) + 1
        return GameEvent.Stored(point, item.id, xp)
    }
    val value = saleValue(item, 1)
    credit(value)
    recordSale(item, 1, value)
    return GameEvent.Sold(point, item.id, value, xp)
}

/** Whether what an implement gathers has somewhere to go: selling needs no room. */
internal fun GameEngine.hasRoom(
    count: Int,
    tool: HarvestTool,
): Boolean = isSelling(tool) || storedCount + count <= storageCapacity

/** A storage building can only come down once the remaining storage can hold its items. */
fun GameEngine.demolishRejection(placed: PlacedBuilding): Rejection? {
    if (state.tractorRun?.home == placed.origin) return Rejection.TractorBusy
    if (state.tractorCargo[placed.origin]?.isEmpty() == false) return Rejection.TractorCargoWaiting
    val capacity = placed.building.storageCapacity ?: 0
    if (capacity <= 0) return null
    val excess = storedCount - (storageCapacity - capacity)
    return if (excess > 0) Rejection.StorageInUse(excess) else null
}

/** Takes some of an item out of storage, leaving no empty entry behind; callers have checked that many are there. */
internal fun GameEngine.removeFromStorage(
    itemID: String,
    count: Int,
) {
    val held = state.storage[itemID] ?: return
    if (held <= count) state.storage.remove(itemID) else state.storage[itemID] = held - count
}

/** What selling some of a stored item pays. */
fun GameEngine.saleValue(
    item: StorageItem,
    quantity: Int,
): Double = (item.sellValue * quantity).roundedToCents

/** Throws part or all of a stored item away for nothing; nothing happens if that many aren't stored. */
fun GameEngine.trashStored(
    item: StorageItem,
    quantity: Int,
): List<GameEvent> {
    if (quantity <= 0 || quantity > (state.storage[item.id] ?: 0)) return emptyList()
    removeFromStorage(item.id, quantity)
    return listOf(GameEvent.StorageTrashed(item.id, quantity))
}

/** Sells part or all of a stored item at the usual price; nothing happens if that many aren't stored. */
fun GameEngine.sellStored(
    item: StorageItem,
    quantity: Int,
): List<GameEvent> {
    if (!item.isSellable || quantity <= 0 || quantity > (state.storage[item.id] ?: 0)) return emptyList()
    val value = saleValue(item, quantity)
    removeFromStorage(item.id, quantity)
    credit(value)
    recordSale(item, quantity, value)
    return listOf(GameEvent.StorageSold(item.id, quantity, value))
}
