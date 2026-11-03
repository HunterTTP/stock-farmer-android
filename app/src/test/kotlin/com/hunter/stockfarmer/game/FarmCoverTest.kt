package com.hunter.stockfarmer.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FarmCoverTest {
    private val cover = FarmCover()
    private val loop = mutableListOf<Boolean>()

    @Test
    fun `covering pauses the attached frame loop and uncovering resumes it`() {
        cover.attach { loop += it }
        cover.cover(true)
        cover.cover(false)
        assertEquals(listOf(true, false), loop)
    }

    @Test
    fun `a view built under a cover starts paused and one built without is left running`() {
        cover.attach { loop += it }
        assertTrue(loop.isEmpty())
        cover.cover(true)
        val later = mutableListOf<Boolean>()
        cover.attach { later += it }
        assertEquals(listOf(true), later)
    }

    @Test
    fun `a gone view is left alone`() {
        cover.attach { loop += it }
        cover.attach(null)
        cover.cover(true)
        assertTrue(loop.isEmpty())
    }
}
