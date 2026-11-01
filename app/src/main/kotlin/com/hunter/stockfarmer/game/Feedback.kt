package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.firstRejection
import com.hunter.stockfarmer.ui.screens.QuestReward

/** A short message over the farm, with the implement it offers to pick up (iOS `FeedbackPresenter.toast`). */
data class ToastMessage(
    val text: String,
    val tool: HarvestTool? = null,
) {
    /** A banner that offers a tool stays long enough to be tapped. */
    val durationMillis: Long get() = if (tool == null) Feedback.TOAST_MILLIS else Feedback.TOOL_TOAST_MILLIS
}

/** How the game answers what happened, as text: the pure half of iOS `FeedbackPresenter`. */
object Feedback {
    const val TOAST_MILLIS = 2_000L
    const val TOOL_TOAST_MILLIS = 4_000L

    /** A quest banner says what was asked and what it paid, which takes a moment to read. */
    const val QUEST_BANNER_MILLIS = 4_000L

    /** The message for a refusal, which takes precedence over everything else in the batch. */
    fun toast(events: List<GameEvent>): ToastMessage? =
        events.firstRejection?.let { ToastMessage(it.message, it.suggestedTool) }

    /** The quests turned in, if a batch without a refusal holds any. */
    fun questBanner(events: List<GameEvent>): List<QuestReward>? =
        if (events.firstRejection != null) null else QuestReward.from(events).takeIf { it.isNotEmpty() }
}
