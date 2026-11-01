@file:Suppress("ktlint:standard:class-naming")

package com.hunter.stockfarmer.core.model

import java.time.Instant
import java.util.Calendar

class ChangelogDay(
    val date: String,
    lines: List<String>,
) {
    val lines: List<String> = java.util.List.copyOf(lines)
    val id: String get() = date

    fun start(calendar: Calendar = Calendar.getInstance()): Instant? {
        val parts = date.split('-').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        val day = calendar.clone() as Calendar
        day.clear()
        day.set(parts[0], parts[1] - 1, parts[2])
        return runCatching { day.toInstant() }.getOrNull()
    }

    override fun equals(other: Any?): Boolean = other is ChangelogDay && date == other.date && lines == other.lines

    override fun hashCode(): Int = 31 * date.hashCode() + lines.hashCode()

    override fun toString(): String = "ChangelogDay(date=$date, lines=$lines)"
}

/** Reads supplied markdown text; resource loading belongs to the application. */
object Changelog {
    sealed class Problem : Exception() {
        data class lineOutsideADay(
            val line: String,
        ) : Problem()

        data class badHeading(
            val heading: String,
        ) : Problem()

        data class badLine(
            val line: String,
        ) : Problem()

        data class emptyDay(
            val date: String,
        ) : Problem()

        data class duplicateDay(
            val date: String,
        ) : Problem()

        data class notNewestFirst(
            val date: String,
        ) : Problem()
    }

    fun parse(text: String): List<ChangelogDay> {
        val days = mutableListOf<ChangelogDay>()
        var date: String? = null
        var lines = mutableListOf<String>()

        fun finishDay() {
            val current = date ?: return
            if (lines.isEmpty()) throw Problem.emptyDay(current)
            days.lastOrNull()?.let { previous ->
                if (previous.date == current) throw Problem.duplicateDay(current)
                if (previous.date <= current) throw Problem.notNewestFirst(current)
            }
            days.add(ChangelogDay(current, lines))
            lines = mutableListOf()
        }
        for (rawLine in text.split('\n').filter { it.isNotEmpty() }) {
            val line = rawLine.trim(::isHorizontalWhitespace)
            when {
                line.startsWith("## ") -> {
                    finishDay()
                    val heading = line.drop(3)
                    if (!isDate(heading)) throw Problem.badHeading(heading)
                    date = heading
                }

                line.startsWith("- ") -> {
                    if (date == null) throw Problem.lineOutsideADay(line)
                    val change = line.drop(2).trim(::isHorizontalWhitespace)
                    if (change.isEmpty()) throw Problem.badLine(line)
                    lines.add(change)
                }

                else -> {
                    throw Problem.badLine(line)
                }
            }
        }
        finishDay()
        return days.toList()
    }

    private fun isDate(text: String): Boolean {
        val parts = text.split('-')
        if (parts.size != 3 || parts[0].length != 4 || parts[1].length != 2 || parts[2].length != 2) return false
        val year = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val day = parts[2].toIntOrNull() ?: return false
        return year > 2000 && month in 1..12 && day in 1..31
    }

    private fun isHorizontalWhitespace(char: Char): Boolean =
        char == '\t' || Character.getType(char) == Character.SPACE_SEPARATOR.toInt()
}
