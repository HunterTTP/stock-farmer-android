package com.hunter.stockfarmer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.QuestRules
import com.hunter.stockfarmer.core.rules.claimCompletedQuests
import com.hunter.stockfarmer.core.rules.claimQuest
import com.hunter.stockfarmer.core.rules.prepareQuests
import com.hunter.stockfarmer.core.rules.replaceQuest
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard
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
) {
    val board by rememberScreenState(session to origin) {
        session.act { QuestBoardState.all(it, Instant.now()).firstOrNull { board -> board.origin == origin } }
    }
    var replacing by remember(session, origin) { mutableStateOf<UUID?>(null) }
    var upgrading by remember(session, origin) { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session, origin) { session.act { it.prepareQuests(origin, SystemRandomSource) } }
    board?.let { state ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Farm quests",
                style = HudType.title2.copy(fontWeight = FontWeight.Bold),
                color = Theme.textPrimary,
            )
            if (!state.isUpgraded) {
                if (state.completedQuests >= QuestRules.QUESTS_TO_UPGRADE) {
                    PillButton(
                        title = "Upgrade",
                        fill = Theme.xp,
                        text = Color.White,
                        modifier = Modifier.alpha(if (state.canAffordUpgrade) 1f else 0.4f),
                        onClick = { upgrading = true },
                    )
                } else {
                    Text(
                        "Quests completed: ${state.completedQuests}/${QuestRules.QUESTS_TO_UPGRADE}",
                        style = HudType.subheadline,
                        color = Theme.textSecondary,
                    )
                }
            }
            message?.let { Text(it, style = HudType.subheadline, color = Theme.spend) }
            state.quests.forEach { quest ->
                QuestCard(
                    quest = quest,
                    onClaim = {
                        val events = session.act { it.claimQuest(quest.id, origin, SystemRandomSource) }
                        onEvents(events)
                    },
                    onReplace = { replacing = quest.id },
                )
            }
        }
    }
    if (upgrading) {
        BuildingUpgradeScreen(
            session = session,
            onClose = { upgrading = false },
            origin = origin,
            onEvents = onEvents,
        )
    }
    replacing?.let { id ->
        HudMaterialTheme {
            AlertDialog(
                onDismissRequest = { replacing = null },
                title = { Text("Replace this quest?", style = HudType.headline, color = Theme.textPrimary) },
                text = {
                    Text(
                        "Its progress will be lost. A new quest is free and has no deadline.",
                        style = HudType.subheadline,
                        color = Theme.textSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        session.act { it.replaceQuest(id, origin, SystemRandomSource) }
                        replacing = null
                    }) {
                        Text(
                            "Replace quest",
                            color = Theme.spend,
                            style = HudType.subheadline.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { replacing = null }) {
                        Text("Cancel", color = Theme.textSecondary, style = HudType.subheadline)
                    }
                },
            )
        }
    }
}

@Composable
private fun QuestCard(
    quest: QuestCardState,
    onClaim: () -> Unit,
    onReplace: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .categoryCard(isHighlighted = false)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("★", color = Theme.accent, style = HudType.headline)
            Text(quest.reward, color = Theme.accent, style = HudType.headline)
        }
        quest.objectives.forEach { objective ->
            ObjectiveRow(objective)
        }
        if (quest.complete) {
            PillButton(
                title = "Claim reward",
                fill = Theme.accent,
                text = Theme.onAccent,
                onClick = onClaim,
            )
        } else {
            TextButton(
                onClick = onReplace,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text(
                    "Replace quest",
                    style = HudType.subheadline,
                    color = Theme.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ObjectiveRow(objective: QuestObjectiveState) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenIcon(objective.art, objective.symbol, size = 40.dp)
            Text(
                objective.title,
                modifier = Modifier.weight(1f),
                style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = Theme.textPrimary,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(objective.fraction.coerceIn(0f, 1f))
                        .background(Theme.accent, RoundedCornerShape(4.dp)),
                )
            }
            Text(
                objective.progress,
                style = HudType.caption,
                color = Theme.textSecondary,
            )
        }
        objective.hint?.let {
            Text(
                it,
                style = HudType.caption,
                color = Theme.textSecondary,
            )
        }
    }
}
