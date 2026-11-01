package com.hunter.stockfarmer.ui.fishing

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FishingOverlayTest {
    @Test
    fun `the catch message takes an before a vowel and a otherwise`() {
        assertEquals("You caught an Eel!", catchMessage("Eel"))
        assertEquals("You caught a Carp!", catchMessage("Carp"))
    }

    @Test
    fun `the catch bar runs from the game's green through yellow to red`() {
        assertEquals(Color.hsv(0.332f * 360, 0.48f, 1f), catchBarColor(0.0))
        assertEquals(Color.hsv(0.332f * 180, 0.68f, 1f), catchBarColor(0.5))
        assertEquals(Color.hsv(0f, 0.88f, 1f), catchBarColor(1.0))
        assertEquals(catchBarColor(1.0), catchBarColor(3.0))
        assertEquals(catchBarColor(0.0), catchBarColor(-1.0))
    }
}
