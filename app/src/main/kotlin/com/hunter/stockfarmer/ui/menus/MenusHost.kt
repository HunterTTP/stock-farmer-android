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
import com.hunter.stockfarmer.game.GameSession
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
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column {
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
                    CatalogMenuScreen(state, loader, choose, Modifier.weight(1f))
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
                CatalogChoiceCard(choice, loader, onChoose)
            }
        }
    }
}

@Composable
private fun CatalogChoiceCard(
    choice: MenuChoice,
    loader: MenuAssetLoader,
    onChoose: (MenuSelection) -> Unit,
) {
    var expanded by rememberSaveable(choice.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = choice.selected, onClick = { onChoose(choice.selection) })
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuImage(choice.picture, loader, Modifier.alpha(if (choice.requiredLevel == null) 1f else 0.45f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(choice.title, style = MaterialTheme.typography.titleSmall)
                if (choice.subtitle.isNotEmpty()) {
                    Text(choice.subtitle, style = MaterialTheme.typography.bodySmall)
                }
                choice.prices.forEach { price ->
                    Text(
                        price.text,
                        color =
                            if (price.affordable) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (choice.atLimit) Text("Placement limit reached", style = MaterialTheme.typography.bodySmall)
            }
            if (choice.requiredLevel != null) {
                Text("Locked", style = MaterialTheme.typography.labelSmall)
            } else if (choice.selected) {
                Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
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
