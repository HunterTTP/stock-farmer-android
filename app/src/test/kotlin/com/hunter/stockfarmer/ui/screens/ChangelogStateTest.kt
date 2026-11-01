package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.model.ChangelogDay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ChangelogStateTest {
    private val days = (20 downTo 1).map { ChangelogDay("2026-10-%02d".format(it), listOf("Wheat $it", "Farm $it")) }

    @Test
    fun `changelog opens newest day and pages seven recorded days at a time`() {
        val state = ChangelogState(days)
        assertTrue(state.isExpanded(days.first().date))
        assertFalse(state.isExpanded(days[1].date))
        assertEquals(3, state.pageCount)
        assertEquals(days.take(7), state.shown)
        assertEquals(days.drop(7).take(7), state.goToPage(1).shown)
        assertEquals(days.takeLast(6), state.goToPage(99).shown)
        assertEquals(0, state.goToPage(-1).page)
    }

    @Test
    fun `search matches only lines ignoring case and spaces and expands results without changing folds`() {
        val state = ChangelogState(days).toggle(days[1].date).goToPage(2).search("  wHeAt  ")
        assertEquals(0, state.page)
        assertTrue(state.shown.all { it.lines.size == 1 && it.lines.single().startsWith("Wheat") })
        assertTrue(state.isExpanded(days[2].date))
        assertTrue(state.toggle(days[2].date).search("").isExpanded(days[1].date))
        assertFalse(state.toggle(days[2].date).search("").isExpanded(days[2].date))
        assertEquals(1, state.search("no match").pageCount)
        assertTrue(state.search("2026").shown.isEmpty())
        assertEquals(days.take(7), state.search("   ").shown)
    }

    @Test
    fun `calendar includes every day between earliest and latest selections including gaps`() {
        val picked = setOf(LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-06"))
        val state = ChangelogState(days).goToPage(2).pick(picked)
        assertEquals(0, state.page)
        assertEquals(listOf("2026-10-06", "2026-10-05", "2026-10-04", "2026-10-03"), state.shown.map { it.date })
        assertEquals(
            listOf("Wheat 5"),
            state
                .search("Wheat 5")
                .shown
                .single()
                .lines,
        )
        assertEquals(
            "2026-10-03",
            state
                .pick(setOf(picked.min()))
                .shown
                .single()
                .date,
        )
        assertEquals(days.take(7), state.pick(emptySet()).shown)
        assertEquals(1, ChangelogState(emptyList()).pageCount)
    }
}
