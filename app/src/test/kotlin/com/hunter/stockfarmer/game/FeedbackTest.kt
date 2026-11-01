package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.Rejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FeedbackTest {
    @Test
    fun `a refusal becomes a toast offering the tool it suggests`() {
        val toast = Feedback.toast(listOf(GameEvent.Rejected(Rejection.UseTheAxe)))
        assertEquals(Rejection.UseTheAxe.message, toast?.text)
        assertEquals(HarvestTool.axe, toast?.tool)
        assertEquals(Feedback.TOOL_TOAST_MILLIS, toast?.durationMillis)
    }

    @Test
    fun `a refusal without a tool is shown for two seconds`() {
        val toast = Feedback.toast(listOf(GameEvent.Rejected(Rejection.StorageFull)))
        assertNull(toast?.tool)
        assertEquals(Feedback.TOAST_MILLIS, toast?.durationMillis)
    }

    @Test
    fun `nothing refused means no toast`() {
        assertNull(Feedback.toast(emptyList()))
    }

    @Test
    fun `quests turned in make a banner unless the batch was refused`() {
        val quest = GameEvent.QuestCompleted(50, "Sell 3 wheat")
        assertEquals(listOf("Sell 3 wheat"), Feedback.questBanner(listOf(quest))?.map { it.summary })
        assertNull(Feedback.questBanner(listOf(GameEvent.Rejected(Rejection.StorageFull), quest)))
        assertNull(Feedback.questBanner(emptyList()))
    }
}
