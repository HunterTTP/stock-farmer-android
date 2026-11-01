package com.hunter.stockfarmer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.market.StockQuoteProvider
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.StockMarketRules
import com.hunter.stockfarmer.core.rules.buyShares
import com.hunter.stockfarmer.core.rules.sellShares
import com.hunter.stockfarmer.core.rules.upgradeHouse
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.market.NetworkMonitor
import com.hunter.stockfarmer.market.StockPolling
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import com.hunter.stockfarmer.ui.theme.Theme
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
    LaunchedEffect(rewards) {
        if (rewards.isNotEmpty()) {
            delay(4000)
            rewards = emptyList()
        }
    }
    ScreenLayout("Stock Market", session, onClose) {
        if (!online) {
            Text("No internet connection", style = MaterialTheme.typography.titleMedium)
            Text("The Stock Market needs the internet to get live prices. Connect to Wi-Fi or mobile data.")
        } else {
            if (rewards.isNotEmpty()) QuestRewardCard(rewards)
            ScreenCard {
                Figure("Stocks Owned", state.slots)
                Figure("Portfolio Value", state.portfolioValue, Theme.accent)
                Figure("Performance %", state.performance, if (state.gainCents < 0) Theme.spend else Theme.accent)
                Figure(
                    "Performance $",
                    ScreenFormat.signedMoney(state.gainCents),
                    if (state.gainCents < 0) Theme.spend else Theme.accent,
                )
            }
            if (state.rows.none { it.holding != null }) Text("You don't own any stocks yet. Search for a ticker below.")
            OutlinedTextField(
                search,
                { search = it },
                label = { Text("Ticker, e.g. AAPL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { scope.launch { controller.search(search) } }, enabled = !prices.loading) {
                Text(if (prices.loading) "Getting prices…" else "Search")
            }
            prices.message?.let { Text(it, color = Theme.spend) }
            TemporaryMessage(message) { message = null }
            state.rows.forEach { row ->
                ScreenCard {
                    Figure(row.quote.ticker, row.price, Theme.accent)
                    row.dayChange?.let { Text(it) }
                    row.holding?.let { holding ->
                        Text("${ScreenFormat.number(holding.shares)} shares · worth ${row.worth}")
                        Text("Average cost ${(holding.averageCostCents / 100.0).dollars}")
                    } ?: Text("Not owned")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { trade = TradeSelection(row.quote.ticker, true) }) { Text("Buy") }
                        if (row.holding != null) {
                            Button(onClick = { trade = TradeSelection(row.quote.ticker, false) }) { Text("Sell") }
                        }
                    }
                }
            }
            state.upgrade?.let {
                Button(onClick = { upgrading = !upgrading }) { Text("Upgrade House") }
                if (upgrading) {
                    UpgradeCard(it) {
                        val events =
                            state.houseOrigin
                                ?.let { origin ->
                                    session.screenAction { engine -> engine.upgradeHouse(origin, Instant.now()) }
                                }.orEmpty()
                        val limit = StockMarketRules.UPGRADED_SLOT_LIMIT
                        onEvents(events)
                        QuestReward.from(events).takeIf { it.isNotEmpty() }?.let { rewards = it }
                        message = events
                            .filterIsInstance<GameEvent.Rejected>()
                            .firstOrNull()
                            ?.rejection
                            ?.message
                            ?: "Luxury House built: you can now own up to $limit stocks"
                        upgrading = false
                    }
                }
            }
        }
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

@Composable
private fun StockTradeDialog(
    session: GameSession,
    row: StockRowState,
    buying: Boolean,
    onDismiss: () -> Unit,
    onEvents: (List<GameEvent>) -> Unit,
    onTraded: (String) -> Unit,
) {
    var quantityText by remember(row.quote.ticker, buying) { mutableStateOf("1") }
    var failure by remember { mutableStateOf<String?>(null) }
    val quantity = quantityText.toLongOrNull() ?: 0L
    val preview = session.act { StockTradeState.from(it, row, buying, quantity, Instant.now()) }
    val verb = if (buying) "Buy" else "Sell"
    HudMaterialTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("$verb ${row.quote.ticker}") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Owned: ${row.holding?.shares ?: 0}")
                    Text("As of ${ScreenFormat.quoteTime(row.quote.quotedAt)}")
                    OutlinedTextField(
                        quantityText,
                        { quantityText = it },
                        label = { Text("Whole shares") },
                        singleLine = true,
                    )
                    TextButton(onClick = { quantityText = preview.maximum.toString() }) {
                        Text("Maximum: ${ScreenFormat.number(preview.maximum)}")
                    }
                    Figure("Price each", row.price)
                    Figure("Total", preview.total)
                    Figure("Profit / loss", preview.profit ?: "—")
                    if (buying) Text("Buying shares does not realize a profit or loss.")
                    (failure ?: preview.rejection)?.let { Text(it, color = Theme.spend) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = preview.rejection == null && quantity in 1..preview.maximum,
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
                        failure =
                            events
                                .filterIsInstance<GameEvent.Rejected>()
                                .firstOrNull()
                                ?.rejection
                                ?.message
                        val resultVerb = if (buying) "Bought" else "Sold"
                        if (failure == null) onTraded("$resultVerb $quantity ${row.quote.ticker}")
                    },
                ) { Text(verb) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}
