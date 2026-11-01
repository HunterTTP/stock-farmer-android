package com.hunter.stockfarmer.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NameCleaningTest {
    @Test fun `namesAreTrimmedLimitedAndBlankOnesFallBack`() {
        assertEquals("Milo", " \nMilo\t ".cleanedName(20, "Cat"))
        assertEquals("Cat", " \n\t ".cleanedName(20, "Cat"))
        assertEquals("a".repeat(20), "a".repeat(30).cleanedName(20, "Cat"))
    }

    @Test fun `the length limit counts grapheme clusters`() {
        assertEquals("👨‍👩‍👧‍👦", "👨‍👩‍👧‍👦a".cleanedName(1, "Cat"))
        assertEquals("e\u0301", "e\u0301x".cleanedName(1, "Cat"))
        assertEquals("Cat", "Milo".cleanedName(0, "Cat"))
        assertEquals("Milo", "\u00a0Milo\u0085".cleanedName(20, "Cat"))
    }
}
