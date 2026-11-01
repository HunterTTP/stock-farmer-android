package com.hunter.stockfarmer.ui.rewards

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChopProgressTest {
    private val origin = GridPoint(4, 5)
    private val rock = GridPoint(8, 9)

    @Test
    fun `a new chop counts on its tree`() {
        val events = listOf(GameEvent.Chopped(GridPoint(5, 6), origin, 2, 6, true))
        assertEquals(ChopProgress(origin, 2, 6), ChopProgress.of(events))
    }

    @Test
    fun `a new mining pass counts on its rock`() {
        assertEquals(ChopProgress(rock, 1, 4), ChopProgress.of(listOf(GameEvent.Mined(rock, 1, 4, true))))
    }

    @Test
    fun `a hit that is not new shows nothing`() {
        val events = listOf(GameEvent.Chopped(origin, origin, 2, 6, false), GameEvent.Mined(rock, 1, 4, false))
        assertNull(ChopProgress.of(events))
    }

    @Test
    fun `the final pass counts to its own total and lingers less`() {
        val last = ChopProgress.of(listOf(GameEvent.Mined(rock, 4, 4, true)))
        assertEquals(ChopProgress(rock, 4, 4), last)
        assertEquals(ChopProgress.FINAL_LINGER_MILLIS, last?.lingerMillis)
        assertEquals(ChopProgress.LINGER_MILLIS, ChopProgress(rock, 3, 4).lingerMillis)
    }
}
