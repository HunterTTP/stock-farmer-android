package com.hunter.stockfarmer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.rules.LevelUnlocks
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.hud.BannerBox
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import com.hunter.stockfarmer.ui.theme.Theme
import kotlinx.coroutines.delay

@Composable
fun LevelUpScreen(
    session: GameSession,
    onClose: () -> Unit,
    unlocks: LevelUnlocks = session.read { LevelUnlocks.unlocked(it.level - 1, it.level) },
) {
    val title =
        if (unlocks.levels.first == unlocks.levels.last) {
            "Level ${unlocks.levels.last}!"
        } else {
            "Levels ${unlocks.levels.first}–${unlocks.levels.last}!"
        }
    ScreenLayout(title, session, onClose, closeLabel = "Continue") {
        unlocks.sections().forEach { section ->
            ScreenCard {
                Text(section.title, style = MaterialTheme.typography.titleMedium, color = Theme.xp)
                section.rows.forEach { Text(it) }
            }
        }
    }
}

/** Pass the summary returned by beginNewDayIfDue; opening the banner never advances the clock. */
@Composable
fun DaySummaryBanner(
    session: GameSession,
    onClose: () -> Unit,
    summary: DaySummary? = null,
) {
    if (summary == null) return
    val state = remember(summary) { DaySummaryState.from(summary) }
    LaunchedEffect(session, summary) {
        delay(12_000)
        onClose()
    }
    HudMaterialTheme {
        BannerBox {
            Column(Modifier.widthIn(max = 360.dp).padding(16.dp)) {
                ScreenCard {
                    Text(state.title, style = MaterialTheme.typography.titleMedium)
                    if (state.rows.isEmpty()) {
                        Text("Nothing was sold yesterday")
                    } else {
                        Text("Sold yesterday")
                        Column(
                            Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.rows.forEach { (name, earnings) -> Figure(name, earnings, Theme.accent) }
                        }
                        Figure("Total", state.total, Theme.accent)
                    }
                    Button(onClick = onClose) { Text("Close") }
                }
            }
        }
    }
}

@Composable
fun QuestBanner(
    session: GameSession,
    onClose: () -> Unit,
    rewards: List<QuestReward> = emptyList(),
) {
    if (rewards.isEmpty()) return
    LaunchedEffect(session, rewards) {
        delay(4000)
        onClose()
    }
    HudMaterialTheme {
        BannerBox {
            Column(Modifier.widthIn(max = 360.dp).padding(16.dp)) { QuestRewardCard(rewards) }
        }
    }
}

@Composable
internal fun QuestRewardCard(rewards: List<QuestReward>) {
    ScreenCard {
        Text(
            if (rewards.size > 1) "Quests complete!" else "Quest complete!",
            style = MaterialTheme.typography.titleMedium,
        )
        rewards.forEach {
            Text(it.summary)
            if (it.xp > 0) Text("+${ScreenFormat.number(it.xp.toLong())} XP", color = Theme.xp)
        }
    }
}
