package com.hunter.stockfarmer.ui.hud

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.Rejection
import com.hunter.stockfarmer.core.rules.equippedStock
import com.hunter.stockfarmer.core.rules.isSelling
import com.hunter.stockfarmer.core.rules.level
import com.hunter.stockfarmer.core.rules.name
import com.hunter.stockfarmer.core.rules.offersUpgrade
import com.hunter.stockfarmer.core.rules.price
import com.hunter.stockfarmer.core.rules.requiredLevel
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.core.rules.selectFertilizer
import com.hunter.stockfarmer.core.rules.setSelling
import com.hunter.stockfarmer.ui.menus.Menu
import com.hunter.stockfarmer.ui.menus.MenuActions
import com.hunter.stockfarmer.ui.menus.MenuChoice
import com.hunter.stockfarmer.ui.menus.MenuPicture
import com.hunter.stockfarmer.ui.menus.MenuPictures
import com.hunter.stockfarmer.ui.menus.MenuPrice
import com.hunter.stockfarmer.ui.menus.MenuSelection
import com.hunter.stockfarmer.ui.menus.MenuState
import com.hunter.stockfarmer.ui.menus.UnlockCategory
import com.hunter.stockfarmer.ui.menus.hasNewItems
import com.hunter.stockfarmer.ui.menus.isNew
import com.hunter.stockfarmer.ui.menus.markSeen
import com.hunter.stockfarmer.ui.menus.shopItem
import com.hunter.stockfarmer.ui.screens.LessonGuideState
import java.util.Locale

/** What picking a row of a tool's list does. */
internal sealed interface PaletteAction {
    data class FromMenu(
        val selection: MenuSelection,
    ) : PaletteAction

    data class Seed(
        val crop: Crop,
    ) : PaletteAction

    data class Implement(
        val tool: HarvestTool,
    ) : PaletteAction

    data object Fertilizer : PaletteAction

    data class Destination(
        val isSelling: Boolean,
    ) : PaletteAction
}

/** One row in a tool's list of choices (iOS `ToolChoice`), reduced to what the row draws. */
internal data class ToolChoice(
    val id: String,
    val title: String,
    val action: PaletteAction,
    val picture: MenuPicture? = null,
    val subtitle: String = "",
    val prices: List<MenuPrice> = emptyList(),
    /** Set for an unlocked seed, whose row shows how long it grows, what it pays and its XP. */
    val crop: Crop? = null,
    /** Set for a building, whose row shows money, logs and stones. */
    val building: Building? = null,
    val isSelected: Boolean = false,
    val requiredLevel: Int? = null,
    val isAffordable: Boolean = true,
    val isAtLimit: Boolean = false,
    val levelText: String? = null,
    val toolLevel: Int? = null,
    /** Swells to point the first-time walk through at this choice. */
    val pulses: Boolean = false,
    val isNew: Boolean = false,
    val detail: ToolRoute? = null,
    val upgrade: ToolRoute? = null,
)

/** A dropdown beside the tool circle: what is chosen now, and the small list it opens to change it. */
internal data class ToolDropdown(
    val id: String,
    val value: String,
    val choices: List<ToolChoice>,
    /** How many of the equipped item are left in storage, shown after its name; null when it is not set down from storage. */
    val stock: Int? = null,
    /** The unlock dot; while the walk through runs it marks only what the walk through points at. */
    val hasNew: Boolean = false,
    val pulses: Boolean = false,
) {
    val selected: ToolChoice? get() = choices.firstOrNull { it.isSelected }
}

/** Builds the dropdowns each tool offers from the game state (iOS `ToolPalette`). Nothing here changes the game. */
internal object ToolPalette {
    private val fertilizerPicture = MenuPicture.Asset("images/tools/fertilizer.png")

    fun dropdowns(
        state: GameState,
        tool: HudTool,
    ): List<ToolDropdown> {
        val engine = GameEngine(state)
        return when (tool) {
            HudTool.Planting -> planting(engine)
            HudTool.Harvest -> harvest(engine)
            HudTool.Tilling -> tilling(engine)
            HudTool.Terrain -> catalog(state, Menu.Terrain, "Terrain", setOf("Ground", "Trees"))
            HudTool.Building -> catalog(state, Menu.Build, "Build", setOf("Buildings"))
            HudTool.Decorating -> catalog(state, Menu.Decorate, "Decorate", setOf("Decorations", "Stored items"))
        }
    }

