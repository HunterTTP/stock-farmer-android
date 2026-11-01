package com.hunter.stockfarmer.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.claimCompletedQuests
import com.hunter.stockfarmer.core.rules.prepareQuests
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun MarketQuestScreen(
    session: GameSession,
    onClose: () -> Unit,
    onEvents: (List<GameEvent>) -> Unit = {},
) {
    val reportEvents by rememberUpdatedState(onEvents)
    var rewards by remember(session) { mutableStateOf(emptyList<QuestReward>()) }
    val boards by rememberScreenState(session) {
        session.act { QuestBoardState.all(it, Instant.now()) }
    }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(session, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val events =
                    session.act { engine ->
                        QuestBoardState.all(engine, Instant.now()).forEach {
                            engine.prepareQuests(it.origin, SystemRandomSource)
                        }
                        engine.claimCompletedQuests(SystemRandomSource)
                    }
                QuestReward.from(events).takeIf { it.isNotEmpty() }?.let { rewards = it }
                if (events.isNotEmpty()) reportEvents(events)
                delay(500)
            }
        }
    }
    LaunchedEffect(rewards) {
        if (rewards.isNotEmpty()) {
            delay(4000)
            rewards = emptyList()
        }
    }
    ScreenLayout("Farm quests", session, onClose) {
        if (rewards.isNotEmpty()) QuestRewardCard(rewards)
        if (boards.isEmpty()) {
            Text(
                "Build a Market Stand to get farm quests.",
                style = HudType.subheadline,
                color = Theme.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
        boards.forEach { board ->
            MarketQuestSection(session, board.origin) {
                QuestReward.from(it).takeIf { claimed -> claimed.isNotEmpty() }?.let { claimed -> rewards = claimed }
                onEvents(it)
            }
        }
    }
}

/** Can also be embedded in the building menu for a particular stand. */
@Composable
fun MarketQuestSection(
    session: GameSession,
    origin: GridPoint,
    onEvents: (List<GameEvent>) -> Unit = {},
) = com.hunter.stockfarmer.ui.sheets
    .MarketQuestSection(session, origin, onEvents)
