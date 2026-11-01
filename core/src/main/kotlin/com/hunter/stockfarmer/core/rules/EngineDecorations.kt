package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.bottomRow
import com.hunter.stockfarmer.core.util.advancedBy
import com.hunter.stockfarmer.core.util.epochSecondsDouble
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant

/** The decoration the Decorate tool is placing, or null while it places fences. */
val GameEngine.selectedDecoration: Decoration? get() = state.selectedDecorationID?.let(Decoration::named)

/** Which size the selected decoration is placed in, counting from the smallest. */
val GameEngine.selectedDecorationSize: Int
    get() = selectedDecoration?.let { it.offeredSize(state.selectedDecorationSizes[it.id]) } ?: 0

/** Picks the size a decoration is placed in and switches to placing it. */
fun GameEngine.select(
    size: Int,
    decoration: Decoration,
) {
    if (!isUnlocked(decoration) || size !in decoration.offeredSizes) return
    state.selectedDecorationSizes[decoration.id] = size
    select(decoration)
}

fun GameEngine.isUnlocked(decoration: Decoration): Boolean = state.level >= decoration.unlockLevel

/** Makes every sprinkler spray all the time, like a fountain, or on the shared schedule again. */
fun GameEngine.setSprinklersRunConstantly(runs: Boolean) {
    state.sprinklersRunConstantly = runs
}

/** Picks a decoration and switches to placing decorations. */
fun GameEngine.select(decoration: Decoration) {
    if (!isUnlocked(decoration) || decoration.offeredSizes.isEmpty()) return
    state.placesDecorationsFromStorage = false
    state.heldItemID = null
    state.selectedDecorationID = decoration.id
    // The Fertilize tool holds sprinklers; every other decoration is the Decorate tool's.
    if (decoration.isSprinkler) {
        state.fertilizesWithSprinklers = true
        state.lastSprinklerID = decoration.id
    } else {
        state.lastDecorationID = decoration.id
    }
    state.mode = PlayMode.decorate
}

/**
 * Picks up a stored decoration, fence or butterfly to set down, which uses up one from storage instead of costing money.
 * Anything else has nothing to be set down, so nothing happens, which is false.
 */
fun GameEngine.equip(item: StorageItem): Boolean {
    when (item) {
        StorageItem.Fertilizer -> {
            return equipFertilizer()
        }

        is StorageItem.Fish, is StorageItem.CropItem, is StorageItem.RareCrop, is StorageItem.Butterfly -> {
            return equipHeld(item)
        }

        is StorageItem.Fence -> {
            select(item.color)
            state.placesDecorationsFromStorage = true
            return true
        }

        is StorageItem.DecorationItem -> {
            if (!isUnlocked(item.decoration) || item.size !in item.decoration.offeredSizes) return false
            select(item.size, item.decoration)
            state.placesDecorationsFromStorage = true
            return true
        }

        else -> {
            return false
        }
    }
}

/** How long after setting something down from storage that running out of it goes unremarked. */
internal const val STORAGE_PLACEMENT_QUIET = 0.8

/** Whether a refusal for having none left in storage is held back because one was only just set down. */
internal fun GameEngine.isQuietAboutStorage(
    rejection: Rejection,
    now: Instant,
): Boolean {
    val until = storagePlacementQuietUntil ?: return false
    return rejection is Rejection.NoneInStorage && now < until
}

/** Whether a stored item is the one the Decorate tool is holding to set down. */
fun GameEngine.isEquipped(item: StorageItem): Boolean =
    when (item) {
        is StorageItem.Fish, is StorageItem.CropItem, is StorageItem.RareCrop, is StorageItem.Butterfly -> {
            state.heldItemID == item.id
        }

        StorageItem.Fertilizer -> {
            state.mode == PlayMode.fertilize && state.placesFertilizerFromStorage
        }

        is StorageItem.DecorationItem -> {
            state.placesDecorationsFromStorage && state.heldItemID == null &&
                state.selectedDecorationID == item.decoration.id && selectedDecorationSize == item.size
        }

        is StorageItem.Fence -> {
            state.mode == PlayMode.decorate && state.placesDecorationsFromStorage && state.heldItemID == null &&
                state.selectedDecorationID == null && selectedFenceColor == item.color
        }

        else -> {
            false
        }
    }

