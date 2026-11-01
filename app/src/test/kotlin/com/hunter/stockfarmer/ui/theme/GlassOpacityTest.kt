package com.hunter.stockfarmer.ui.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GlassOpacityTest {
    @Test
    fun glassBaseIsMostlyOpaqueSoTheFarmDoesNotShowThrough() {
        assertEquals(Theme.GLASS_BASE_ALPHA, Theme.glassBase.alpha, 0.01f)
        assertTrue(Theme.GLASS_BASE_ALPHA >= 0.85f)
    }

    @Test
    fun glassWashMatchesIos() {
        assertEquals(0.5f, Theme.GLASS_WASH, 0f)
    }
}
