package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.catalog.UnlockOrder
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.compactDollars
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.equippedItem
import com.hunter.stockfarmer.core.rules.hasReachedPlacementLimit
import com.hunter.stockfarmer.core.rules.isEquipped
import com.hunter.stockfarmer.core.rules.name
import com.hunter.stockfarmer.core.rules.placedOnMap
import com.hunter.stockfarmer.core.rules.placementLimit
import com.hunter.stockfarmer.core.rules.price
import com.hunter.stockfarmer.core.rules.requiredLevel
import com.hunter.stockfarmer.core.rules.selectedDecorationSize
import com.hunter.stockfarmer.core.rules.selectedFenceColor
import com.hunter.stockfarmer.core.rules.selectedTreeColor
import com.hunter.stockfarmer.core.rules.storageCapacity
import com.hunter.stockfarmer.core.rules.storedCount
import com.hunter.stockfarmer.core.rules.storedItems
import com.hunter.stockfarmer.core.rules.tree

internal data class MenuPrice(
    val text: String,
    val affordable: Boolean,
)

internal data class MenuChoice(
    val id: String,
    val title: String,
    val picture: MenuPicture,
    val selection: MenuSelection,
    val subtitle: String = "",
    val prices: List<MenuPrice> = emptyList(),
    val selected: Boolean = false,
    val requiredLevel: Int? = null,
    val affordable: Boolean = true,
    val atLimit: Boolean = false,
    val variantTitle: String = "",
    val variants: List<MenuChoice> = emptyList(),
)

internal data class MenuSection(
    val title: String,
    val choices: List<MenuChoice>,
)

internal data class StoredRow(
    val item: StorageItem,
    val count: Int,
    val picture: MenuPicture,
    val selected: Boolean,
) {
    val countText: String get() = "×${MenuNumbers.count(count)}"
}

/** Only immutable presentation values escape GameSession.read; no mutable farm collections are retained. */
internal data class MenuState(
    val money: String,
    val level: Int,
    val sections: List<MenuSection>,
    val stored: List<StoredRow>,
    val used: Int,
    val capacity: Int,
) {
    val capacityText: String get() = "${MenuNumbers.count(used)} / ${MenuNumbers.count(capacity)}"
    val capacityProgress: Float get() = (used.toFloat() / capacity.coerceAtLeast(1)).coerceIn(0f, 1f)

    companion object {
        fun from(
            state: GameState,
            menu: Menu,
        ): MenuState {
            val engine = GameEngine(state)
            val rows =
                engine.storedItems.map { (item, count) ->
                    StoredRow(item, count, MenuPictures.stored(item), engine.isEquipped(item))
                }
            val catalog = MenuCatalog(engine)
            val sections =
                when (menu) {
                    Menu.Build -> {
                        listOf(MenuSection("Buildings", catalog.sorted(Building.all.map(::buildingItem))))
                    }

                    Menu.Decorate -> {
                        listOf(
                            MenuSection(
                                "Decorations",
                                catalog.sorted(
                                    listOf(ShopItem.Fence) +
                                        Decoration.shopChoices.filter { !it.isSprinkler }.map(::decorationItem),
                                ),
                            ),
                            MenuSection(
                                "Sprinklers",
                                catalog.sorted(Decoration.shopChoices.filter { it.isSprinkler }.map(::decorationItem)),
                            ),
                            MenuSection(
                                "Stored items",
                                rows.filter { it.item.isEquippable }.map { row ->
                                    MenuChoice(
                                        id = "stored-${row.item.id}",
                                        title = row.item.name,
                                        picture = row.picture,
                                        selection = MenuSelection.Stored(row.item),
                                        subtitle = "${row.countText} in storage",
                                        selected = engine.equippedItem == row.item,
                                    )
                                },
                            ),
                        )
                    }

                    Menu.Terrain -> {
                        listOf(
                            MenuSection(
                                "Ground",
                                catalog.sorted(
                                    listOf(ShopItem.Farmland, ShopItem.Water) +
                                        GroundCover.entries.map { ShopItem.GroundCoverItem(it) },
                                ) +
                                    MenuChoice(
                                        id = "grass",
                                        title = "Grass",
                                        picture = MenuPictures.terrain("grass"),
                                        selection = MenuSelection.Grass,
                                        selected = engine.isSelected(LandscapeTool.grass),
                                    ),
                            ),
                            MenuSection("Trees", catalog.sorted(TreeSpecies.all.map { ShopItem.Tree(it.id) })),
                        )
                    }

                    Menu.Storage -> {
                        emptyList()
                    }
                }
            return MenuState(
                money = state.money.dollars,
                level = state.level,
                sections = sections,
                stored = rows,
                used = engine.storedCount,
                capacity = engine.storageCapacity,
            )
        }
    }
}

private fun buildingItem(building: Building) = ShopItem.BuildingItem(building.id)

private fun decorationItem(decoration: Decoration) = ShopItem.DecorationItem(decoration.id)

