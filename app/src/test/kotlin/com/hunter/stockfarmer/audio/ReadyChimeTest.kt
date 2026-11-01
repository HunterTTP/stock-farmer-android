package com.hunter.stockfarmer.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReadyChimeTest {
    private val beet = ReadyCrop("beetroot", "Beetroot")
    private val carrot = ReadyCrop("carrot", "Carrot")

    @Test fun `first partial field is silent but a whole ready field announces once`() {
        val chime = ReadyChime()
        assertNull(chime.update(false, listOf(beet)))
        assertEquals("All crops ready to harvest!", chime.update(true, listOf(beet)))
        assertNull(chime.update(true, listOf(beet)))
        chime.update(false, emptyList())
        assertEquals("All crops ready to harvest!", chime.update(true, listOf(beet)))
    }

    @Test fun `newly ready crop groups preserve names order and grammar`() {
        val chime = ReadyChime()
        chime.update(false, emptyList())
        assertEquals("Beetroot and Carrot are ready to harvest!", chime.update(false, listOf(beet, carrot)))
        assertNull(chime.update(false, listOf(beet, carrot)))
        chime.update(false, listOf(beet))
        assertEquals("Carrot is ready to harvest!", chime.update(false, listOf(beet, carrot)))
        chime.clear()
        assertNull(chime.update(false, listOf(carrot)))
    }
}
