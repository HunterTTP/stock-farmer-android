package com.hunter.stockfarmer.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HudNavigationSaveErrorTest {
    private val navigation = HudNavigation()
    private val error = "The farm could not be renamed. Please retry."

    @Test
    fun `the HUD shows the save error while Load Game is closed`() {
        assertEquals(error, navigation.hudSaveError(error))
    }

    @Test
    fun `the HUD stays quiet while Load Game shows the error itself`() {
        navigation.isLoadGameShown = true
        assertNull(navigation.hudSaveError(error))
        navigation.isLoadGameShown = false
        assertEquals(error, navigation.hudSaveError(error))
    }

    @Test
    fun `no error stays no error`() {
        assertNull(navigation.hudSaveError(null))
    }
}
