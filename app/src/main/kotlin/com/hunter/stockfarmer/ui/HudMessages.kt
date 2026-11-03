package com.hunter.stockfarmer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.LevelUnlocks
import com.hunter.stockfarmer.game.Feedback
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.QuestReward
import com.hunter.stockfarmer.game.ToastMessage
import kotlinx.coroutines.delay

/** What the game has to tell the player over the farm: the toast, quest rewards, the level-up and the day summary. */
@Stable
class HudMessages {
    var toast by mutableStateOf<ToastMessage?>(null)
    var levelUnlocks by mutableStateOf<LevelUnlocks?>(null)
    var rewards by mutableStateOf(emptyList<QuestReward>())
    var daySummary by mutableStateOf<DaySummary?>(null)
}

/** The messages for [session], kept up to date from its events while the HUD is composed. */
@Composable
fun rememberHudMessages(session: GameSession): HudMessages {
    val messages = remember { HudMessages() }
    LaunchedEffect(session) { session.newDays.collect { messages.daySummary = it } }
    LaunchedEffect(session) { session.toasts.collect { messages.toast = it } }
    LaunchedEffect(session) {
        session.events.collect { events ->
            Feedback.toast(events)?.let { messages.toast = it }
            events.filterIsInstance<GameEvent.LeveledUp>().lastOrNull()?.let {
                messages.levelUnlocks = LevelUnlocks.unlocked(it.from, it.level)
            }
            Feedback.questBanner(events)?.let { messages.rewards = it }
        }
    }
    LaunchedEffect(messages.toast) {
        val shown = messages.toast ?: return@LaunchedEffect
        delay(shown.durationMillis)
        if (messages.toast == shown) messages.toast = null
    }
    return messages
}
