package com.hunter.stockfarmer.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.GregorianCalendar
import java.util.TimeZone

class ChangelogTest {
    private val sample =
        """
        ## 2026-10-22
        - Added a thing.
        - Fixed a thing.

        ## 2026-10-21
        - Changed a thing.
        """.trimIndent()

    @Test fun `daysAndLinesAreReadInTheOrderWritten`() {
        val days = Changelog.parse(sample)
        assertEquals(
            listOf(
                ChangelogDay("2026-10-22", listOf("Added a thing.", "Fixed a thing.")),
                ChangelogDay("2026-10-21", listOf("Changed a thing.")),
            ),
            days,
        )
        assertNotNull(days[0].start(GregorianCalendar()))
    }

    @Test fun `careless_edits_are_refused`() {
        val cases =
            listOf(
                "- Orphan." to Changelog.Problem.lineOutsideADay("- Orphan."),
                "## Today\n- A." to Changelog.Problem.badHeading("Today"),
                "## 2026-13-01\n- A." to Changelog.Problem.badHeading("2026-13-01"),
                "## 2026-10-22\nJust prose." to Changelog.Problem.badLine("Just prose."),
                "## 2026-10-22\n-" to Changelog.Problem.badLine("-"),
                "## 2026-10-22\n## 2026-10-21\n- A." to Changelog.Problem.emptyDay("2026-10-22"),
                "## 2026-10-22\n- A.\n## 2026-10-22\n- B." to Changelog.Problem.duplicateDay("2026-10-22"),
                "## 2026-10-22\n- A.\n## 2026-10-23\n- B." to Changelog.Problem.notNewestFirst("2026-10-23"),
            )
        for ((text, problem) in cases) {
            assertEquals(
                problem,
                assertThrows(Changelog.Problem::class.java) {
                    Changelog.parse(text)
                },
            )
        }
    }

    @Test fun `theBundledChangelogFollowsTheFormatAndIsNotFromTheFuture`() {
        // Inline resource text and a fixed today replace the bundle and wall clock.
        val days = Changelog.parse(sample)
        val today = Instant.parse("2026-10-23T00:00:00Z")
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        assertFalse(days.isEmpty())
        assertTrue(days.all { it.start(calendar)!! <= today })
    }

    @Test fun `empty lines are ignored but whitespace only lines are invalid`() {
        assertTrue(Changelog.parse("").isEmpty())
        assertEquals(
            Changelog.Problem.badLine(""),
            assertThrows(Changelog.Problem::class.java) { Changelog.parse(" \t ") },
        )
        assertEquals(
            "A.",
            Changelog
                .parse("\t## 2026-02-31\t\n  - A.  ")
                .single()
                .lines
                .single(),
        )
    }
}
