package com.hunter.stockfarmer.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DurationTextTest {
    @Test fun `usesOneLetterUnitsRightAfterTheirNumbers`() {
        assertEquals("45s", 45.0.compactDurationText)
        assertEquals("1m 5s", 65.0.compactDurationText)
        assertEquals("2m", 120.0.compactDurationText)
        assertEquals("3h 20m", (3.0 * 3600 + 20 * 60 + 15).compactDurationText)
        assertEquals("2h", (2.0 * 3600).compactDurationText)
    }

    @Test fun `countsDaysAndKeepsToTheTwoLargestUnits`() {
        assertEquals("1d", 86_400.0.compactDurationText)
        assertEquals("2d 5h", 194_340.0.compactDurationText)
        assertEquals("1d", (86_400.0 + 30 * 60).compactDurationText)
    }

    @Test fun `showsTheTwoLargestNonZeroUnits`() {
        assertEquals("45 sec", 45.0.durationText)
        assertEquals("1 min 5 sec", 65.0.durationText)
        assertEquals("2 min", 120.0.durationText)
        assertEquals("3 h 20 min", (3.0 * 3600 + 20 * 60 + 15).durationText)
        assertEquals("2 h", (2.0 * 3600).durationText)
    }

    @Test fun `roundsUpAndNeverShowsNegativeTime`() {
        assertEquals("1s", 0.2.compactDurationText)
        assertEquals("0s", (-5.0).compactDurationText)
        assertEquals("1 sec", 0.2.durationText)
        assertEquals("0 sec", 0.0.durationText)
        assertEquals("0 sec", (-5.0).durationText)
    }
}
