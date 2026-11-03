package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.countAsSeen
import com.hunter.stockfarmer.core.rules.currentLesson
import com.hunter.stockfarmer.core.rules.farmlandLimit
import com.hunter.stockfarmer.core.rules.hasCountedUnlocks
import com.hunter.stockfarmer.core.rules.hasSeen
import com.hunter.stockfarmer.core.rules.requiredLevel
import com.hunter.stockfarmer.game.GameSession

/** Keys are stable saved strings; farmland gets a fresh dot whenever its allowance increases. */
fun GameEngine.unlockKey(item: ShopItem): String? =
    when (item) {
        is ShopItem.Seed -> Crop.named(item.cropID)?.let { "seed:${it.id}" }
        is ShopItem.BuildingItem -> "building:${item.id}"
        is ShopItem.Tree -> "tree:${item.id}"
        is ShopItem.DecorationItem -> "decoration:${item.id}"
        is ShopItem.GroundCoverItem -> "groundCover:${item.cover.rawValue}"
        ShopItem.Farmland -> "farmland:${state.farmlandLimit}"
        ShopItem.Water, ShopItem.Fence, ShopItem.Fertilizer -> null
    }

val GameEngine.unlockKeys: Set<String>
    get() = allUnlockItems.filter { requiredLevel(it) == null }.mapNotNull(::unlockKey).toSet()

fun GameEngine.isNew(item: ShopItem): Boolean =
    currentLesson == null && requiredLevel(item) == null && unlockKey(item)?.let { !hasSeen(it) } == true

fun GameEngine.markSeen(item: ShopItem) {
    unlockKey(item)?.let { countAsSeen(setOf(it)) }
}

/** Called after every farm load, before play can unlock anything else. Uses the existing saved seenUnlocks field. */
fun GameEngine.countCurrentUnlocksAsSeen() {
    if (!hasCountedUnlocks) countAsSeen(unlockKeys)
}

enum class UnlockCategory { PLANT, FERTILIZE, TERRAIN, BUILD, DECORATE }

fun GameSession.hasNewItems(category: UnlockCategory): Boolean = act { it.hasNewItems(category) }

fun GameEngine.hasNewItems(category: UnlockCategory): Boolean = category.items.any(::isNew)

private val UnlockCategory.items: List<ShopItem>
    get() = unlockItems.getValue(this)

/** The catalog never changes while the app runs, so each category's items are listed once. */
private val unlockItems: Map<UnlockCategory, List<ShopItem>> by lazy {
    UnlockCategory.entries.associateWith { it.catalogItems() }
}

private fun UnlockCategory.catalogItems(): List<ShopItem> =
    when (this) {
        UnlockCategory.PLANT -> {
            Crop.all.map { ShopItem.Seed(it.id) }
        }

        UnlockCategory.FERTILIZE -> {
            Decoration.shopChoices
                .filter { it.isSprinkler }
                .map { ShopItem.DecorationItem(it.id) }
        }

        UnlockCategory.TERRAIN -> {
            listOf(ShopItem.Farmland, ShopItem.Water) +
                GroundCover.entries.map { ShopItem.GroundCoverItem(it) } +
                TreeSpecies.all.map { ShopItem.Tree(it.id) }
        }

        UnlockCategory.BUILD -> {
            Building.all.map { ShopItem.BuildingItem(it.id) }
        }

        UnlockCategory.DECORATE -> {
            Decoration.shopChoices
                .filter { !it.isSprinkler }
                .map { ShopItem.DecorationItem(it.id) }
        }
    }

private val allUnlockItems: List<ShopItem> by lazy {
    UnlockCategory.entries.flatMap { it.items }
}
