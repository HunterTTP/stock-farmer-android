package com.hunter.stockfarmer.ui.hud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolRouteStackTest {
    @Test
    fun `a new sheet shows its root and cannot go back`() {
        val stack = ToolRouteStack(ToolRoute.Settings)
        assertEquals(ToolRoute.Settings, stack.current)
        assertFalse(stack.canGoBack)
        assertNull(stack.back())
    }

    @Test
    fun `pushing opens a screen on top and back returns to the last`() {
        val stack = ToolRouteStack(ToolRoute.Settings).push(ToolRoute.About).push(ToolRoute.Privacy)
        assertEquals(ToolRoute.Privacy, stack.current)
        assertEquals(ToolRoute.Settings, stack.root)
        val up = stack.back()
        assertEquals(ToolRoute.About, up?.current)
        assertEquals(ToolRoute.Settings, up?.back()?.current)
        assertNull(up?.back()?.back())
    }

    @Test
    fun `pushing the screen already showing is ignored`() {
        val stack = ToolRouteStack(ToolRoute.Settings).push(ToolRoute.About)
        assertEquals(stack, stack.push(ToolRoute.About))
        assertTrue(stack.push(ToolRoute.Changelog).canGoBack)
    }

    @Test
    fun `an empty stack is refused`() {
        assertThrows(IllegalArgumentException::class.java) { ToolRouteStack(emptyList()) }
    }
}
