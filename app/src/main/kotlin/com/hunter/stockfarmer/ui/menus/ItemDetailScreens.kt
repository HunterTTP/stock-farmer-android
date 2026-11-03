package com.hunter.stockfarmer.ui.menus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.farmlandLimit
import com.hunter.stockfarmer.core.rules.isSelling
import com.hunter.stockfarmer.core.rules.markSeen
import com.hunter.stockfarmer.core.rules.placedOnMap
import com.hunter.stockfarmer.core.rules.requiredLevel
import com.hunter.stockfarmer.core.rules.select
import com.hunter.stockfarmer.core.rules.setSelling
import com.hunter.stockfarmer.core.rules.setSprinklersRunConstantly
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.FAST_POLL_MILLIS
import com.hunter.stockfarmer.ui.rememberPolledState
import com.hunter.stockfarmer.ui.sheets.SheetChoice
import com.hunter.stockfarmer.ui.theme.BarScrollView
import com.hunter.stockfarmer.ui.theme.DetailLayout
import com.hunter.stockfarmer.ui.theme.DetailStat
import com.hunter.stockfarmer.ui.theme.EffectsCard
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.LevelRequirementNotice
import com.hunter.stockfarmer.ui.theme.Requirement
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard

/** Public routes for the HUD's info and Upgrade buttons, hosted by MenusHost. */
sealed interface ItemScreen {
    data class Item(
        val item: ShopItem,
    ) : ItemScreen

    data object Eraser : ItemScreen

    data class Harvest(
        val tool: HarvestTool,
    ) : ItemScreen

    data class Upgrade(
        val tool: HarvestTool,
    ) : ItemScreen
}

internal data class ItemRequirement(
    val id: String,
    val label: String,
    val amount: String,
    val needed: String?,
    val enough: Boolean,
    val picture: MenuPicture,
)

internal data class ItemDetailState(
    val info: ItemInfo,
    val choice: MenuChoice,
    val stats: List<DetailStat>,
    val requirements: List<ItemRequirement>,
    val fountain: Boolean?,
    val colors: List<MenuChoice>,
) {
    companion object {
        fun from(
            engine: GameEngine,
            item: ShopItem,
        ): ItemDetailState {
            val info = ItemInfo.from(item, engine)
            val locked = engine.requiredLevel(item) != null
            val building = (item as? ShopItem.BuildingItem)?.let { Building.named(it.id) }
            val requirements =
                building
                    ?.let {
                        listOf(
                            material(engine, StorageItem.Log, "logs", "Logs", it.logCost),
                            material(engine, StorageItem.Stone, "stones", "Stones", it.stoneCost),
                            ItemRequirement(
                                "money",
                                "Money",
                                it.cost.dollars,
                                null,
                                engine.state.money >= it.cost,
                                MenuPicture.Symbol("$"),
                            ),
                        )
                    }.orEmpty()
            return ItemDetailState(
                info,
                MenuCatalog(engine).choice(item),
                if (locked) {
                    emptyList()
                } else {
                    buildList {
                        if (item == ShopItem.Farmland) add(DetailStat("Unlocked", engine.state.farmlandLimit))
                        add(DetailStat(info.placedLabel, engine.placedOnMap(item)))
                    }
                },
                requirements,
                engine.state.sprinklersRunConstantly.takeIf {
                    !locked && (item as? ShopItem.DecorationItem)?.let { Decoration.named(it.id)?.isSprinkler } == true
                },
                itemDetailColors(engine, item),
            )
        }

        private fun material(
            engine: GameEngine,
            item: StorageItem,
            id: String,
            label: String,
            cost: Int,
        ): ItemRequirement {
            val stored = engine.state.storage[item.id] ?: 0
            return ItemRequirement(
                id,
                label,
                MenuNumbers.count(stored),
                MenuNumbers.count(cost),
                stored >= cost,
                MenuPictures.stored(item),
            )
        }
    }
}

