package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.model.ValueSample
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.LevelUnlocks
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

internal object ScreenFormat {
    fun number(value: Long): String = String.format(Locale.US, "%,d", value)

    fun compact(
        value: Double,
        money: Boolean = false,
        fractionDigits: Int = 0,
    ): String {
        val magnitude = abs(value)
        val steps = listOf(1e12 to "T", 1e9 to "B", 1e6 to "M", 1e3 to "K")
        val step = steps.firstOrNull { magnitude >= it.first }
        val optional = "#".repeat(fractionDigits)
        val pattern =
            if (step == null) {
                if (fractionDigits > 0) "#,##0.$optional" else "#,##0"
            } else {
                "0." + optional.ifEmpty { "#" }
            }
        val formatter = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
        val sign = if (value < -1e-9) "-" else ""
        return sign + (if (money) "$" else "") +
            formatter.format(magnitude / (step?.first ?: 1.0)) + (step?.second ?: "")
    }

    fun percent(fraction: Double): String = String.format(Locale.US, "%+.2f%%", fraction * 100)

    fun quoteTime(
        time: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US).withZone(zone).format(time)

    fun signedMoney(cents: Long): String =
        (
            if (cents > 0) {
                "+"
            } else if (cents < 0) {
                "-"
            } else {
                ""
            }
        ) + (abs(cents) / 100.0).dollars
}

internal data class ChartPoint(
    val x: Float,
    val y: Float,
)

internal data class CareerChartState(
    val points: List<ChartPoint>,
    val minimum: Double,
    val maximum: Double,
    val endTime: String,
    val yLabels: List<String> = emptyList(),
    val xLabels: List<String> = emptyList(),
) {
    companion object {
        fun from(
            samples: List<ValueSample>,
            scale: Double = 1.0,
            money: Boolean = false,
        ): CareerChartState {
            val end = samples.lastOrNull()?.playTime ?: 0.0
            val unit = if (end < 7200) 60.0 else 3600.0
            val unitSuffix = if (unit == 60.0) "m" else "h"
            val endText = ScreenFormat.compact(end / unit) + unitSuffix
            val values = samples.map { it.value * scale }
            val minimum = minOf(0.0, values.minOrNull() ?: 0.0)
            val maximum = maxOf(0.0, values.maxOrNull() ?: 0.0)
            val span = (maximum - minimum).takeIf { it > 0 } ?: 1.0
            val points =
                samples.map { sample ->
                    ChartPoint(
                        (sample.playTime / end.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f),
                        (1 - (sample.value * scale - minimum) / span).toFloat().coerceIn(0f, 1f),
                    )
                }
            val yLabels = distinctTickLabels((0..3).map { minimum + (maximum - minimum) * ((3 - it) / 3.0) }, money)
            val xLabels =
                (0..3).map { index ->
                    val fraction = index / 3.0
                    val amount = (end / unit) * fraction
                    val formatted =
                        if (amount % 1.0 == 0.0) {
                            String.format(Locale.US, "%.0f", amount)
                        } else {
                            String.format(Locale.US, "%.1f", amount)
                        }
                    "$formatted$unitSuffix"
                }
            return CareerChartState(points, minimum, maximum, endText, yLabels, xLabels)
        }
    }
}

internal data class UnlockSection(
    val title: String,
    val rows: List<String>,
)

internal fun LevelUnlocks.sections(): List<UnlockSection> =
    listOf(
        UnlockSection("Crops", crops.map { it.plantingName } + fruitTrees.map { it.name }),
        UnlockSection(
            "Farmland",
            if (farmlandTiles > 0) listOf("$farmlandTiles more tiles, $farmlandTotal in all") else emptyList(),
        ),
        UnlockSection("Buildings", buildings.map { it.name }),
        UnlockSection("Decorations", decorations.map { it.name }),
        UnlockSection("Trees", trees.map { it.name }),
        UnlockSection(
            "Recipes",
            recipes.map { "${it.output.name} at the ${Building.named(it.buildingID)?.name ?: "workshop"}" },
        ),
        UnlockSection(
            "Upgrades",
            toolUpgrades.map { "${it.tool.displayName} Level ${it.level}" } +
                homeUpgrades.map {
                    val name = Building.named(it.buildingID)?.name ?: it.buildingID
                    "$name Level ${it.level}: ${HomeUpgrade.capacity(it.buildingID, it.level)} animals"
                },
        ),
        UnlockSection(
            "Fishing",
            buildList {
                if (fishRarities.isNotEmpty()) {
                    add("New ${fishRarities.joinToString(" and ") { it.displayName.lowercase() }} fish start to appear")
                }
                if (fishRatesShift) add("Rarer fish are more likely to bite")
            },
        ),
        UnlockSection("Quests", if (stockQuests) listOf("Stock Market quests") else emptyList()),
    ).filter { it.rows.isNotEmpty() }

internal data class DaySummaryState(
    val title: String,
    val rows: List<Pair<String, String>>,
    val total: String,
) {
    companion object {
        fun from(summary: DaySummary): DaySummaryState =
            DaySummaryState(
                "Day ${summary.day} has begun!",
                summary.sales.map { "${it.name} ×${ScreenFormat.number(it.quantity.toLong())}" to it.earnings.dollars },
                summary.earnings.dollars,
            )
    }
}

/**
 * Labels for evenly spaced axis ticks. Whole-number rounding can print two different ticks the same (a range of 2 gives
 * 2, 1, 1, 0), so decimals are added until every different tick reads differently.
 */
internal fun distinctTickLabels(
    ticks: List<Double>,
    money: Boolean,
): List<String> {
    val wanted = ticks.map { Math.round(it * TICK_RESOLUTION) }.distinct().size
    var labels = ticks.map { ScreenFormat.compact(it, money) }
    var digits = 1
    while (labels.distinct().size < wanted && digits <= MAX_TICK_DIGITS) {
        labels = ticks.map { ScreenFormat.compact(it, money, digits) }
        digits++
    }
    return labels
}

private const val TICK_RESOLUTION = 1e6
private const val MAX_TICK_DIGITS = 3
