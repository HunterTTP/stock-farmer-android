@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hunter.stockfarmer.ui.menus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.ChoiceRow
import com.hunter.stockfarmer.ui.theme.HudChoice
import com.hunter.stockfarmer.ui.theme.HudSheet
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.MoneyBadge
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard
import com.hunter.stockfarmer.ui.theme.newDot
import kotlinx.coroutines.delay

/** The caller owns which menu is open; a successful selection returns the player to the farm. */
@Composable
fun MenusHost(
    session: GameSession,
    openMenu: Menu,
    onClose: () -> Unit,
) {
    var state by remember(session, openMenu) { mutableStateOf(session.menuState(openMenu)) }
    var message by remember(session, openMenu) { mutableStateOf<String?>(null) }
    var transaction by remember(session, openMenu) { mutableStateOf<StorageTransaction?>(null) }
    var detail by remember(session, openMenu) { mutableStateOf<ItemScreen?>(null) }
    val loader = rememberMenuAssetLoader()
    LaunchedEffect(session, openMenu) {
        while (true) {
            state = session.menuState(openMenu)
            delay(250)
        }
    }
    val choose: (MenuSelection) -> Unit = { selection ->
        val rejection = session.act { MenuActions.choose(it, selection) }
        state = session.menuState(openMenu)
        if (rejection == null) onClose() else message = rejection.message
    }
    detail?.let { screen ->
        MenusHost(session, screen, onClose = { detail = null }, onSelected = onClose)
        return
    }
    HudSheet(onDismiss = onClose) {
        Column(Modifier.fillMaxHeight()) {
            MenuHeader(openMenu, state)
            if (openMenu == Menu.Storage) {
                StorageMenuScreen(
                    state = state,
                    loader = loader,
                    onChoose = choose,
                    onTransact = { transaction = it },
                    modifier = Modifier.weight(1f),
                )
            } else {
                CatalogMenuScreen(state, loader, choose, onInfo = { detail = it }, Modifier.weight(1f))
            }
            message?.let { text ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        text,
                        Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text("Close")
            }
        }
    }
    transaction?.let { action ->
        StorageTransactionDialog(
            session = session,
            transaction = action,
            stored = state.stored.firstOrNull { it.item == action.item }?.count ?: 0,
            loader = loader,
            onDismiss = { transaction = null },
            onConfirm = { quantity ->
                message = session.act { MenuActions.transact(it, action.item, quantity, action.sell) }
                state = session.menuState(openMenu)
                transaction = null
            },
        )
    }
}

private fun GameSession.menuState(menu: Menu): MenuState = read { MenuState.from(it, menu) }

@Composable
private fun MenuHeader(
    menu: Menu,
    state: MenuState,
) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(menu.title, style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.money, style = MaterialTheme.typography.titleMedium)
            Text("Level ${state.level}")
        }
    }
}

@Composable
private fun CatalogMenuScreen(
    state: MenuState,
    loader: MenuAssetLoader,
    onChoose: (MenuSelection) -> Unit,
    onInfo: (ItemScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.sections.filter { it.choices.isNotEmpty() }.forEach { section ->
            item(key = section.title) {
                Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
            }
            items(section.choices, key = { it.id }) { choice ->
                CatalogChoiceCard(choice, loader, onChoose, onInfo)
            }
        }
    }
}

@Composable
private fun CatalogChoiceCard(
    choice: MenuChoice,
    loader: MenuAssetLoader,
    onChoose: (MenuSelection) -> Unit,
    onInfo: (ItemScreen) -> Unit,
) {
    var expanded by rememberSaveable(choice.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().categoryCard(choice.selected)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChoiceRow(
                HudChoice(
                    id = choice.id,
                    title = choice.title,
                    onPick = { onChoose(choice.selection) },
                    subtitle = choice.subtitle,
                    icon = { MenuImage(choice.picture, loader) },
                    priceLine =
                        if (choice.prices.isEmpty()) {
                            null
                        } else {
                            {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    choice.prices.forEach { price ->
                                        Text(
                                            price.text,
                                            style = HudType.caption,
                                            color = if (price.affordable) Theme.accent else Theme.spend,
                                        )
                                    }
                                }
                            }
                        },
                    isSelected = choice.selected,
                    isLocked = choice.requiredLevel != null,
                    isNew = choice.isNew,
                    isAffordable = choice.affordable,
                    isAtLimit = choice.atLimit,
                ),
                Modifier.weight(1f).selectable(choice.selected) { onChoose(choice.selection) },
            )
            val item = choice.selection.shopItem
            if (item != null || choice.selection == MenuSelection.Grass) {
                TextButton(onClick = { onInfo(item?.let { ItemScreen.Item(it) } ?: ItemScreen.Eraser) }) {
                    Text("ⓘ", color = Theme.accent)
                }
            }
        }
        if (choice.variants.isNotEmpty()) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("${if (expanded) "Hide" else "Show"} ${choice.variantTitle.lowercase()}")
            }
            if (expanded) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(choice.variants, key = { it.id }) { variant ->
                        FilterChip(
                            selected = variant.selected,
                            onClick = { onChoose(variant.selection) },
                            label = { Text(variant.title) },
                            leadingIcon = { MenuImage(variant.picture, loader, Modifier.size(28.dp)) },
                        )
                    }
                }
            }
        }
    }
}

/** HUD entry point for seed/fertilizer/tool info and tool upgrades; catalog info buttons use this host too. */
@Composable
fun MenusHost(
    session: GameSession,
    screen: ItemScreen,
    onClose: () -> Unit,
    onSelected: () -> Unit = onClose,
    onUpgradeResult: (ToolUpgradeResult) -> Unit = {},
) {
    var message by remember(session, screen) { mutableStateOf<String?>(null) }
    var money by remember(session, screen) { mutableStateOf(session.read { it.money }) }
    LaunchedEffect(session, screen) {
        while (true) {
            money = session.read { it.money }
            delay(250)
        }
    }
    HudSheet(onDismiss = onClose) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (screen) {
                    is ItemScreen.Item -> {
                        session.act { ItemInfo.from(screen.item, it).name }
                    }

                    ItemScreen.Eraser -> {
                        "Grass"
                    }

                    is ItemScreen.Harvest -> {
                        screen.tool.displayName
                    }

                    is ItemScreen.Upgrade -> {
                        "Upgrade ${screen.tool.displayName}"
                    }
                },
                Modifier.weight(1f),
                style = HudType.headline,
            )
            MoneyBadge(money)
            TextButton(onClick = onClose) { Text("Close") }
        }
        Column(Modifier.weight(1f)) {
            when (screen) {
                is ItemScreen.Item -> {
                    ItemDetailScreen(session, screen.item, onSelected, { message = it })
                }

                ItemScreen.Eraser -> {
                    EraserDetailScreen(session, onSelected)
                }

                is ItemScreen.Harvest -> {
                    HarvestToolDetailScreen(session, screen.tool, onSelected)
                }

                is ItemScreen.Upgrade -> {
                    ToolUpgradeScreen(session, screen.tool, onBought = {
                        onUpgradeResult(ToolUpgradeResult(true, it))
                        onClose()
                    }, onMessage = {
                        message = it
                        onUpgradeResult(ToolUpgradeResult(false, it))
                    })
                }
            }
        }
        message?.let { Text(it, Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}