@Composable
fun ItemDetailScreen(
    session: GameSession,
    item: ShopItem,
    onSelect: () -> Unit,
    onMessage: (String) -> Unit = {},
) {
    val polledState =
        rememberPolledState(session to item, FAST_POLL_MILLIS) { session.act { ItemDetailState.from(it, item) } }
    val state by polledState
    val loader = rememberMenuAssetLoader()
    LaunchedEffect(session, item) {
        session.act { it.markSeen(item) }
        polledState.refresh()
    }
    DetailLayout(
        name = state.info.name,
        effects = state.info.effects,
        stats = state.stats,
        isHighlighted = state.choice.selected,
        dropdowns =
            if (state.colors.isEmpty()) {
                null
            } else {
                {
                    val variants = state.colors
                    SheetChoice(
                        "Color",
                        variants.firstOrNull { it.selected }?.title ?: variants.first().title,
                        variants,
                        { it.title },
                    ) { variant ->
                        session.act { selectItemDetailColor(it, variant.selection) }
                        polledState.refresh()
                    }
                }
            },
        requirements =
            state.requirements.map { requirement ->
                Requirement(
                    requirement.id,
                    requirement.label,
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = if (requirement.enough) Theme.accent else Theme.spend)) {
                            append(requirement.amount)
                        }
                        requirement.needed?.let { append(" / $it") }
                    },
                ) { MenuImage(requirement.picture, loader) }
            },
        onSelect =
            if (state.choice.requiredLevel != null) {
                null
            } else {
                {
                    val rejection = session.act { MenuActions.choose(it, MenuSelection.Shop(item)) }
                    if (rejection == null) onSelect() else onMessage(rejection.message)
                }
            },
        icon = { MenuImage(state.choice.picture, loader, Modifier.size(96.dp)) },
    ) {
        state.choice.requiredLevel?.let { LevelRequirementNotice(it) }
        state.fountain?.let { runs ->
            Row(Modifier.fillMaxWidth().categoryCard(false), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Always On", style = HudType.headline, color = Theme.textPrimary)
                    Text(
                        "Every sprinkler runs all the time, like a fountain",
                        style = HudType.caption,
                        color = Theme.textSecondary,
                    )
                }
                Switch(runs, { value ->
                    session.act { it.setSprinklersRunConstantly(value) }
                    polledState.refresh()
                }, colors = SwitchDefaults.colors(checkedTrackColor = Theme.accent))
            }
        }
    }
}

@Composable
fun EraserDetailScreen(
    session: GameSession,
    onSelect: () -> Unit,
) {
    val loader = rememberMenuAssetLoader()
    val stats = session.read { listOf(DetailStat("Placed", it.grassTileCount)) }
    DetailLayout(
        name = LandscapeTool.grass.name,
        effects = plain("Clears farmland or water back to grass", "Tiles with crops growing can't be cleared"),
        stats = stats,
        isHighlighted = session.act { it.isSelected(LandscapeTool.grass) },
        onSelect = {
            session.act { it.select(LandscapeTool.grass) }
            onSelect()
        },
        icon = { MenuImage(MenuPictures.terrain("grass"), loader, Modifier.size(96.dp)) },
        actions = {},
    )
}

@Composable
fun HarvestToolDetailScreen(
    session: GameSession,
    tool: HarvestTool,
    onSelect: () -> Unit,
) {
    var selling by remember(session, tool) { mutableStateOf(session.act { it.isSelling(tool) }) }
    DetailLayout(
        name = tool.displayName,
        effects = ToolInfo.effects(tool),
        stats = emptyList(),
        isHighlighted = session.read { it.mode == PlayMode.harvest && it.harvestTool == tool },
        dropdowns =
            if (!tool.canSell) {
                null
            } else {
                {
                    SheetChoice(
                        "Gathered items",
                        if (selling) "Sell" else "Store",
                        listOf(false, true),
                        { if (it) "Sell" else "Store" },
                    ) {
                        session.act { engine -> engine.setSelling(it, tool) }
                        selling = it
                    }
                }
            },
        onSelect = {
            session.act { it.select(tool) }
            onSelect()
        },
        icon = { HarvestToolImage(tool, Modifier.size(96.dp)) },
        actions = {},
    )
}

@Composable
fun ToolUpgradeScreen(
    session: GameSession,
    tool: HarvestTool,
    onBought: (String) -> Unit,
    onMessage: (String) -> Unit = {},
) {
    val state by rememberPolledState(session to tool, FAST_POLL_MILLIS) {
        session.act { ToolUpgradeState.from(it, tool) }
    }
    BarScrollView {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(
                Modifier.fillMaxWidth().categoryCard(false),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HarvestToolImage(tool, Modifier.size(120.dp))
                Text(state.title, style = HudType.title2.copy(fontWeight = FontWeight.Bold), color = Theme.textPrimary)
            }
            if (state.effects.isNotEmpty()) EffectsCard(state.effects)
            state.upgrade?.let { upgrade ->
                val level = state.requiredLevel
                if (level != null) {
                    LevelRequirementNotice(level)
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .alpha(if (state.affordable) 1f else 0.4f)
                            .background(Theme.xp, RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) {
                                val result = session.upgradeTool(tool)
                                if (result.bought) onBought(result.message) else onMessage(result.message)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Upgrade · ${upgrade.cost.dollars}",
                            style = HudType.headline.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HarvestToolImage(
    tool: HarvestTool,
    modifier: Modifier = Modifier,
) {
    val loader = rememberMenuAssetLoader()
    MenuImage(MenuPicture.Asset("images/tools/${tool.imageName}.png"), loader, modifier)
}
