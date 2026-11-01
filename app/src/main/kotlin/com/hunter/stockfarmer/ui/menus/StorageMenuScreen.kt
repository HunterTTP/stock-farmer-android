package com.hunter.stockfarmer.ui.menus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.BACKPACK_CAPACITY
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.saleValue
import com.hunter.stockfarmer.game.GameSession

internal data class StorageTransaction(
    val item: StorageItem,
    val sell: Boolean,
) {
    val title: String get() = if (sell) "Sell" else item.discardTitle
}

@Composable
internal fun StorageMenuScreen(
    state: MenuState,
    loader: MenuAssetLoader,
    onChoose: (MenuSelection) -> Unit,
    onTransact: (StorageTransaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "capacity") {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.capacityText, style = MaterialTheme.typography.titleLarge)
                    LinearProgressIndicator(
                        progress = { state.capacityProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color =
                            if (state.used >= state.capacity) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                    Text(
                        "Your backpack holds $BACKPACK_CAPACITY; each Shed adds 50 and each Silo adds 150",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (state.stored.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nothing stored yet. Drag the Harvest tool over crops, trees, rocks, eggs and animals " +
                        "to gather them.",
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(state.stored, key = { it.item.id }) { row ->
            StoredItemCard(row, loader, onChoose, onTransact)
        }
    }
}

@Composable
private fun StoredItemCard(
    row: StoredRow,
    loader: MenuAssetLoader,
    onChoose: (MenuSelection) -> Unit,
    onTransact: (StorageTransaction) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MenuImage(row.picture, loader)
                Text(row.item.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(row.countText)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (row.item.isEquippable) {
                    OutlinedButton(
                        onClick = { onChoose(MenuSelection.Stored(row.item)) },
                        modifier = Modifier.semantics { contentDescription = "Select ${row.item.name}" },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Text(if (row.selected) "Selected" else "Select")
                    }
                }
                if (row.item.isSellable) {
                    Button(
                        onClick = { onTransact(StorageTransaction(row.item, sell = true)) },
                        modifier = Modifier.semantics { contentDescription = "Sell ${row.item.name}" },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) { Text("Sell") }
                }
                TextButton(
                    onClick = { onTransact(StorageTransaction(row.item, sell = false)) },
                    modifier = Modifier.semantics { contentDescription = "${row.item.discardTitle} ${row.item.name}" },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) { Text(row.item.discardTitle, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
internal fun StorageTransactionDialog(
    session: GameSession,
    transaction: StorageTransaction,
    stored: Int,
    loader: MenuAssetLoader,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var requested by remember(transaction) { mutableIntStateOf(1) }
    val quantity = MenuNumbers.quantity(requested, stored)
    val item = transaction.item
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${transaction.title} ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuImage(MenuPictures.stored(item), loader, Modifier.size(64.dp).align(Alignment.CenterHorizontally))
                Text("${MenuNumbers.count(stored)} in storage")
                QuantityControls(quantity, stored) { requested = it }
                if (transaction.sell) {
                    val prices =
                        session.read {
                            val engine = GameEngine(it)
                            engine.saleValue(item, 1).dollars to engine.saleValue(item, quantity).dollars
                        }
                    Text("Price each: ${prices.first}")
                    Text("Total: ${prices.second}", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("${item.discardedTitle} items are gone for good and pay nothing")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(quantity) }, enabled = quantity > 0) {
                Text("${transaction.title} ${MenuNumbers.count(quantity)}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun QuantityControls(
    quantity: Int,
    maximum: Int,
    onChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onChange(quantity - 1) }, enabled = quantity > 1) { Text("−") }
            Text(MenuNumbers.count(quantity), Modifier.align(Alignment.CenterVertically))
            TextButton(onClick = { onChange(quantity + 1) }, enabled = quantity < maximum) { Text("+") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onChange(1) }, enabled = maximum > 0) { Text("Min") }
            TextButton(onClick = { onChange(maximum) }, enabled = maximum > 0) { Text("Max") }
        }
    }
}
