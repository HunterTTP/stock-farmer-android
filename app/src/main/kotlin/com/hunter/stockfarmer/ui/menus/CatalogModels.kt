package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.core.rules.tree

/** Detail pages offer colors; building styles and decoration sizes belong to the tool's dropdowns. */
internal fun itemDetailColors(
    engine: GameEngine,
    item: ShopItem,
): List<MenuChoice> {
    if (item != ShopItem.Water && item != ShopItem.Fence && item !is ShopItem.Tree) return emptyList()
    val preferredTreeColor =
        (item as? ShopItem.Tree)?.let {
            engine.tree(it.id)?.color(engine.state.selectedTreeColorID)?.id
        }
    return MenuCatalog(engine).choice(item).variants.map { variant ->
        val tree = variant.selection as? MenuSelection.Tree
        if (tree == null) variant else variant.copy(selected = tree.color.id == preferredTreeColor)
    }
}

internal fun selectItemDetailColor(
    engine: GameEngine,
    selection: MenuSelection,
) {
    when (selection) {
        is MenuSelection.Water -> engine.select(selection.style)
        is MenuSelection.Fence -> engine.select(selection.color)
        is MenuSelection.Tree -> engine.select(selection.color)
        else -> error("Not an item detail color")
    }
}
