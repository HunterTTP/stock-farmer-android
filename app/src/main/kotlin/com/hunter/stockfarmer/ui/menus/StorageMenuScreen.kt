package com.hunter.stockfarmer.ui.menus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.BACKPACK_CAPACITY
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.learn
import com.hunter.stockfarmer.core.rules.saleValue
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.HudIcon
import com.hunter.stockfarmer.ui.theme.HudIcons
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.QuantityControls
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard
import com.hunter.stockfarmer.ui.theme.glassCard
import com.hunter.stockfarmer.ui.theme.lessonPulse

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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "capacity") {
            CapacityCard(state, Modifier.padding(bottom = 8.dp))
        }
        if (state.stored.isEmpty()) {
            item(key = "empty") {
                Text(
                    text =
                        "Nothing stored yet. Drag the Harvest tool over crops, trees, rocks, eggs and animals " +
                            "to gather them.",
                    style = HudType.subheadline,
                    color = Theme.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
        items(state.stored, key = { it.item.id }) { row ->
            StoredItemCard(row, loader, onChoose, onTransact)
        }
    }
}

@Composable
private fun CapacityCard(
    state: MenuState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().categoryCard(isHighlighted = false),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HudIcon(HudIcons.bagFill, Color.White, size = 34.dp)
        Text(
            text = state.capacityText,
            style = HudType.title2.copy(fontWeight = FontWeight.ExtraBold),
            color = if (state.used >= state.capacity) Theme.spend else Theme.textPrimary,
        )
        LinearProgressIndicator(
            progress = { state.capacityProgress },
            modifier = Modifier.fillMaxWidth(),
            color = if (state.used >= state.capacity) Theme.spend else Theme.accent,
            trackColor = Color.White.copy(alpha = 0.15f),
        )
        Text(
            text = "Your backpack holds $BACKPACK_CAPACITY; each Shed adds 50 and each Silo adds 150",
            style = HudType.caption,
            color = Theme.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private val ACTION_BUTTON_WIDTH = 88.dp
private val ACTION_BUTTON_HEIGHT = 36.dp

@Composable
private fun StoredItemCard(
    row: StoredRow,
    loader: MenuAssetLoader,
    onChoose: (MenuSelection) -> Unit,
    onTransact: (StorageTransaction) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .lessonPulse(row.isLesson, shape = RoundedCornerShape(14.dp))
                .glassCard(14.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuImage(row.picture, loader, Modifier.size(40.dp))
            Text(
                text = row.item.name,
                style = HudType.headline,
                color = Theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = row.countText,
                style = HudType.headline.copy(fontWeight = FontWeight.ExtraBold),
                color = Theme.textSecondary,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.item.isEquippable) {
                ActionButton(
                    title = "Select",
                    textColor = Theme.onNeutral,
                    background = Color.White.copy(alpha = 0.18f),
                    contentDescription = "Select ${row.item.name}",
                    onClick = { onChoose(MenuSelection.Stored(row.item)) },
                )
            }
            if (row.item.isSellable) {
                ActionButton(
                    title = "Sell",
                    textColor = Theme.onAccent,
                    background = Theme.accent,
                    pulses = row.isLesson,
                    contentDescription = "Sell ${row.item.name}",
                    onClick = { onTransact(StorageTransaction(row.item, sell = true)) },
                )
            }
            ActionButton(
                title = row.item.discardTitle,
                textColor = Theme.onDanger,
                background = Theme.danger,
                contentDescription = "${row.item.discardTitle} ${row.item.name}",
                onClick = { onTransact(StorageTransaction(row.item, sell = false)) },
            )
        }
    }
}

@Composable
private fun ActionButton(
    title: String,
    textColor: Color,
    background: Color,
    contentDescription: String,
    pulses: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .defaultMinSize(minWidth = ACTION_BUTTON_WIDTH, minHeight = ACTION_BUTTON_HEIGHT)
                .lessonPulse(pulses, shape = CircleShape)
                .background(background, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = HudType.subheadline.copy(fontWeight = FontWeight.Bold),
            color = textColor,
        )
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
    currentLesson: Lesson? = null,
) {
    var requested by remember(transaction) { mutableIntStateOf(1) }
    val quantity = MenuNumbers.quantity(requested, stored)
    val item = transaction.item
    HudMaterialTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "${transaction.title} ${item.name}",
                    style = HudType.title3.copy(fontWeight = FontWeight.Bold),
                    color = Theme.textPrimary,
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().categoryCard(isHighlighted = false),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MenuImage(
                            MenuPictures.stored(item),
                            loader,
                            Modifier.size(72.dp).align(Alignment.CenterHorizontally),
                        )
                        Text(
                            text = "${item.name} (${MenuNumbers.count(stored)}x)",
                            style = HudType.title3.copy(fontWeight = FontWeight.Bold),
                            color = Theme.textPrimary,
                        )
                    }
                    QuantityControls(
                        quantity = quantity,
                        onQuantityChange = { requested = it },
                        maximum = stored,
                        pulsesMax = transaction.sell && currentLesson == Lesson.pickMax,
                        onMax = {
                            if (transaction.sell) {
                                session.act { it.learn(Lesson.pickMax) }
                            }
                        },
                    )
                    if (transaction.sell) {
                        val prices =
                            session.read {
                                val engine = GameEngine(it)
                                engine.saleValue(item, 1).dollars to engine.saleValue(item, quantity).dollars
                            }
                        PriceRow(title = "Price each", price = prices.first, isTotal = false)
                        PriceRow(title = "Total", price = prices.second, isTotal = true)
                    } else {
                        Text(
                            text = "${item.discardedTitle} items are gone for good and pay nothing",
                            style = HudType.footnote,
                            color = Theme.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
            confirmButton = {
                val pulsesConfirm = transaction.sell && currentLesson == Lesson.sell
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .lessonPulse(pulsesConfirm, shape = RoundedCornerShape(16.dp))
                            .background(
                                if (transaction.sell) Theme.accent else Theme.danger,
                                RoundedCornerShape(16.dp),
                            ).clickable(role = Role.Button, enabled = quantity > 0) {
                                if (transaction.sell) {
                                    session.act { it.learn(Lesson.sell) }
                                }
                                onConfirm(quantity)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            if (transaction.sell) {
                                transaction.title
                            } else {
                                "${transaction.title} ${MenuNumbers.count(
                                    quantity,
                                )}"
                            },
                        style = HudType.headline,
                        color = if (transaction.sell) Theme.onAccent else Theme.onDanger,
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier =
                        Modifier
                            .glassCard(12.dp)
                            .clickable(role = Role.Button, onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", style = HudType.subheadline, color = Theme.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun PriceRow(
    title: String,
    price: String,
    isTotal: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = HudType.subheadline, color = Theme.textSecondary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = price,
            style =
                if (isTotal) {
                    HudType.title3.copy(
                        fontWeight = FontWeight.ExtraBold,
                    )
                } else {
                    HudType.body.copy(fontWeight = FontWeight.SemiBold)
                },
            color = Theme.money,
        )
    }
}