    /** The picture on the tool circle: what the tool holds right now. */
    fun picture(
        state: GameState,
        tool: HudTool,
    ): MenuPicture =
        when (tool) {
            HudTool.Harvest -> harvestPicture(state.harvestTool)
            else -> dropdowns(state, tool).firstOrNull()?.selected?.picture ?: menuPicture(tool)
        }

    /** The picture on the ring's tile for a tool, which stays the same whatever the tool holds. */
    fun menuPicture(tool: HudTool): MenuPicture =
        when (tool) {
            HudTool.Planting -> MenuPictures.stored(StorageItem.CropItem(Crop.starter))
            HudTool.Harvest -> harvestPicture(HarvestTool.scythe)
            HudTool.Tilling -> fertilizerPicture
            HudTool.Terrain -> MenuPictures.terrain("farmland")
            HudTool.Building -> MenuPictures.building(Building.starter)
            HudTool.Decorating -> MenuPictures.decoration(requireNotNull(Decoration.named("scarecrow")))
        }

    fun harvestPicture(tool: HarvestTool): MenuPicture =
        when (tool) {
            HarvestTool.scythe -> MenuPicture.Asset("images/tools/harvester.png")
            HarvestTool.axe -> MenuPicture.Asset("images/tools/axe.png")
            HarvestTool.pickaxe -> MenuPicture.Asset("images/tools/pickaxe.png")
            HarvestTool.bucket -> MenuPicture.Asset("images/tools/bucket.png")
            HarvestTool.rod -> MenuPicture.Symbol("🎣")
        }

    /** Applies a pick. A row that is not unlocked yet only says what unlocks it. */
    fun perform(
        engine: GameEngine,
        action: PaletteAction,
    ): Rejection? =
        when (action) {
            is PaletteAction.FromMenu -> {
                MenuActions.choose(engine, action.selection)
            }

            is PaletteAction.Seed -> {
                val item = ShopItem.Seed(action.crop.id)
                val needed = engine.requiredLevel(item)
                if (needed == null) {
                    engine.select(action.crop)
                    engine.markSeen(item)
                }
                needed?.let { Rejection.NeedLevel(it) }
            }

            is PaletteAction.Implement -> {
                engine.select(action.tool)
                null
            }

            PaletteAction.Fertilizer -> {
                engine.selectFertilizer()
                null
            }

            is PaletteAction.Destination -> {
                engine.setSelling(action.isSelling, engine.state.harvestTool)
                null
            }
        }

    /** The count after a dropdown's name, "(x12)". */
    fun stockSuffix(stock: Int): String = " (x${String.format(Locale.US, "%,d", stock)})"

    private fun planting(engine: GameEngine): List<ToolDropdown> {
        val isPlanting = engine.state.mode == PlayMode.plant
        val choices =
            Crop.all.map { crop ->
                val item = ShopItem.Seed(crop.id)
                val needed = engine.requiredLevel(item)
                ToolChoice(
                    id = crop.id,
                    title = engine.name(item),
                    action = PaletteAction.Seed(crop),
                    picture = MenuPictures.stored(StorageItem.CropItem(crop)),
                    subtitle = needed?.let { "Unlocks at Level $it" } ?: "",
                    crop = crop.takeIf { needed == null },
                    isSelected = isPlanting && crop == engine.selectedCrop,
                    requiredLevel = needed,
                    isAffordable = engine.state.money >= engine.price(item),
                    isNew = engine.isNew(item),
                    detail = ToolRoute.Item(item),
                )
            }
        return listOf(
            ToolDropdown(
                "Plant",
                engine.name(ShopItem.Seed(engine.selectedCrop.id)),
                choices,
                hasNew = engine.hasNewItems(UnlockCategory.PLANT),
            ),
        )
    }

