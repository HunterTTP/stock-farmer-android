package com.hunter.stockfarmer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.ValueSample
import com.hunter.stockfarmer.core.rules.CareerSeries
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.Theme

@Composable
fun CareerScreen(
    session: GameSession,
    onClose: () -> Unit,
) {
    val state by rememberScreenState(session) { session.act(CareerState::from) }
    var collection by remember { mutableStateOf<CareerCollection?>(null) }
    var series by remember { mutableStateOf<CareerSeries?>(null) }
    var query by remember(collection) { mutableStateOf("") }
    var filter by remember(collection) { mutableStateOf(CareerFilter.ALL) }
    val title = collection?.title ?: series?.title ?: "Career"
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
            selectedCollection != null -> {
                val sections = state.collections.getValue(selectedCollection)
                val total = sections.sumOf { it.entries.size }
                val collected = sections.sumOf { it.collected }
                ScreenCard {
                    Text("$collected of $total collected")
                    LinearProgressIndicator(progress = { collected.toFloat() / total.coerceAtLeast(1) })
                    OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CareerFilter.entries.forEach {
                            FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it.title) })
                        }
                    }
                }
                val visible = sections.mapNotNull { it.filtered(query, filter) }
                if (visible.isEmpty()) Text("Nothing matches.")
                visible.forEach { section ->
                    Text(
                        "${section.title} · ${section.collected}/${section.entries.size}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    section.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { entry ->
                                androidx.compose.foundation.layout
                                    .Column(Modifier.weight(1f)) { CareerTile(entry) }
                            }
                        }
                    }
                }
            }

            selectedSeries != null -> {
                ScreenCard {
                    Text(
                        when (selectedSeries) {
                            CareerSeries.XP -> "Level ${state.level} · ${state.xpProgress}"
                            CareerSeries.INCOME -> state.income
                            CareerSeries.STOCK_PROFIT -> state.profit
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (selectedSeries == CareerSeries.INCOME) {
                        Text(
                            "Every sale plus the profit of each winning stock sale. " +
                                "Spending, refunds and stock losses never take it down.",
                        )
                    }
                    if (selectedSeries ==
                        CareerSeries.STOCK_PROFIT
                    ) {
                        Text("What your stock sales have made or lost, all told.")
                    }
                }
                CareerChart(
                    selectedSeries.title,
                    state.histories.getValue(selectedSeries),
                    selectedSeries != CareerSeries.XP,
                )
                ScreenCard {
                    when (selectedSeries) {
                        CareerSeries.XP -> {
                            Figure("Total XP", state.totalXP)
                            Figure("Time played", state.time)
                            Figure("Crops harvested", state.cropsHarvested)
                            Figure("Quests completed", state.completedQuests)
                        }

                        CareerSeries.INCOME -> {
                            Figure("Best day", state.bestIncome)
                        }

                        CareerSeries.STOCK_PROFIT -> {
                            Figure("Best day", state.bestProfit)
                        }
                    }
                }
            }

            else -> {
                ScreenCard {
                    TextButton(
                        onClick = { series = CareerSeries.XP },
                    ) { Text("Level ${state.level} ›", color = Theme.xp) }
                    Text(state.xpProgress)
                    LinearProgressIndicator(progress = { state.levelFraction })
                    Figure("Time Played", state.time)
                    TextButton(onClick = { series = CareerSeries.INCOME }) { Text("Money Earned · ${state.income} ›") }
                    Text("Best day ${state.bestIncome}")
                    TextButton(
                        onClick = { series = CareerSeries.STOCK_PROFIT },
                    ) { Text("Stock Profit · ${state.profit} ›") }
                    Text("Best day ${state.bestProfit}")
                }
                Text("Collections", style = MaterialTheme.typography.titleMedium)
                state.collections.forEach { (kind, sections) ->
                    val collected = sections.sumOf { it.collected }
                    val total = sections.sumOf { it.entries.size }
                    ScreenCard {
                        TextButton(onClick = { collection = kind }) { Text("${kind.title} · $collected / $total ›") }
                        LinearProgressIndicator(progress = { collected.toFloat() / total.coerceAtLeast(1) })
                    }
                }
                ScreenCard {
                    Figure("Perfection", "${state.perfection}%", Theme.gold)
                    LinearProgressIndicator(progress = { state.collected.toFloat() / state.total.coerceAtLeast(1) })
                    Text("${state.collected} of ${state.total} completed")
                }
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

@Composable
private fun CareerTile(entry: CareerEntry) {
    ScreenCard {
        ScreenIcon(entry.art, entry.symbol, entry.collected)
        Text(entry.name, color = if (entry.collected) Theme.accent else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (entry.collected) entry.detail ?: "Collected ✓" else "Not yet collected",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CareerChart(
    title: String,
    samples: List<ValueSample>,
    money: Boolean,
) {
    val chart = remember(samples, money) { CareerChartState.from(samples, if (money) 0.01 else 1.0) }
    ScreenCard {
        Text("$title over time played", style = MaterialTheme.typography.titleMedium)
        if (chart.points.size < 2) {
            Text("Play a little longer to chart this.")
        } else {
            Figure(ScreenFormat.compact(chart.maximum, money), ScreenFormat.compact(chart.minimum, money))
            Canvas(
                Modifier.fillMaxWidth().height(180.dp).semantics {
                    contentDescription =
                        "$title from ${samples.first().value} to ${samples.last().value}; ${chart.endTime} played"
                },
            ) {
                repeat(4) { index ->
                    val y = size.height * index / 3
                    drawLine(Color.White.copy(alpha = 0.1f), Offset(0f, y), Offset(size.width, y))
                }
                val path = Path()
                chart.points.forEachIndexed { index, point ->
                    if (index == 0) {
                        path.moveTo(point.x * size.width, point.y * size.height)
                    } else {
                        path.lineTo(point.x * size.width, point.y * size.height)
                    }
                }
                drawPath(path, Theme.xp, style = Stroke(width = 2.5.dp.toPx()))
                path.lineTo(size.width, size.height)
                path.lineTo(0f, size.height)
                path.close()
                drawPath(path, Brush.verticalGradient(listOf(Theme.xp.copy(alpha = 0.3f), Color.Transparent)))
            }
            Figure("0", chart.endTime)
        }
    }
}
