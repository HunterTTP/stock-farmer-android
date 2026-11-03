package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.firstRejection
import com.hunter.stockfarmer.ui.screens.QuestReward

/** A short message over the farm, with the implement it offers to pick up. */
data class ToastMessage(
    val text: String,
    val tool: HarvestTool? = null,
    val durationMillis: Long = if (tool == null) Feedback.TOAST_MILLIS else Feedback.TOOL_TOAST_MILLIS,
)

/** How the game answers what happened, as text: the pure half of the feedback presenter. */
object Feedback {
    const val TOAST_MILLIS = 2_000L
    const val TOOL_TOAST_MILLIS = 4_000L

    /** The message for a refusal, which takes precedence over everything else in the batch. */
    fun toast(events: List<GameEvent>): ToastMessage? =
        events.firstRejection?.let { ToastMessage(it.message, it.suggestedTool) }
            ?: events.filterIsInstance<GameEvent.AnimalSold>().firstOrNull()?.let {
                ToastMessage(
                    "Sold for ${it.value.dollars}",
                )
            }

    /** The quests turned in, if a batch without a refusal holds any. */
    fun questBanner(events: List<GameEvent>): List<QuestReward>? =
        if (events.firstRejection != null) null else QuestReward.from(events).takeIf { it.isNotEmpty() }
}
