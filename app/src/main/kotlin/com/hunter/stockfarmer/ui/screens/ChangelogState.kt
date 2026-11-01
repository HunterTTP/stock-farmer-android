package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.model.ChangelogDay
import java.time.LocalDate

internal data class ChangelogState(
    val days: List<ChangelogDay>,
    val query: String = "",
    val picked: Set<LocalDate> = emptySet(),
    val expanded: Set<String> = days.take(1).map { it.date }.toSet(),
    val page: Int = 0,
) {
    val isSearching: Boolean get() = query.trim().isNotEmpty()
    private val matching: List<ChangelogDay> get() {
        val first = picked.minOrNull()?.toString()
        val last = picked.maxOrNull()?.toString()
        val needle = query.trim()
        return days.mapNotNull { day ->
            if (first != null && last != null && day.date !in first..last) return@mapNotNull null
            if (needle.isEmpty()) return@mapNotNull day
            val lines = day.lines.filter { it.contains(needle, ignoreCase = true) }
            if (lines.isEmpty()) null else ChangelogDay(day.date, lines)
        }
    }
    val pageCount: Int get() = ((matching.size + DAYS_PER_PAGE - 1) / DAYS_PER_PAGE).coerceAtLeast(1)
    val shown: List<ChangelogDay> get() =
        matching.drop(page.coerceIn(0, pageCount - 1) * DAYS_PER_PAGE).take(DAYS_PER_PAGE)

    fun isExpanded(date: String): Boolean = isSearching || date in expanded

    fun search(query: String): ChangelogState = copy(query = query, page = 0)

    fun pick(dates: Set<LocalDate>): ChangelogState = copy(picked = dates.toSet(), page = 0)

    fun goToPage(page: Int): ChangelogState = copy(page = page.coerceIn(0, pageCount - 1))

    fun toggle(date: String): ChangelogState =
        if (isSearching) this else copy(expanded = if (date in expanded) expanded - date else expanded + date)

    companion object {
        const val DAYS_PER_PAGE = 7
    }
}
