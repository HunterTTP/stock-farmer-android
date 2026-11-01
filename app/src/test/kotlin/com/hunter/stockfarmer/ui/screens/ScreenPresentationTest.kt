package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.model.ItemSale
import com.hunter.stockfarmer.core.model.ValueSample
import com.hunter.stockfarmer.core.rules.LevelUnlocks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class ScreenPresentationTest {
    @Test
    fun `quote timestamp uses the requested zone across a calendar boundary`() {
        val time = Instant.parse("2026-01-01T00:05:00Z")
        assertEquals("Jan 1, 2026, 12:05 AM", ScreenFormat.quoteTime(time, ZoneId.of("UTC")))
        assertEquals("Dec 31, 2025, 7:05 PM", ScreenFormat.quoteTime(time, ZoneId.of("America/New_York")))
    }

    @Test
    fun `money and percent formatting retain zero loss and billion dollar precision`() {
        assertEquals("$0", ScreenFormat.signedMoney(0))
        assertEquals("-$0.01", ScreenFormat.signedMoney(-1))
        assertEquals("+$1,000,000,000", ScreenFormat.signedMoney(100000000000))
        assertEquals("+0.00%", ScreenFormat.percent(0.0))
        assertEquals("-100.00%", ScreenFormat.percent(-1.0))
    }

    @Test
    fun `chart axes switch units exactly at two hours and retain fractional ticks`() {
        val before = CareerChartState.from(listOf(ValueSample(7199.0, 3000)))
        assertEquals(listOf("0m", "40.0m", "80.0m", "120.0m"), before.xLabels)
        assertEquals(listOf("3K", "2K", "1K", "0"), before.yLabels)
        val boundary = CareerChartState.from(listOf(ValueSample(7200.0, 3000)))
        assertEquals(listOf("0h", "0.7h", "1.3h", "2h"), boundary.xLabels)
        val later = CareerChartState.from(listOf(ValueSample(10800.0, 3000)))
        assertEquals(listOf("0h", "1h", "2h", "3h"), later.xLabels)
    }

    @Test
    fun `empty axes and all negative money axes include zero`() {
        val empty = CareerChartState.from(emptyList(), money = true)
        assertEquals(List(4) { "$0" }, empty.yLabels)
        assertEquals(List(4) { "0m" }, empty.xLabels)
        assertEquals("0m", empty.endTime)
        val loss =
            CareerChartState.from(
                listOf(ValueSample(0.0, -300000), ValueSample(180.0, -150000)),
                scale = 0.01,
                money = true,
            )
        assertEquals(listOf("$0", "-$1K", "-$2K", "-$3K"), loss.yLabels)
        assertEquals(listOf(ChartPoint(0f, 1f), ChartPoint(1f, 0.5f)), loss.points)
    }

    @Test
    fun `day banner keeps unknown items separate and formats grouped quantities and cents`() {
        val summary =
            DaySummary(
                1234,
                listOf(
                    ItemSale("unknown", "Mystery", 1234567, 0.25),
                    ItemSale("wheat", "Wheat", 1000, 0.5),
                ),
            )
        val state = DaySummaryState.from(summary)
        assertEquals("Day 1234 has begun!", state.title)
        assertEquals(listOf("Mystery ×1,234,567" to "$0.25", "Wheat ×1,000" to "$0.50"), state.rows)
        assertEquals("$0.75", state.total)
        assertEquals("$0", DaySummaryState.from(DaySummary(1, emptyList())).total)
        assertEquals(1234567, summary.sales.first().quantity)
    }

    @Test
    fun `compact labels retain money signs and use catalog scale thresholds`() {
        assertEquals("-$1.3K", ScreenFormat.compact(-1260.0, true))
        assertEquals("1M", ScreenFormat.compact(1_000_000.0))
        assertEquals("$999", ScreenFormat.compact(999.0, true))
        assertEquals("1.2T", ScreenFormat.compact(1.2e12))
        assertEquals("-12.50%", ScreenFormat.percent(-0.125))
        assertEquals("+$12.34", ScreenFormat.signedMoney(1234))
    }

    @Test
    fun `formatting does not depend on device locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1,234,567", ScreenFormat.number(1_234_567))
            assertEquals("1.2K", ScreenFormat.compact(1200.0))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `chart uses actual elapsed play time and includes negative stock profit`() {
        val chart =
            CareerChartState.from(
                listOf(ValueSample(0.0, 0), ValueSample(60.0, -500), ValueSample(240.0, 1500)),
                0.01,
                money = true,
            )
        assertEquals(-5.0, chart.minimum)
        assertEquals(15.0, chart.maximum)
        assertEquals("4m", chart.endTime)
        assertEquals(listOf(0f, 0.25f, 1f), chart.points.map { it.x })
        assertEquals(listOf(0.75f, 1f, 0f), chart.points.map { it.y })
        assertEquals(listOf("$15", "$8", "$2", "-$5"), chart.yLabels)
        assertEquals(listOf("0m", "1.3m", "2.7m", "4m"), chart.xLabels)
    }

    @Test
    fun `empty flat and single point charts have finite coordinates and switch to hours at two hours`() {
        assertTrue(CareerChartState.from(emptyList()).points.isEmpty())
        val flat = CareerChartState.from(listOf(ValueSample(0.0, 0), ValueSample(7200.0, 0)))
        assertEquals("2h", flat.endTime)
        assertTrue(flat.points.all { it.x.isFinite() && it.y.isFinite() })
        assertEquals(ChartPoint(0f, 1f), CareerChartState.from(listOf(ValueSample(0.0, 0))).points.single())
    }

    @Test
    fun `day summary preserves engine order quantities and the previous days total`() {
        val state =
            DaySummaryState.from(
                DaySummary(
                    3,
                    listOf(
                        ItemSale("milk", "Milk", 2, 120.0),
                        ItemSale("wheat", "Wheat", 7, 3.5),
                    ),
                ),
            )
        assertEquals("Day 3 has begun!", state.title)
        assertEquals(listOf("Milk ×2", "Wheat ×7"), state.rows.map { it.first })
        assertEquals("$123.50", state.total)
        assertTrue(DaySummaryState.from(DaySummary(2, emptyList())).rows.isEmpty())
    }

    @Test
    fun `multi level unlocks include each catalog item once and omit empty sections`() {
        val unlocks = LevelUnlocks.unlocked(9, 20)
        val sections = unlocks.sections()
        assertTrue(sections.all { it.rows.isNotEmpty() })
        assertEquals(unlocks.crops.size + unlocks.fruitTrees.size, sections.first { it.title == "Crops" }.rows.size)
        assertEquals(listOf("Stock Market quests"), sections.first { it.title == "Quests" }.rows)
        assertTrue(
            sections
                .first { it.title == "Farmland" }
                .rows
                .single()
                .contains("${unlocks.farmlandTotal} in all"),
        )
    }
}