/** The stored item the Decorate or Fertilize tool is setting down from storage, if it is. */
val GameEngine.equippedItem: StorageItem?
    get() {
        if (state.mode == PlayMode.decorate) heldItem?.let { return it }
        if (state.mode == PlayMode.fertilize && state.placesFertilizerFromStorage) return StorageItem.Fertilizer
        if (state.mode != PlayMode.decorate || !state.placesDecorationsFromStorage) return null
        return selectedDecoration?.let { StorageItem.DecorationItem(it, selectedDecorationSize) }
            ?: StorageItem.Fence(selectedFenceColor)
    }

/** How many of what the tool has equipped are left in storage, which is nothing once it has used them all. */
val GameEngine.equippedStock: Int? get() = equippedItem?.let { state.storage[it.id] ?: 0 }

/** Whether the Decorate or Fertilize tool is setting down what comes out of storage, which is what its list then shows. */
val GameEngine.isPlacingFromStorage: Boolean
    get() =
        (state.mode == PlayMode.decorate && (state.placesDecorationsFromStorage || state.heldItemID != null)) ||
            (state.mode == PlayMode.fertilize && state.placesFertilizerFromStorage)

/** Why the selected decoration can't be paid for now, or null if it can: money, or a stored one when setting them down from storage. */
internal fun GameEngine.unaffordableSelectedDecoration(
    decoration: Decoration,
    size: Int,
): Rejection? {
    if (!state.placesDecorationsFromStorage) {
        return if (state.money >=
            decoration.cost
        ) {
            null
        } else {
            Rejection.NeedMoney(decoration.cost)
        }
    }
    val item = StorageItem.DecorationItem(decoration, size)
    return if ((state.storage[item.id] ?: 0) > 0) null else Rejection.NoneInStorage(item.name)
}

/** Pays for the selected decoration being set down: one from storage when setting them down from storage, otherwise its price. */
internal fun GameEngine.payForSelectedDecoration(): Rejection? {
    val decoration = selectedDecoration ?: return null
    unaffordableSelectedDecoration(decoration, selectedDecorationSize)?.let { return it }
    if (state.placesDecorationsFromStorage) {
        removeFromStorage(StorageItem.DecorationItem(decoration, selectedDecorationSize).id, 1)
    } else {
        state.money -= decoration.cost
    }
    return null
}

/** Sets the selected decoration down with its bottom left tile on the tapped tile. */
internal fun GameEngine.tapDecoration(
    decoration: Decoration,
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    if (!isUnlocked(decoration)) return reject(Rejection.NeedLevel(decoration.unlockLevel))
    if (decoration.offeredSizes.isEmpty()) return reject(Rejection.NothingHere)
    val origin = decoration.originSettingBottomLeftOn(point)
    if (decoration.isSprinkler) {
        beginPlacingSelectedDecoration(point)
        return emptyList()
    }
    val ground = decoration.footprint(origin)
    if (!ground.bottomRow.all(GameState::contains) || !ground.bottomRow.all(::isFreeForTree) ||
        origin in state.decorations
    ) {
        return reject(Rejection.NotEnoughSpace)
    }
    if (blocksAHomesFront(ground)) return reject(Rejection.BlocksAFront)
    if (decoration.id == "trunk" &&
        state.decorations.values.count { it.decorationID == decoration.id } >= Decoration.TRUNK_LIMIT
    ) {
        return reject(Rejection.BuildingLimit(decoration.name, Decoration.TRUNK_LIMIT))
    }
    val paid = if (state.placesDecorationsFromStorage) 0.0 else decoration.cost
    payForSelectedDecoration()?.let { return if (isQuietAboutStorage(it, now)) emptyList() else reject(it) }
    if (state.placesDecorationsFromStorage) storagePlacementQuietUntil = now.advancedBy(STORAGE_PLACEMENT_QUIET)
    notePlaced(decoration.id)
    if (decoration.isRock) {
        state.rocks[origin] = decoration.rockSizes[selectedDecorationSize]
    } else {
        state.putDecoration(origin, PlacedDecoration.of(decoration, selectedDecorationSize, placedAt = now))
    }
    return listOf(GameEvent.Decorated(origin, paid))
}

