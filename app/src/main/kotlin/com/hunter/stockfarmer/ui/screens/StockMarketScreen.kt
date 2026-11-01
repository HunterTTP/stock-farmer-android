package com.hunter.stockfarmer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.market.StockQuoteProvider
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.StockMarketRules
import com.hunter.stockfarmer.core.rules.buyShares
import com.hunter.stockfarmer.core.rules.sellShares
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.market.NetworkMonitor
import com.hunter.stockfarmer.market.StockPolling
import com.hunter.stockfarmer.ui.theme.BarScrollView
import com.hunter.stockfarmer.ui.theme.HudSheet
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.QuantityControls
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard
import com.hunter.stockfarmer.ui.theme.glassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import com.hunter.stockfarmer.market.StockQuoteProvider as YahooQuotes

@Composable
private fun rememberIsOnline(): Boolean {
    val monitor = NetworkMonitor.of(LocalContext.current)
    return monitor.isOnline.collectAsState().value
}

private val defaultStockQuotes = YahooQuotes()

private data class TradeSelection(
    val ticker: String,
    val buying: Boolean,
)

@Composable
fun StockMarketScreen(
    session: GameSession,
    onClose: () -> Unit,
    provider: StockQuoteProvider = defaultStockQuotes,
    online: Boolean = rememberIsOnline(),
    onEvents: (List<GameEvent>) -> Unit = {},
) {
    val controller = remember(session, provider) { StockMarketController(session, provider) }
    val prices by controller.prices.collectAsState()
    val state by rememberScreenState(session to controller) {
        session.act { StockMarketState.from(it, controller.prices.value, Instant.now()) }
    }
    var search by remember { mutableStateOf("") }
    var trade by remember { mutableStateOf<TradeSelection?>(null) }
    var upgrading by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var rewards by remember(session) { mutableStateOf(emptyList<QuestReward>()) }
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(controller, online, owner) {
        if (!online) {
            trade = null
            upgrading = false
            return@LaunchedEffect
        }
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var reportsFailure = true
            while (true) {
                controller.pullIfDue(reportsFailure)
                reportsFailure = false
                val wait =
                    session.read { StockPolling.waitSeconds(it.lastStockPullAt, Instant.now(), it.holdings.isEmpty()) }
                delay((wait * 1000).toLong())
            }
        }
    }
    BackHandler(enabled = searching) { searching = false }
    LaunchedEffect(rewards) {
        if (rewards.isNotEmpty()) {
            delay(4000)
            rewards = emptyList()
        }
    }
    ScreenLayout("Stock Market", session, onClose) {
        if (!online) {
            OfflineNotice()
        } else {
            if (rewards.isNotEmpty()) QuestRewardCard(rewards)
            TemporaryMessage(message) { message = null }
            if (searching) {
                SearchCard(search, { search = it }, prices.loading) { scope.launch { controller.search(search) } }
                prices.message?.let { Text(it, color = Theme.spend, style = HudType.subheadline) }
                state.rows.firstOrNull { it.quote.ticker == prices.selected }?.let { row ->
                    StockRow(row) { trade = TradeSelection(row.quote.ticker, it) }
                }
            } else {
                SummaryCard(state)
                val owned = state.rows.filter { it.holding != null }
                if (owned.isEmpty()) {
                    Text(
                        "You don't own any stocks yet. Tap Buy Stock to search for a ticker.",
                        color = Theme.textSecondary,
                        style = HudType.subheadline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                owned.forEach { row -> StockRow(row) { trade = TradeSelection(row.quote.ticker, it) } }
                PillButton("Buy Stock", Theme.accent, Theme.onAccent) { searching = true }
            }
            state.upgrade?.takeIf { !searching }?.let {
                PillButton("Upgrade", Theme.xp.copy(alpha = 0.14f), Theme.xp) { upgrading = true }
            }
        }
    }
    if (upgrading && state.houseOrigin != null) {
        BuildingUpgradeScreen(
            session = session,
            onClose = { upgrading = false },
            origin = state.houseOrigin,
            onEvents = { events ->
                onEvents(events)
                QuestReward.from(events).takeIf { it.isNotEmpty() }?.let { rewards = it }
            },
        )
    }
    trade?.let { selection ->
        val row = state.rows.firstOrNull { it.quote.ticker == selection.ticker }
        if (row != null && online) {
            StockTradeDialog(session, row, selection.buying, { trade = null }, { events ->
                onEvents(events)
                QuestReward.from(events).takeIf { it.isNotEmpty() }?.let { rewards = it }
            }) {
                message = it
                trade = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockTradeDialog(
    session: GameSession,
    row: StockRowState,
    buying: Boolean,
    onDismiss: () -> Unit,
    onEvents: (List<GameEvent>) -> Unit,
    onTraded: (String) -> Unit,
) {
    var quantity by remember(row.quote.ticker, buying) { mutableLongStateOf(1L) }
    var failure by remember { mutableStateOf<String?>(null) }
    val preview = session.act { StockTradeState.from(it, row, buying, quantity, Instant.now()) }
    val verb = if (buying) "Buy" else "Sell"
    val owned = row.holding?.shares ?: 0L
    val change = StockMarketRules.dayChange(row.priceCents, row.quote.previousCloseCents)
    val changeColor = if ((change ?: 0.0) >= 0) Theme.accent else Theme.spend
    val canConfirm = preview.rejection == null && quantity in 1..preview.maximum && preview.maximum > 0L
    val buttonFill = if (buying) Theme.accent else Theme.danger
    val buttonText = if (buying) Theme.onAccent else Theme.onDanger

    HudSheet(onDismiss = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$verb ${row.quote.ticker}",
                Modifier.weight(1f),
                style = HudType.headline,
                color = Theme.textPrimary,
            )
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Theme.textSecondary, style = HudType.subheadline)
            }
        }
        BarScrollView(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().categoryCard(isHighlighted = false),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val countText = if (owned > 0L) " (${ScreenFormat.number(owned)}x)" else ""
                    Text(
                        "${row.quote.ticker}$countText",
                        style = HudType.title3.copy(fontWeight = FontWeight.Bold),
                        color = Theme.textPrimary,
                    )
                    if (change != null) {
                        val arrow = if (change >= 0) "▲" else "▼"
                        Text(
                            "$arrow ${ScreenFormat.percent(change)}",
                            color = changeColor,
                            style = HudType.subheadline.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    Text(
                        "As of ${ScreenFormat.quoteTime(row.quote.quotedAt)}",
                        style = HudType.footnote,
                        color = Theme.textSecondary,
                    )
                }

                QuantityControls(
                    quantity = quantity,
                    onQuantityChange = { quantity = it },
                    maximum = preview.maximum,
                )

                Column(
                    Modifier.fillMaxWidth().glassCard(14.dp).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TradePriceRow("Price each", row.price)
                    TradePriceRow("Total", preview.total, isTotal = true)
                    TradeProfitRow("Profit / loss", preview.profit, preview.profitCents)
                    if (buying) {
                        Text(
                            "Buying shares does not realize a profit or loss.",
                            style = HudType.footnote,
                            color = Theme.textSecondary,
                        )
                    }
                }

                (failure ?: preview.rejection)?.let {
                    Text(it, color = Theme.spend, style = HudType.subheadline)
                }

                PillButton(
                    title = verb,
                    fill = buttonFill,
                    text = buttonText,
                    enabled = canConfirm,
                    modifier = Modifier.alpha(if (canConfirm) 1f else 0.5f),
                    onClick = {
                        val events =
                            session.screenAction {
                                if (buying) {
                                    it.buyShares(row.quote, quantity, row.priceCents, Instant.now())
                                } else {
                                    it.sellShares(row.quote, quantity, row.priceCents, Instant.now())
                                }
                            }
                        onEvents(events)
                        val rejection =
                            events
                                .filterIsInstance<GameEvent.Rejected>()
                                .firstOrNull()
                                ?.rejection
                                ?.message
                        failure = rejection
                        val resultVerb = if (buying) "Bought" else "Sold"
                        if (rejection == null) {
                            onTraded("$resultVerb $quantity ${row.quote.ticker}")
                        }
                    },
                )
            }
        }
    }
}
