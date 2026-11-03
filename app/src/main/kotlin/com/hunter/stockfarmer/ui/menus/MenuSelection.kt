package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.BuildingStyle
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.equip
import com.hunter.stockfarmer.core.rules.firstRejection
import com.hunter.stockfarmer.core.rules.markSeen
import com.hunter.stockfarmer.core.rules.requiredLevel
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.core.rules.selectFertilizer
import com.hunter.stockfarmer.core.rules.sellStored
import com.hunter.stockfarmer.core.rules.trashStored

internal sealed interface MenuSelection {
    data class Shop(
        val item: ShopItem,
    ) : MenuSelection

    data object Grass : MenuSelection

    data class Style(
        val building: Building,
        val style: BuildingStyle,
    ) : MenuSelection

    data class Size(
        val decoration: Decoration,
        val size: Int,
    ) : MenuSelection

    data class Fence(
        val color: FenceColor,
    ) : MenuSelection

    data class Tree(
        val species: TreeSpecies,
        val color: TreeColor,
    ) : MenuSelection

    data class Water(
        val style: WaterStyle,
    ) : MenuSelection

    data class Stored(
        val item: StorageItem,
    ) : MenuSelection
}

/** Selection never buys an item; the engine charges and checks the site when it is placed on the farm. */
internal object MenuActions {
    fun choose(
        engine: GameEngine,
        selection: MenuSelection,
    ): Rejection? {
        val requiredLevel =
            when (selection) {
                is MenuSelection.Shop -> {
                    engine.requiredLevel(selection.item)
                }

                is MenuSelection.Style -> {
                    engine.requiredLevel(ShopItem.BuildingItem(selection.building.id))
                }

                is MenuSelection.Size -> {
                    engine.requiredLevel(ShopItem.DecorationItem(selection.decoration.id))
                }

                is MenuSelection.Tree -> {
                    engine.requiredLevel(ShopItem.Tree(selection.species.id))
                }

                is MenuSelection.Stored -> {
                    if ((engine.state.storage[selection.item.id] ?: 0) <= 0) {
                        return Rejection.NoneInStorage(selection.item.name)
                    }
                    (selection.item as? StorageItem.DecorationItem)?.let {
                        engine.requiredLevel(ShopItem.DecorationItem(it.decoration.id))
                    }
                }

                else -> {
                    null
                }
            }
        if (requiredLevel != null) return Rejection.NeedLevel(requiredLevel)
        when (selection) {
            is MenuSelection.Shop -> {
                selectShopItem(engine, selection.item)
            }

            MenuSelection.Grass -> {
                engine.select(LandscapeTool.grass)
            }

            is MenuSelection.Style -> {
                engine.select(selection.style, selection.building)
            }

            is MenuSelection.Size -> {
                engine.select(selection.size, selection.decoration)
            }

            is MenuSelection.Fence -> {
                engine.select(selection.color)
            }

            is MenuSelection.Tree -> {
                engine.select(selection.species)
                engine.select(selection.color)
            }

            is MenuSelection.Water -> {
                engine.select(selection.style)
            }

            is MenuSelection.Stored -> {
                engine.equip(selection.item)
            }
        }
        selection.shopItem?.let { engine.markSeen(it) }
        return null
    }

    private fun selectShopItem(
        engine: GameEngine,
        item: ShopItem,
    ) {
        when (item) {
            is ShopItem.Seed -> Crop.named(item.cropID)?.let { engine.select(it) }
            ShopItem.Fertilizer -> engine.selectFertilizer()
            is ShopItem.BuildingItem -> Building.named(item.id)?.let { engine.select(it) }
            is ShopItem.DecorationItem -> Decoration.named(item.id)?.let { engine.select(it) }
            is ShopItem.Tree -> TreeSpecies.named(item.id)?.let { engine.select(it) }
            is ShopItem.GroundCoverItem -> engine.select(LandscapeTool.groundCover(item.cover))
            ShopItem.Farmland -> engine.select(LandscapeTool.farmland)
            ShopItem.Water -> engine.select(LandscapeTool.water)
            ShopItem.Fence -> engine.select(FenceColor.named(engine.state.selectedFenceColorID) ?: FenceColor.starter)
        }
    }

    /** Sells or throws away stored items; [StorageOutcome.events] holds the news to publish, nothing if it was refused. */
    fun transact(
        engine: GameEngine,
        item: StorageItem,
        quantity: Int,
        sell: Boolean,
    ): StorageOutcome {
        val events = if (sell) engine.sellStored(item, quantity) else engine.trashStored(item, quantity)
        val rejection = events.firstRejection?.message
        val message =
            rejection ?: when (val event = events.firstOrNull()) {
                is GameEvent.StorageSold -> {
                    "Sold ${MenuNumbers.count(event.quantity)} ${item.name} for ${event.value.dollars}"
                }

                is GameEvent.StorageTrashed -> {
                    "${item.discardedTitle} ${MenuNumbers.count(event.quantity)} ${item.name}"
                }

                else -> {
                    null
                }
            }
        return StorageOutcome(message, if (message == null || rejection != null) emptyList() else events)
    }
}

internal class StorageOutcome(
    val message: String?,
    val events: List<GameEvent>,
)

internal val MenuSelection.shopItem: ShopItem?
    get() =
        when (this) {
            is MenuSelection.Shop -> item
            is MenuSelection.Style -> ShopItem.BuildingItem(building.id)
            is MenuSelection.Size -> ShopItem.DecorationItem(decoration.id)
            is MenuSelection.Tree -> ShopItem.Tree(species.id)
            is MenuSelection.Water -> ShopItem.Water
            is MenuSelection.Fence -> ShopItem.Fence
            else -> null
        }

internal object MenuNumbers {
    fun count(value: Int): String =
        java.text.NumberFormat
            .getIntegerInstance(java.util.Locale.US)
            .format(value)

    fun quantity(
        requested: Int,
        stored: Int,
    ): Int = if (stored <= 0) 0 else requested.coerceIn(1, stored)
}
