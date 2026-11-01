package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.core.util.advancedBy
import java.time.Instant

/** The stored item the Decorate tool is holding to put into water or feed to an animal, if it is holding one. */
val GameEngine.heldItem: StorageItem? get() = state.heldItemID?.let(StorageItem::of)

/** Picks up a stored fish or crop for the Decorate tool: a fish is let go in water or fed to a cat or dog, a crop fed to an animal. */
internal fun GameEngine.equipHeld(item: StorageItem): Boolean {
    if ((state.storage[item.id] ?: 0) <= 0) return false
    state.heldItemID = item.id
    state.selectedDecorationID = null
    state.placesDecorationsFromStorage = false
    state.mode = PlayMode.decorate
    return true
}

/** Uses the held item on a tile: it feeds the animal standing there, or a fish is let go in the water there. */
internal fun GameEngine.useHeldItem(
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val item = heldItem ?: return reject(Rejection.NothingHere)
    if ((state.storage[item.id] ?: 0) <= 0) {
        val rejection = Rejection.NoneInStorage(item.name)
        return if (isQuietAboutStorage(rejection, now)) emptyList() else reject(rejection)
    }
    if (item is StorageItem.Butterfly) return release(item.wings, point, now)
    animalStandingAt(point)?.let { return feed(it, item, now) }
    if (item !is StorageItem.Fish) return reject(Rejection.NeedAnimalToFeed)
    return letGo(item.kind, point, now)
}

/** Lets a fish go on a tile of free water, where it swims like any other and stays until it is caught. */
private fun GameEngine.letGo(
    kind: FishKind,
    point: GridPoint,
    now: Instant,
): List<GameEvent> {
    val home = homeForLetGoFish(point)
    if (point !in state.water || state.decoration(point) != null || isFishSwimming(point) || home == null) {
        return reject(Rejection.NeedWaterForFish)
    }
    val fish = PondFish(kind.id, point, kind.randomWeight(SystemRandomSource), null, isNaturallyGenerated = false)
    takeFromStorage(StorageItem.Fish(kind), now)
    val waiting = state.fishPonds[home]
    state.fishPonds[home] =
        FishPond(
            fish = fish,
            nextFishAt = waiting?.nextFishAt ?: now.advancedBy(GameEngine.FISH_INTERVAL),
            waitStartedAt =
                waiting?.waitStartedAt
                    ?: (
                        waiting?.nextFishAt ?: now.advancedBy(
                            GameEngine.FISH_INTERVAL,
                        )
                    ).advancedBy(-GameEngine.FISH_INTERVAL),
        )
    return listOf(GameEvent.Placed(point))
}

/**
 * Where a fish let go on a tile is saved: under the tile, unless that is the home of a fish that has swum off, which it must
 * not replace, and then under another tile of the same water that is no fish's home.
 */
private fun GameEngine.homeForLetGoFish(point: GridPoint): GridPoint? {
    if (state.fishPonds[point]?.fish == null) return point
    val layout = state.waterLayout
    return layout.bodyIndexByTile[point]?.let {
        layout.bodies[it].tiles.firstOrNull { tile ->
            tile !in state.fishPonds
        }
    }
}

/** Feeds an animal by hand: its favorite crop, or any fish for a cat or dog, boosts it for a few minutes. */
private fun GameEngine.feed(
    animal: AnimalRef,
    item: StorageItem,
    now: Instant,
): List<GameEvent> {
    if (!isFood(item, animal)) return reject(Rejection.WontEat)
    if (meal(animal)?.isActive(now) == true || hasEaten(animal)) return reject(Rejection.AlreadyFed)
    takeFromStorage(item, now)
    giveMeal(animal, now)
    if (item !is StorageItem.Fish) recordFavoriteEaten(animal)
    return listOf(GameEvent.Fed(animal))
}

private fun GameEngine.isFood(
    item: StorageItem,
    animal: AnimalRef,
): Boolean =
    when (item) {
        is StorageItem.CropItem -> item.crop.id == favoriteCrop(animal)?.id
        is StorageItem.RareCrop -> item.crop.id == favoriteCrop(animal)?.id
        is StorageItem.Fish -> animal is AnimalRef.Pet
        else -> false
    }

/**
 * Takes one stored item for setting down or feeding, after which the finger still on the farm gets a moment before being
 * told there are none.
 */
private fun GameEngine.takeFromStorage(
    item: StorageItem,
    now: Instant,
) {
    removeFromStorage(item.id, 1)
    storagePlacementQuietUntil = now.advancedBy(STORAGE_PLACEMENT_QUIET)
}
