package com.hunter.stockfarmer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.ValueSample
import com.hunter.stockfarmer.core.rules.CareerSeries
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.glassCard

@Composable
fun CareerScreen(
    session: GameSession,
    onClose: () -> Unit,
) {
    val state by rememberScreenState(session) { session.act(CareerState::from) }
    var collection by remember { mutableStateOf<CareerCollection?>(null) }
    var series by remember { mutableStateOf<CareerSeries?>(null) }
    val title =
        collection?.title ?: series?.let { if (it == CareerSeries.XP) "Level ${state.level}" else it.title } ?: "Career"
    val close = {
        if (collection != null || series != null) {
            collection = null
            series = null
        } else {
            onClose()
        }
    }
    ScreenLayout(title, session, close) {
        val selectedCollection = collection
        val selectedSeries = series
        when {
            selectedCollection != null -> CollectionPage(selectedCollection, state)
            selectedSeries != null -> SeriesPage(selectedSeries, state)
            else -> Overview(state, { series = it }, { collection = it })
        }
    }
}

@Composable
private fun Overview(
    state: CareerState,
    openSeries: (CareerSeries) -> Unit,
    openCollection: (CareerCollection) -> Unit,
) {
    val isMax = state.xpProgress == "Max level"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SquareCard(
            "Level",
            Theme.xp,
            Modifier.weight(1f),
            opensPage = true,
            onClick = { openSeries(CareerSeries.XP) },
            caption = state.xpProgress,
        ) { LevelRing(state.level, state.levelFraction, isMax, 84.dp) }
        SquareCard("Time Played", Theme.textPrimary, Modifier.weight(1f)) {
            SquareValue(state.time, Theme.textPrimary)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MoneyCard(
            "Money Earned",
            state.income,
            state.bestIncome,
            Modifier.weight(1f),
        ) { openSeries(CareerSeries.INCOME) }
        MoneyCard("Stock Profit", state.profit, state.bestProfit, Modifier.weight(1f)) {
            openSeries(CareerSeries.STOCK_PROFIT)
        }
    }
    Text("Collections", Modifier.padding(start = 4.dp), color = Theme.textPrimary, style = HudType.headline)
    state.collections.forEach { (kind, sections) ->
        CollectionRow(
            kind,
            sections.sumOf { it.collected },
            sections.sumOf { it.entries.size },
        ) { openCollection(kind) }
    }
    PerfectionCard(state.collected, state.total, state.perfection)
}

@Composable
private fun MoneyCard(
    title: String,
    amount: String,
    bestDay: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val tint = if (amount.startsWith("-")) Theme.spend else Theme.money
    SquareCard(title, tint, modifier, opensPage = true, onClick = onClick, caption = "Best day $bestDay") {
        SquareValue(amount, tint)
    }
}

@Composable
private fun SeriesPage(
    series: CareerSeries,
    state: CareerState,
) {
    val money = series != CareerSeries.XP
    val amount =
        when (series) {
            CareerSeries.XP -> state.xpProgress
            CareerSeries.INCOME -> state.income
            CareerSeries.STOCK_PROFIT -> state.profit
        }
    val tint =
        if (!money) {
            Theme.xp
        } else if (amount.startsWith("-")) {
            Theme.spend
        } else {
            Theme.money
        }
    Column(Modifier.fillMaxWidth().glassCard(18.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (series == CareerSeries.XP) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                LevelRing(state.level, state.levelFraction, amount == "Max level", 92.dp)
                Text(amount, color = tint, style = HudType.title3.copy(fontWeight = FontWeight.ExtraBold))
            }
        } else {
            Text(amount, color = tint, style = HudType.title.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
            Text(series.explanation, color = Theme.textSecondary, style = HudType.footnote)
        }
    }
    CareerChart(series.title, state.histories.getValue(series), money, tint)
    StatsCard(
        when (series) {
            CareerSeries.XP -> {
                listOf(
                    "Total XP" to state.totalXP,
                    "Time played" to state.time,
                    "Crops harvested" to state.cropsHarvested,
                    "Quests completed" to state.completedQuests,
                )
            }

            CareerSeries.INCOME -> {
                listOf("Best day" to state.bestIncome)
            }

            CareerSeries.STOCK_PROFIT -> {
                listOf("Best day" to state.bestProfit)
            }
        },
    )
}