/**
 * A sprinkler that is moved or destroyed stops watering its land the way it started: each tile it had watered dries out at
 * its own random moment over as long as the spray took, until all of it has.
 */
internal fun GameEngine.dryOut(
    placed: PlacedDecoration,
    origin: GridPoint,
    now: Instant,
) {
    for (tile in state.wateredFarmland(placed, origin)) {
        val random = StageRandom("${tile.row},${tile.col}|dry|${now.epochSecondsDouble}")
        val driesAt = now.advancedBy(Decoration.SPRAY_DURATION * random.next())
        state.lingeringWater[tile] = maxOf(driesAt, state.lingeringWater[tile] ?: Instant.MIN)
    }
}

/**
 * Moves every sprinkler on in its first spray: its farmland gets saturated tile by tile, each at its own random moment of
 * the spray, until all of it is when the spray is over; and lets the land of those taken away dry out tile by tile. True if
 * anything changed.
 */
fun GameEngine.activateSprinklers(now: Instant): Boolean {
    var changed = false
    for ((tile, driesAt) in state.lingeringWater.entries.toList()) {
        if (driesAt <= now) {
            state.lingeringWater.remove(tile)
            changed = true
        }
    }
    for ((origin, placed) in state.decorations.entries.toList()) {
        val endsAt = placed.wateringStartsAt ?: continue
        val reach = placed.decoration.waterReach ?: continue
        if (endsAt <= now) {
            state.putDecoration(origin, placed.copy(wateringStartsAt = null, wateredTiles = emptySet()))
            changed = true
            continue
        }
        val elapsed = now.secondsSince(endsAt.advancedBy(-Decoration.SPRAY_DURATION))
        val watered = placed.wateredTiles.toMutableSet()
        val reachable =
            placed.decoration
                .footprint(
                    origin,
                ).flatMap { FarmlandRules.farmlandWithin(reach, it, state.farmland) }
        for (tile in reachable) {
            val random = StageRandom("${origin.row},${origin.col}|${tile.row},${tile.col}")
            if (elapsed >= Decoration.SPRAY_DURATION * random.next()) watered += tile
        }
        if (watered == placed.wateredTiles) continue
        state.putDecoration(origin, placed.copy(wateredTiles = watered))
        changed = true
    }
    return changed
}

/** Whether the saturation of some farmland is still changing a tile at a time: a sprinkler in its first spray, or land drying out. */
val GameEngine.isSoilWateringChanging: Boolean
    get() = state.lingeringWater.isNotEmpty() || state.decorations.values.any { it.wateringStartsAt != null }

/** Takes a decoration away and refunds 80% of its price. */
fun GameEngine.destroyDecoration(
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val (origin, placed) = state.decoration(point) ?: return emptyList()
    dryOut(placed, origin, now)
    state.removeDecoration(origin)
    credit(refund(placed.decoration))
    return listOf(GameEvent.Undecorated(origin, refund(placed.decoration)))
}

/** Ground a sprinkler may stand on: grass, farmland or water, with no crop, fish, animal or anything else on it. */
internal fun GameEngine.isFreeForSprinkler(point: GridPoint): Boolean =
    point !in state.fences && state.buildingBlocking(point) == null && !state.hasObstacle(point) &&
        point !in state.plots &&
        animalStandingAt(point) == null && state.fishPonds.values.none { it.fish?.position == point }
