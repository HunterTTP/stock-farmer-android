package com.hunter.stockfarmer.core.feedback

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HapticCueTest {
    private val planted = GameEvent.Moved(GridPoint(1, 1))
    private val sold = GameEvent.StorageSold("wheat", 1, 5.0)

    @Test
    fun `nothing happening is silent`() {
        assertNull(HapticCue.cue(emptyList()))
    }

    @Test
    fun `a refusal wins over everything`() {
        val events = listOf(sold, GameEvent.Rejected(Rejection.FishNotBiting))
        assertEquals(HapticCue.Warning, HapticCue.cue(events))
    }

    @Test
    fun `a level reached is success and beats money`() {
        val events = listOf(sold, GameEvent.LeveledUp(2, 0, 1))
        assertEquals(HapticCue.Success, HapticCue.cue(events))
    }

    @Test
    fun `a finished quest is success`() {
        assertEquals(HapticCue.Success, HapticCue.cue(listOf(GameEvent.QuestCompleted(10, "Sell wheat"))))
    }

    @Test
    fun `money changing hands is medium`() {
        assertEquals(HapticCue.Medium, HapticCue.cue(listOf(planted, sold)))
    }

    @Test
    fun `anything else is light`() {
        assertEquals(HapticCue.Light, HapticCue.cue(listOf(planted)))
    }
}
