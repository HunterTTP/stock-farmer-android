package com.hunter.stockfarmer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.LevelUnlocks
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.hud.BannerBox
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.hudPanel
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
    BackHandler(onBack = onClose)
    HudMaterialTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 420.dp)
                        .hudPanel(22.dp)
                        .padding(18.dp)
                        .clickable(enabled = false) {},
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = HudType.title.copy(fontWeight = FontWeight.ExtraBold),
                    color = Theme.textPrimary,
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    unlocks.sections().forEach { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = section.title.uppercase(),
                                style = HudType.caption.copy(fontWeight = FontWeight.Bold),
                                color = Theme.textSecondary,
                            )
                            section.rows.forEach { rowText ->
                                Text(
                                    text = rowText,
                                    style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                                    color = Theme.textPrimary,
                                )
                            }
                        }
                    }
                }
                PillButton(
                    title = "Continue",
                    fill = Theme.accent,
                    text = Theme.onAccent,
                    onClick = onClose,
                )
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
            Box(
                modifier =
                    Modifier
                        .widthIn(max = 360.dp)
                        .hudPanel(18.dp)
                        .clickable(onClick = onClose)
                        .padding(16.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.title,
                        style = HudType.title3.copy(fontWeight = FontWeight.ExtraBold),
                        color = Theme.textPrimary,
                    )
                    if (summary.sales.isEmpty()) {
                        Text(
                            text = "Nothing was sold yesterday",
                            style = HudType.subheadline,
                            color = Theme.textSecondary,
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Sold yesterday",
                                style = HudType.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = Theme.textSecondary,
                            )
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp)
                                        .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                summary.sales.forEach { sale ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = sale.name,
                                            style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                                            color = Theme.textPrimary,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "×${ScreenFormat.number(sale.quantity.toLong())}",
                                            style = HudType.subheadline,
                                            color = Theme.textSecondary,
                                        )
                                        Text(
                                            text = sale.earnings.dollars,
                                            style = HudType.subheadline.copy(fontWeight = FontWeight.Bold),
                                            color = Theme.money,
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Theme.panelBorder),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Total",
                                    style = HudType.headline,
                                    color = Theme.textPrimary,
                                )
                                Text(
                                    text = summary.earnings.dollars,
                                    style = HudType.headline,
                                    color = Theme.money,
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(44.dp)
                            .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✕",
                        style = HudType.title3,
                        color = Theme.textSecondary,
                    )
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
            Box(
                modifier =
                    Modifier
                        .widthIn(max = 360.dp)
                        .hudPanel(18.dp)
                        .background(Theme.xp.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .border(1.5.dp, Theme.xp, RoundedCornerShape(18.dp))
                        .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (rewards.size > 1) "Quests complete!" else "Quest complete!",
                        style = HudType.title3.copy(fontWeight = FontWeight.ExtraBold),
                        color = Theme.textPrimary,
                    )
                    rewards.forEach { quest ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = quest.summary,
                                style = HudType.subheadline,
                                color = Theme.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                            if (quest.xp > 0) {
                                Text(
                                    text = "+${ScreenFormat.number(quest.xp.toLong())} XP",
                                    style = HudType.headline,
                                    color = Theme.xp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuestRewardCard(rewards: List<QuestReward>) {
    ScreenCard {
        Text(
            text = if (rewards.size > 1) "Quests complete!" else "Quest complete!",
            style = HudType.title3.copy(fontWeight = FontWeight.ExtraBold),
            color = Theme.textPrimary,
        )
        rewards.forEach { quest ->
            Text(quest.summary, style = HudType.subheadline, color = Theme.textSecondary)
            if (quest.xp > 0) {
                Text("+${ScreenFormat.number(quest.xp.toLong())} XP", style = HudType.headline, color = Theme.xp)
            }
        }
    }
}