private class MenuCatalog(
    val engine: GameEngine,
) {
    private val state get() = engine.state

    fun sorted(items: List<ShopItem>): List<MenuChoice> = UnlockOrder.sorted(items) { it.unlockLevel }.map(::choice)

    private fun choice(item: ShopItem): MenuChoice {
        val limit = engine.placementLimit(item)
        val name = engine.name(item)
        val placed = MenuNumbers.count(engine.placedOnMap(item))
        val title = if (limit == null) name else "$name ($placed/${MenuNumbers.count(limit)})"
        val requiredLevel = engine.requiredLevel(item)
        val variants = variants(item)
        return MenuChoice(
            id = item.toString(),
            title = title,
            picture = picture(item),
            selection = MenuSelection.Shop(item),
            subtitle = requiredLevel?.let { "Unlocks at Level $it" } ?: "",
            prices = if (requiredLevel == null) prices(item) else emptyList(),
            selected = engine.isSelected(item) && engine.equippedItem == null,
            requiredLevel = requiredLevel,
            affordable = state.money >= engine.price(item),
            atLimit = engine.hasReachedPlacementLimit(item),
            variantTitle = variants.first,
            variants = variants.second,
        )
    }

    private fun prices(item: ShopItem): List<MenuPrice> {
        val cost = engine.price(item)
        val money = MenuPrice(if (cost == 0.0) "Free" else cost.compactDollars, state.money >= cost)
        val building = (item as? ShopItem.BuildingItem)?.let { Building.named(it.id) } ?: return listOf(money)
        return listOfNotNull(
            money,
            building.logCost.takeIf { it > 0 }?.let { needs("logs", it, StorageItem.Log) },
            building.stoneCost.takeIf { it > 0 }?.let { needs("stones", it, StorageItem.Stone) },
        )
    }

    private fun needs(
        noun: String,
        amount: Int,
        item: StorageItem,
    ) = MenuPrice("${MenuNumbers.count(amount)} $noun", (state.storage[item.id] ?: 0) >= amount)

    private fun picture(item: ShopItem): MenuPicture =
        when (item) {
            is ShopItem.BuildingItem -> {
                MenuPictures.building(building(item.id))
            }

            is ShopItem.DecorationItem -> {
                Decoration.named(item.id)!!.let {
                    MenuPictures.decoration(it, it.offeredSize(state.selectedDecorationSizes[it.id]))
                }
            }

            is ShopItem.Tree -> {
                engine.tree(item.id)!!.let { MenuPictures.tree(it, it.color(state.selectedTreeColorID)) }
            }

            is ShopItem.GroundCoverItem -> {
                MenuPicture.Ground(item.cover)
            }

            ShopItem.Farmland -> {
                MenuPictures.terrain("farmland")
            }

            ShopItem.Water -> {
                MenuPicture.Water(state.selectedWaterStyle)
            }

            ShopItem.Fence -> {
                MenuPictures.fence(engine.selectedFenceColor)
            }

            else -> {
                error("Not a menu item: $item")
            }
        }

    private fun building(id: String): Building = Building.named(id)!!.styled(state.selectedBuildingStyleIDs[id])

    private fun variants(item: ShopItem): Pair<String, List<MenuChoice>> =
        when (item) {
            is ShopItem.BuildingItem -> {
                "Styles" to
                    Building.named(item.id)!!.let { building ->
                        building.styles.takeIf { it.size > 1 }.orEmpty().map { style ->
                            MenuChoice(
                                id = style.id,
                                title = style.name,
                                picture = MenuPictures.building(building.styled(style.id)),
                                selection = MenuSelection.Style(building, style),
                                selected = this.building(item.id).style == style,
                            )
                        }
                    }
            }

            is ShopItem.DecorationItem -> {
                "Sizes" to
                    Decoration.named(item.id)!!.let { decoration ->
                        if (decoration.sizeNames.size <= 1 || decoration.offeredSizes.size <= 1) {
                            emptyList()
                        } else {
                            decoration.offeredSizes.map { size ->
                                val selected =
                                    state.selectedDecorationID == item.id && engine.selectedDecorationSize == size
                                MenuChoice(
                                    id = size.toString(),
                                    title = decoration.sizeNames[size],
                                    picture = MenuPictures.decoration(decoration, size),
                                    selection = MenuSelection.Size(decoration, size),
                                    selected = selected,
                                )
                            }
                        }
                    }
            }

            is ShopItem.Tree -> {
                "Colors" to
                    engine.tree(item.id)!!.let { tree ->
                        tree.species.colors.map { color ->
                            MenuChoice(
                                id = color.id,
                                title = color.name,
                                picture = MenuPictures.tree(tree, color),
                                selection = MenuSelection.Tree(tree.species, color),
                                selected = state.selectedTreeID == tree.id && engine.selectedTreeColor == color,
                            )
                        }
                    }
            }

            ShopItem.Water -> {
                "Styles" to
                    WaterStyle.entries.map { style ->
                        MenuChoice(
                            id = style.name,
                            title = style.displayName,
                            picture = MenuPicture.Water(style),
                            selection = MenuSelection.Water(style),
                            selected = state.selectedWaterStyle == style,
                        )
                    }
            }

            ShopItem.Fence -> {
                "Colors" to
                    FenceColor.all.takeIf { it.size > 1 }.orEmpty().map { color ->
                        MenuChoice(
                            id = color.id,
                            title = color.name,
                            picture = MenuPictures.fence(color),
                            selection = MenuSelection.Fence(color),
                            subtitle = "${FenceColor.price.dollars} each",
                            selected = engine.selectedFenceColor == color,
                        )
                    }
            }

            else -> {
                "" to emptyList()
            }
        }
}
