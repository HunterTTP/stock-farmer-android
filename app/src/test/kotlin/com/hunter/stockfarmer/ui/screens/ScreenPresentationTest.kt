package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.model.ItemSale
import com.hunter.stockfarmer.core.model.ValueSample
import com.hunter.stockfarmer.core.rules.LevelUnlocks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class ScreenPresentationTest {
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
