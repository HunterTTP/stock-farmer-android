@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hunter.stockfarmer.ui.menus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.rules.currentLesson
import com.hunter.stockfarmer.core.rules.isNew
import com.hunter.stockfarmer.core.rules.learn
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.learnLesson
import com.hunter.stockfarmer.ui.FAST_POLL_MILLIS
import com.hunter.stockfarmer.ui.rememberPolledState
import com.hunter.stockfarmer.ui.theme.BuildingPriceLine
import com.hunter.stockfarmer.ui.theme.ChoiceRow
import com.hunter.stockfarmer.ui.theme.HudChoice
import com.hunter.stockfarmer.ui.theme.HudIcon
import com.hunter.stockfarmer.ui.theme.HudIcons
import com.hunter.stockfarmer.ui.theme.HudSheet
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.MoneyPriceLine
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.ToastView
import com.hunter.stockfarmer.ui.theme.categoryCard
import com.hunter.stockfarmer.ui.theme.glassCard
import com.hunter.stockfarmer.ui.theme.lessonPulse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** The caller owns which menu is open; a successful selection returns the player to the farm. */
@Composable
fun MenusHost(
    session: GameSession,
    openMenu: Menu,
    onClose: () -> Unit,
    onToast: (String) -> Unit = {},
) {
    val polledState = rememberPolledState(session to openMenu, FAST_POLL_MILLIS) { session.menuState(openMenu) }
    var state by polledState
    var message by remember(session, openMenu) { mutableStateOf<String?>(null) }
    var messageIsRejection by remember(session, openMenu) { mutableStateOf(false) }
    var transaction by remember(session, openMenu) { mutableStateOf<StorageTransaction?>(null) }
    var detail by remember(session, openMenu) { mutableStateOf<ItemScreen?>(null) }
    var toastText by remember(session) { mutableStateOf<String?>(null) }
    val loader = rememberMenuAssetLoader()

    LaunchedEffect(openMenu) {
        if (openMenu == Menu.Storage) {
            session.act { it.learn(Lesson.openStorage) }
            polledState.refresh()
        }
    }
    LaunchedEffect(session) {
        session.toasts.collectLatest { toast ->
            toastText = toast.text
            delay(toast.durationMillis)
            if (toastText == toast.text) toastText = null
        }
    }
    val choose: (MenuSelection) -> Unit = { selection ->
        val rejection = session.act { MenuActions.choose(it, selection) }
        polledState.refresh()
        if (rejection == null) {
            onClose()
        } else {
            message = rejection.message
            messageIsRejection = true
        }
    }
    detail?.let { screen ->
        MenusHost(session, screen, onClose = { detail = null }, onSelected = onClose)
        return
    }

    val isStorage = openMenu == Menu.Storage
    val pulsesClose = isStorage && state.currentLesson == Lesson.closeStorage
    val closeWithLesson = {
        if (isStorage && session.learnLesson(Lesson.closeStorage)) {
            val msg = "Have fun building your farm!"
            onToast(msg)
            session.showToast(msg)
        }
        onClose()
    }

    HudSheet(onDismiss = closeWithLesson) {
        Column(Modifier.fillMaxHeight()) {
            ToastView(message = toastText, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
            MenuHeader(openMenu, state)
            if (openMenu == Menu.Storage) {
                StorageMenuScreen(
                    state = state,
                    loader = loader,
                    onChoose = choose,
                    onTransact = { action ->
                        if (action.sell) {
                            val isLesson = state.stored.firstOrNull { it.item == action.item }?.isLesson == true
                            if (isLesson) {
                                session.act { it.learn(Lesson.sellItem) }
                                polledState.refresh()
                            }
                        }
                        transaction = action
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                CatalogMenuScreen(state, loader, choose, onInfo = { detail = it }, Modifier.weight(1f))
            }
            message?.let { MenuMessageBox(it, messageIsRejection) }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .lessonPulse(pulsesClose, shape = RoundedCornerShape(16.dp))
                        .glassCard(16.dp)
                        .clickable(role = Role.Button, onClick = closeWithLesson)
                        .heightIn(min = 52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Close", style = HudType.headline, color = Theme.textPrimary)
            }
        }
    }
    transaction?.let { action ->
        StorageTransactionDialog(
            session = session,
            transaction = action,
            stored = state.stored.firstOrNull { it.item == action.item }?.count ?: 0,
            loader = loader,
            currentLesson = state.currentLesson,
            onDismiss = { transaction = null },
            onConfirm = { quantity ->
                val outcome = session.act { MenuActions.transact(it, action.item, quantity, action.sell) }
                message = outcome.message
                messageIsRejection = outcome.events.isEmpty()
                session.publish(outcome.events)
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
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(menu.title, style = HudType.headline, color = Theme.textPrimary)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(state.money, style = HudType.title3.copy(fontWeight = FontWeight.Bold), color = Theme.money)
            Text("Level ${state.level}", style = HudType.subheadline, color = Theme.textSecondary)
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
                Text(
                    section.title,
                    style = HudType.headline.copy(fontWeight = FontWeight.Bold),
                    color = Theme.textPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
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
                    priceLine = catalogPriceLine(choice, loader),
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
                Box(
                    modifier =
                        Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .glassCard(10.dp)
                            .clickable(role = Role.Button) {
                                onInfo(item?.let { ItemScreen.Item(it) } ?: ItemScreen.Eraser)
                            }.semantics { contentDescription = "About ${choice.title}" },
                    contentAlignment = Alignment.Center,
                ) {
                    HudIcon(HudIcons.infoCircle, Theme.accent, size = 20.dp)
                }
            }
        }
        if (choice.variants.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .glassCard(10.dp)
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "${if (expanded) "Hide" else "Show"} ${choice.variantTitle.lowercase()}",
                    style = HudType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = Theme.accent,
                )
            }
            if (expanded) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(choice.variants, key = { it.id }) { variant ->
                        Box(
                            modifier =
                                Modifier
                                    .glassCard(12.dp, tint = if (variant.selected) Theme.accent else null)
                                    .clickable(role = Role.RadioButton) { onChoose(variant.selection) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MenuImage(variant.picture, loader, Modifier.size(28.dp))
                                Text(
                                    variant.title,
                                    style = HudType.caption.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (variant.selected) Theme.accent else Theme.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun catalogPriceLine(
    choice: MenuChoice,
    loader: MenuAssetLoader,
): (@Composable () -> Unit)? {
    val money = choice.prices.firstOrNull() ?: return null
    val building =
        (choice.selection.shopItem as? ShopItem.BuildingItem)?.let { Building.named(it.id) }
            ?: return { MoneyPriceLine(money.text, money.affordable) }
    val logs = choice.prices.getOrNull(1).takeIf { building.logCost > 0 }
    val stones = choice.prices.getOrNull(if (building.logCost > 0) 2 else 1).takeIf { building.stoneCost > 0 }
    return {
        BuildingPriceLine(
            moneyText = money.text,
            hasMoney = money.affordable,
            logs = building.logCost,
            hasLogs = logs?.affordable ?: true,
            logIcon = { MenuImage(MenuPictures.stored(StorageItem.Log), loader) },
            stones = building.stoneCost,
            hasStones = stones?.affordable ?: true,
            stoneIcon = { MenuImage(MenuPictures.stored(StorageItem.Stone), loader) },
        )
    }
}

/** The line under a menu: what a purchase or sale came to, in the error style when it was refused. */
@Composable
internal fun MenuMessageBox(
    text: String,
    isRejection: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .glassCard(12.dp, tint = if (isRejection) Theme.spend else null)
                .padding(14.dp),
    ) {
        Text(
            text,
            style = HudType.body,
            color = if (isRejection) Theme.spend else Theme.textPrimary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
