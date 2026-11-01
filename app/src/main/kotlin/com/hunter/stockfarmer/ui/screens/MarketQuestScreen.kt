package com.hunter.stockfarmer.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.claimCompletedQuests
import com.hunter.stockfarmer.core.rules.claimQuest
import com.hunter.stockfarmer.core.rules.prepareQuests
import com.hunter.stockfarmer.core.rules.replaceQuest
import com.hunter.stockfarmer.core.rules.upgradeMarketStand
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.game.GameSession
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.UUID

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
        if (boards.isEmpty()) Text("Build a Market Stand to get farm quests.")
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
) {
    val board by rememberScreenState(session to origin) {
        session.act { QuestBoardState.all(it, Instant.now()).firstOrNull { board -> board.origin == origin } }
    }
    var replacing by remember(session, origin) { mutableStateOf<UUID?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session, origin) { session.act { it.prepareQuests(origin, SystemRandomSource) } }
    board?.let { state ->
        Text(state.name, style = MaterialTheme.typography.titleMedium)
        state.upgrade?.let { upgrade ->
            UpgradeCard(upgrade) {
                val events = session.act { it.upgradeMarketStand(origin, Instant.now(), SystemRandomSource) }
                message =
                    events
                        .filterIsInstance<GameEvent.Rejected>()
                        .firstOrNull()
                        ?.rejection
                        ?.message
                onEvents(events)
            }
        }
        message?.let { Text(it) }
        state.quests.forEach { quest ->
            ScreenCard {
                Text(quest.reward, color = FarmPurple, style = MaterialTheme.typography.titleMedium)
                quest.objectives.forEach { objective ->
                    ScreenIcon(objective.art, objective.symbol)
                    Text(objective.title)
                    LinearProgressIndicator(progress = { objective.fraction })
                    Text(objective.progress, style = MaterialTheme.typography.labelMedium)
                    objective.hint?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (quest.complete) {
                    Button(onClick = {
                        onEvents(session.act { it.claimQuest(quest.id, origin, SystemRandomSource) })
                    }) { Text("Claim reward") }
                } else {
                    TextButton(onClick = { replacing = quest.id }) { Text("Replace quest") }
                }
            }
        }
    }
    replacing?.let { id ->
        AlertDialog(
            onDismissRequest = { replacing = null },
            title = { Text("Replace this quest?") },
            text = { Text("Its progress will be lost. A new quest is free and has no deadline.") },
            confirmButton = {
                TextButton(onClick = {
                    session.act { it.replaceQuest(id, origin, SystemRandomSource) }
                    replacing = null
                }) { Text("Replace quest") }
            },
            dismissButton = { TextButton(onClick = { replacing = null }) { Text("Cancel") } },
        )
    }
}