    private fun harvest(engine: GameEngine): List<ToolDropdown> {
        val needsScythe = LessonGuideState.from(engine.state).needsScythe
        val held = engine.state.harvestTool
        val isHarvesting = engine.state.mode == PlayMode.harvest
        val implements =
            HarvestTool.entries.map { tool ->
                val level = engine.level(tool)
                ToolChoice(
                    id = tool.displayName,
                    title = tool.displayName,
                    action = PaletteAction.Implement(tool),
                    picture = harvestPicture(tool),
                    isSelected = isHarvesting && tool == held,
                    levelText = "Level $level",
                    toolLevel = level,
                    pulses = tool == HarvestTool.scythe && needsScythe,
                    detail = ToolRoute.HarvestToolDetail(tool),
                    upgrade = ToolRoute.ToolUpgrade(tool).takeIf { engine.offersUpgrade(tool) },
                )
            }
        val destination =
            if (!held.canSell) {
                emptyList()
            } else {
                val isSelling = engine.isSelling(held)
                listOf(
                    ToolDropdown(
                        "harvestDestination",
                        if (isSelling) "Sell" else "Store",
                        listOf(
                            ToolChoice(
                                "store",
                                "Store",
                                PaletteAction.Destination(false),
                                subtitle = "Keep it in storage to sell later",
                                isSelected = !isSelling,
                            ),
                            ToolChoice(
                                "sell",
                                "Sell",
                                PaletteAction.Destination(true),
                                subtitle = "Sell it the moment it is gathered",
                                isSelected = isSelling,
                            ),
                        ),
                    ),
                )
            }
        return listOf(
            ToolDropdown("Harvest", held.displayName, implements, hasNew = needsScythe, pulses = needsScythe),
        ) +
            destination
    }

    private fun tilling(engine: GameEngine): List<ToolDropdown> {
        val state = engine.state
        val sprinklers =
            MenuState
                .from(state, Menu.Decorate)
                .sections
                .firstOrNull { it.title == "Sprinklers" }
                ?.choices
                .orEmpty()
        val fertilizer =
            ToolChoice(
                id = "Fertilizer",
                title = engine.name(ShopItem.Fertilizer),
                action = PaletteAction.Fertilizer,
                picture = fertilizerPicture,
                isSelected = state.mode == PlayMode.fertilize,
                isAffordable = state.money >= engine.price(ShopItem.Fertilizer),
                detail = ToolRoute.Item(ShopItem.Fertilizer),
            )
        val choices = listOf(fertilizer) + sprinklers.map { it.toToolChoice() }
        val held = choices.firstOrNull { it.isSelected } ?: fertilizer
        val stock = if (held === fertilizer) null else engine.equippedStock
        return listOf(
            ToolDropdown(
                "Fertilize",
                held.title,
                choices,
                stock,
                hasNew = engine.hasNewItems(UnlockCategory.FERTILIZE),
            ),
        )
    }

    /** The Terrain, Build and Decorate lists come from the menus the screen already has, with their variants as more dropdowns. */
    private fun catalog(
        state: GameState,
        menu: Menu,
        id: String,
        sections: Set<String>,
    ): List<ToolDropdown> {
        val choices =
            MenuState
                .from(state, menu)
                .sections
                .filter { it.title in sections }
                .flatMap { it.choices }
        val selected = choices.firstOrNull { it.selected }
        val stock = if (menu == Menu.Decorate) GameEngine(state).equippedStock else null
        val main =
            ToolDropdown(
                id,
                selected?.title ?: choices.firstOrNull()?.title.orEmpty(),
                choices.map { it.toToolChoice() },
                stock,
                hasNew = choices.any { it.isNew },
            )
        val variants = selected?.variants.orEmpty()
        if (selected == null || variants.isEmpty()) return listOf(main)
        val chosen = variants.firstOrNull { it.selected } ?: variants.first()
        return listOf(main, ToolDropdown(selected.variantTitle, chosen.title, variants.map { it.toToolChoice() }))
    }

    private fun MenuChoice.toToolChoice() =
        ToolChoice(
            id = id,
            title = title,
            action = PaletteAction.FromMenu(selection),
            picture = picture,
            subtitle = subtitle,
            prices = prices,
            building =
                ((selection as? MenuSelection.Shop)?.item as? ShopItem.BuildingItem)?.let {
                    Building.named(
                        it.id,
                    )
                },
            isSelected = selected,
            requiredLevel = requiredLevel,
            isAffordable = affordable,
            isAtLimit = atLimit,
            isNew = isNew,
            detail =
                selection.shopItem?.let(
                    ToolRoute::Item,
                ) ?: ToolRoute.Eraser.takeIf { selection == MenuSelection.Grass },
        )
}
