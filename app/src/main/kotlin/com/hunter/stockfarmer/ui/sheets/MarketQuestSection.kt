package com.hunter.stockfarmer.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.core.rules.FruitTreePhase
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.QuestRules
import com.hunter.stockfarmer.core.rules.prepareQuests
import com.hunter.stockfarmer.core.rules.quests
import com.hunter.stockfarmer.core.rules.replaceQuest
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.rememberPolledState
import com.hunter.stockfarmer.ui.screens.BuildingUpgradeScreen
import com.hunter.stockfarmer.ui.screens.PillButton
import com.hunter.stockfarmer.ui.screens.QuestBoardState
import com.hunter.stockfarmer.ui.screens.QuestCardState
import com.hunter.stockfarmer.ui.screens.QuestObjectiveState
import com.hunter.stockfarmer.ui.screens.ScreenArt
import com.hunter.stockfarmer.ui.screens.ScreenIcon
import com.hunter.stockfarmer.ui.screens.iconSymbol
import com.hunter.stockfarmer.ui.screens.item
import com.hunter.stockfarmer.ui.theme.HudIcon
import com.hunter.stockfarmer.ui.theme.HudIcons
import com.hunter.stockfarmer.ui.theme.HudMaterialTheme
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard
import java.time.Instant
import java.util.UUID

/** Can also be embedded in the building menu for a particular stand. */
@Composable
fun MarketQuestSection(
    session: GameSession,
    origin: GridPoint,
    onEvents: (List<GameEvent>) -> Unit = {},
) {
    val presentation by rememberPolledState(session to origin) {
        session.act { engine ->
            QuestBoardState.all(engine, Instant.now()).firstOrNull { it.origin == origin } to
                engine.quests(origin).associate { quest ->
                    quest.id to quest.objectives.map { questObjectiveIcons(it.goal) }
                }
        }
    }
    val (board, icons) = presentation
    var replacing by remember(session, origin) { mutableStateOf<UUID?>(null) }
    var upgrading by remember(session, origin) { mutableStateOf(false) }
    LaunchedEffect(session, origin) { session.prepareQuests(origin) }
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
                        style = HudType.subheadline.copy(fontFeatureSettings = "tnum"),
                        color = Theme.textSecondary,
                    )
                }
            }
            state.quests.forEach { quest ->
                QuestCard(
                    quest = quest,
                    icons = icons[quest.id].orEmpty(),
                    onReplace = { replacing = quest.id },
                )
            }
        }
    }
    if (upgrading) {
        Dialog(
            onDismissRequest = { upgrading = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BuildingUpgradeScreen(
                session = session,
                onClose = { upgrading = false },
                origin = origin,
                onEvents = onEvents,
            )
        }
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
                        session.commit()
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
    icons: List<List<QuestObjectiveIcon>>,
    onReplace: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .categoryCard(isHighlighted = false),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudIcon(HudIcons.starFill, Theme.accent, size = 17.dp)
            Text(quest.reward, color = Theme.accent, style = HudType.headline)
        }
        quest.objectives.forEachIndexed { index, objective ->
            ObjectiveRow(objective, icons.getOrElse(index) { emptyList() })
        }
        TextButton(onClick = onReplace, modifier = Modifier.heightIn(min = 44.dp)) {
            Text("Replace quest", style = HudType.subheadline, color = Theme.textPrimary)
        }
    }
}

@Composable
private fun ObjectiveRow(
    objective: QuestObjectiveState,
    icons: List<QuestObjectiveIcon>,
) {
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.size(72.dp, 44.dp).clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icons.forEach { icon ->
                    Box(Modifier.size(icon.width.dp, icon.height.dp), contentAlignment = Alignment.Center) {
                        if (icon.isFlour) {
                            HudIcon(HudIcons.bagFill, Color.White, size = 30.dp)
                        } else if (icon.art == null) {
                            Text(icon.symbol, fontSize = 30.sp)
                        } else {
                            ScreenIcon(icon.art, icon.symbol, size = maxOf(icon.width, icon.height).dp)
                        }
                    }
                }
            }
            Text(
                objective.title,
                modifier = Modifier.weight(1f),
                style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = Theme.textPrimary,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(objective.fraction.coerceIn(0f, 1f))
                        .background(Theme.accent, RoundedCornerShape(2.dp)),
                )
            }
            Text(
                objective.progress,
                style = HudType.caption.copy(fontFeatureSettings = "tnum"),
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

internal data class QuestObjectiveIcon(
    val art: ScreenArt?,
    val symbol: String = "",
    val width: Int = 44,
    val height: Int = 44,
    val isFlour: Boolean = false,
)

internal fun questObjectiveIcons(goal: QuestGoal): List<QuestObjectiveIcon> {
    fun item(item: StorageItem) =
        QuestObjectiveIcon(
            ScreenArt.item(item),
            item.iconSymbol,
            isFlour =
                item == StorageItem.Flour,
        )

    fun building(id: String) =
        Building.named(id)?.let {
            QuestObjectiveIcon(ScreenArt.image("buildings", it.assetName), width = 40)
        }
    return when (goal) {
        is QuestGoal.Harvest -> {
            Crop.named(goal.id)?.let { listOf(item(StorageItem.CropItem(it))) }.orEmpty()
        }

        is QuestGoal.Collect -> {
            StorageItem.of(goal.id)?.let { listOf(item(it)) }.orEmpty()
        }

        is QuestGoal.Workshop -> {
            buildList {
                building(goal.id)?.let(::add)
                Building.named(goal.id)?.let(Recipe::of)?.output?.let {
                    add(item(it).copy(width = 28, height = 32))
                }
            }
        }

        is QuestGoal.Feed -> {
            buildList {
                add(QuestObjectiveIcon(ScreenArt.crop(Crop.starter), width = 28, height = 32))
                building(goal.id)?.let(::add)
            }
        }

        QuestGoal.StockProfit, QuestGoal.StockValue -> {
            listOfNotNull(building("house1"))
        }

        QuestGoal.PlantTrees -> {
            buildList {
                add(
                    QuestObjectiveIcon(
                        ScreenArt.image(
                            "trees",
                            Tree.starter.assetName(
                                Tree.starter.species.colors
                                    .first(),
                            ),
                        ),
                        width = 32,
                    ),
                )
                Crop.all.firstOrNull { it.isFruitTree }?.let {
                    add(
                        QuestObjectiveIcon(
                            ScreenArt.image("crops/fruit_trees", it.treeAssetName(FruitTreePhase.ripe)),
                            width = 36,
                        ),
                    )
                }
            }
        }
    }
}

/** Fills the stand's board, committing only when the board changed, as iOS does. */
private fun GameSession.prepareQuests(origin: GridPoint) {
    val changed =
        act {
            val before = it.state.marketQuests.toMap()
            it.prepareQuests(origin, SystemRandomSource)
            it.state.marketQuests != before
        }
    if (changed) commit()
}
