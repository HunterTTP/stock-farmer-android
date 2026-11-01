package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.ShopItem

fun GameEngine.isUnlocked(crop: Crop): Boolean = state.level >= crop.unlockLevel

fun GameEngine.isUnlocked(tree: Tree): Boolean = state.level >= tree.unlockLevel

fun GameEngine.isUnlocked(building: Building): Boolean = state.level >= building.unlockLevel

/** The level a seed or building needs, if the player hasn't reached it yet. */
fun GameEngine.requiredLevel(item: ShopItem): Int? = if (item.unlockLevel > state.level) item.unlockLevel else null

fun GameEngine.select(crop: Crop) {
    if (!isUnlocked(crop)) return
    state.selectedCropID = crop.id
    state.mode = PlayMode.plant
}

/** True once the game has recorded which unlocks the player already has, which a brand new game does as it starts. */
val GameEngine.hasCountedUnlocks: Boolean get() = state.seenUnlocks != null

fun GameEngine.hasSeen(unlockKey: String): Boolean = state.seenUnlocks?.contains(unlockKey) ?: false

fun GameEngine.countAsSeen(unlockKeys: Set<String>) {
    state.seenUnlocks = ((state.seenUnlocks ?: mutableSetOf()) + unlockKeys).toMutableSet()
}