@Composable
private fun CollectionPage(
    kind: CareerCollection,
    state: CareerState,
) {
    var query by remember(kind) { mutableStateOf("") }
    var filter by remember(kind) { mutableStateOf(CareerFilter.ALL) }
    val sections = state.collections.getValue(kind)
    val total = sections.sumOf { it.entries.size }
    val collected = sections.sumOf { it.collected }
    Column(Modifier.fillMaxWidth().glassCard(18.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "$collected of $total collected",
            color = Theme.textPrimary,
            style = HudType.title3.copy(fontWeight = FontWeight.ExtraBold),
        )
        ProgressBar(collected, total, Theme.accent)
        OutlinedTextField(
            query,
            { query = it },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CareerFilter.entries.forEach {
                FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it.title) })
            }
        }
    }
    val visible = sections.mapNotNull { it.filtered(query, filter) }
    if (visible.isEmpty()) {
        Text(
            "Nothing matches.",
            Modifier.fillMaxWidth().padding(top = 24.dp),
            color = Theme.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
    visible.forEach { section ->
        if (visible.size > 1) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(section.title, Modifier.weight(1f), color = Theme.textPrimary, style = HudType.headline)
                Text(
                    "${section.collected} / ${section.entries.size}",
                    color = Theme.textSecondary,
                    style = HudType.footnote.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
        section.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { CareerTile(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val CareerSeries.title: String get() =
    when (this) {
        CareerSeries.XP -> "XP"
        CareerSeries.INCOME -> "Money Earned"
        CareerSeries.STOCK_PROFIT -> "Stock Profit"
    }

private val CareerSeries.explanation: String get() =
    when (this) {
        CareerSeries.INCOME -> {
            "Every sale plus the profit of each winning stock sale. " +
                "Spending, refunds and stock losses never take it down."
        }

        else -> {
            "What your stock sales have made or lost, all told."
        }
    }

@Composable
private fun CareerChart(
    title: String,
    samples: List<ValueSample>,
    money: Boolean,
    tint: Color,
) {
    val chart = remember(samples, money) { CareerChartState.from(samples, if (money) 0.01 else 1.0) }
    Column(Modifier.fillMaxWidth().glassCard(18.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$title over time played", color = Theme.textPrimary, style = HudType.headline)
        if (chart.points.size < 2) {
            Text(
                "Play a little longer to chart this.",
                Modifier.fillMaxWidth().height(120.dp),
                color = Theme.textSecondary,
                style = HudType.subheadline,
                textAlign = TextAlign.Center,
            )
        } else {
            Row(Modifier.fillMaxWidth()) {
                AxisLabel(ScreenFormat.compact(chart.maximum, money))
                Spacer(Modifier.weight(1f))
                AxisLabel(ScreenFormat.compact(chart.minimum, money))
            }
            Canvas(
                Modifier.fillMaxWidth().height(180.dp).semantics {
                    contentDescription =
                        "$title from ${samples.first().value} to ${samples.last().value}; ${chart.endTime} played"
                },
            ) {
                repeat(4) { index ->
                    val y = size.height * index / 3
                    drawLine(Theme.glassFill, Offset(0f, y), Offset(size.width, y))
                }
                val path = Path()
                chart.points.forEachIndexed { index, point ->
                    if (index == 0) {
                        path.moveTo(point.x * size.width, point.y * size.height)
                    } else {
                        path.lineTo(point.x * size.width, point.y * size.height)
                    }
                }
                drawPath(path, tint, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                path.lineTo(size.width, size.height)
                path.lineTo(0f, size.height)
                path.close()
                drawPath(path, Brush.verticalGradient(listOf(tint.copy(alpha = 0.45f), tint.copy(alpha = 0.02f))))
            }
            Row(Modifier.fillMaxWidth()) {
                AxisLabel("0")
                Spacer(Modifier.weight(1f))
                AxisLabel(chart.endTime)
            }
        }
    }
}

@Composable
private fun AxisLabel(text: String) = Text(text, color = Theme.textSecondary, style = HudType.caption)
