package com.hunter.stockfarmer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.StockMarketRules
import com.hunter.stockfarmer.ui.theme.HudIcon
import com.hunter.stockfarmer.ui.theme.HudIcons
import com.hunter.stockfarmer.ui.theme.HudTextField
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard
import com.hunter.stockfarmer.ui.theme.glassCard

@Composable
internal fun SummaryCard(state: StockMarketState) {
    val gainColor = if (state.gainCents < 0) Theme.spend else Theme.accent
    val color = if (state.hasCost) gainColor else Theme.textPrimary
    Column(Modifier.fillMaxWidth().categoryCard(false), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudIcon(HudIcons.chartLineUptrend, Color.White, size = 34.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Stocks Owned", color = Theme.textSecondary, style = HudType.caption)
                Text(
                    state.slots,
                    color = Theme.textPrimary,
                    style = HudType.headline.copy(fontWeight = FontWeight.ExtraBold),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
        SummaryRow("Portfolio Value", state.portfolioValue, Theme.textPrimary, FontWeight.ExtraBold)
        val arrow = if (state.gainCents >= 0) "▲" else "▼"
        SummaryRow(
            "Performance %",
            if (state.hasCost) "${state.performance} $arrow" else state.performance,
            color,
            FontWeight.Bold,
            HudType.subheadline,
        )
        SummaryRow("Performance $", if (state.hasCost) ScreenFormat.signedMoney(state.gainCents) else "$0", color)
    }
}

@Composable
private fun SummaryRow(
    title: String,
    value: String,
    color: Color,
    weight: FontWeight = FontWeight.Bold,
    style: TextStyle = HudType.headline,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), color = Theme.textSecondary, style = HudType.subheadline, maxLines = 1)
        Text(value, color = color, style = style.copy(fontWeight = weight), maxLines = 1)
    }
}

@Composable
internal fun StockRow(
    row: StockRowState,
    onTrade: (buying: Boolean) -> Unit,
) {
    val change = StockMarketRules.dayChange(row.quote.priceCents, row.quote.previousCloseCents)
    val changeColor = if ((change ?: 0.0) >= 0) Theme.accent else Theme.spend
    Row(
        Modifier.fillMaxWidth().glassCard(14.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.quote.ticker,
                    color = Theme.textPrimary,
                    style = HudType.title3.copy(fontWeight = FontWeight.ExtraBold),
                )
                if (change !=
                    null
                ) {
                    Text(if (change >= 0) "▲" else "▼", color = changeColor, style = HudType.subheadline)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.price, color = Theme.money, style = HudType.headline.copy(fontWeight = FontWeight.Bold))
                row.dayChange?.let {
                    Text(it, color = changeColor, style = HudType.subheadline.copy(fontWeight = FontWeight.Bold))
                }
            }
            val holding = row.holding
            if (holding != null) {
                val noun = if (holding.shares == 1L) "share" else "shares"
                RowDetail("${ScreenFormat.number(holding.shares)} $noun · worth ${row.worth}")
                RowDetail("Average cost ${(holding.averageCostCents / 100.0).dollars}")
            } else {
                RowDetail("Not owned")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TradePill("Buy", Theme.accent, Theme.onAccent) { onTrade(true) }
            if (row.holding != null) TradePill("Sell", Theme.danger, Theme.onDanger) { onTrade(false) }
        }
    }
}

@Composable
private fun RowDetail(text: String) = Text(text, color = Theme.textSecondary, style = HudType.caption, maxLines = 1)

@Composable
private fun TradePill(
    title: String,
    fill: Color,
    text: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 72.dp, minHeight = 36.dp)
            .background(fill, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = text, style = HudType.subheadline.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
internal fun PillButton(
    title: String,
    fill: Color,
    text: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    onClick: () -> Unit,
) = com.hunter.stockfarmer.ui.theme
    .PillButton(title, fill, text, modifier, enabled, cornerRadius, onClick = onClick)

@Composable
internal fun PillButton(
    title: String,
    role: com.hunter.stockfarmer.ui.theme.PillRole,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = com.hunter.stockfarmer.ui.theme
    .PillButton(title, role, modifier, enabled, onClick = onClick)

@Composable
internal fun TradePriceRow(
    title: String,
    value: String,
    isTotal: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Theme.textSecondary, style = HudType.subheadline)
        Text(
            value,
            color = Theme.money,
            style =
                if (isTotal) {
                    HudType.title3.copy(fontWeight = FontWeight.Bold)
                } else {
                    HudType.body.copy(fontWeight = FontWeight.SemiBold)
                },
        )
    }
}

@Composable
internal fun TradeProfitRow(
    title: String,
    profit: String?,
    profitCents: Long?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Theme.textSecondary, style = HudType.subheadline)
        if (profit != null) {
            val color =
                when {
                    (profitCents ?: 0L) > 0L -> Theme.earn
                    (profitCents ?: 0L) < 0L -> Theme.spend
                    else -> Theme.textPrimary
                }
            Text(profit, color = color, style = HudType.body.copy(fontWeight = FontWeight.SemiBold))
        } else {
            Text("—", color = Theme.textSecondary, style = HudType.body)
        }
    }
}

@Composable
internal fun SearchCard(
    search: String,
    onSearch: (String) -> Unit,
    loading: Boolean,
    onRun: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().categoryCard(false),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudTextField(
            search,
            onSearch,
            "Ticker, e.g. AAPL",
            Modifier.weight(1f),
            KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
            KeyboardActions(onSearch = { onRun() }),
        )
        Box(
            Modifier
                .defaultMinSize(minHeight = 44.dp)
                .background(Theme.accent.copy(alpha = 0.15f), CircleShape)
                .clickable(enabled = !loading, role = Role.Button, onClick = onRun)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Search",
                color = Theme.accent,
                style = HudType.subheadline.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
internal fun OfflineNotice() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HudIcon(HudIcons.wifiSlash, Theme.textSecondary, size = 54.dp)
        Text(
            "No internet connection",
            color = Theme.textPrimary,
            style = HudType.title3.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            "The Stock Market needs the internet to get live prices, so it's closed for now. Connect to Wi-Fi or " +
                "mobile data and tap your house again. The rest of your farm works just fine without it.",
            color = Theme.textSecondary,
            style = HudType.subheadline,
            textAlign = TextAlign.Center,
        )
    }
}
