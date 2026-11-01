package com.hunter.stockfarmer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.DaySummary
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.LevelUnlocks
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.hud.BannerBox
import com.hunter.stockfarmer.ui.hud.ToolPalette
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.menus.MenuPicture
import com.hunter.stockfarmer.ui.menus.MenuPictures
import com.hunter.stockfarmer.ui.menus.rememberMenuAssetLoader
import com.hunter.stockfarmer.ui.theme.HudIcon
import com.hunter.stockfarmer.ui.theme.HudIcons
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
    val loader = rememberMenuAssetLoader()
    val visible = remember(unlocks) { MutableTransitionState(false).apply { targetState = true } }
    val close by rememberUpdatedState(onClose)
    val dismiss = { visible.targetState = false }
    LaunchedEffect(visible.targetState) {
        if (!visible.targetState) {
            delay(300)
            close()
        }
    }
    val title =
        if (unlocks.levels.first == unlocks.levels.last) {
            "Level ${unlocks.levels.last}!"
        } else {
            "Levels ${unlocks.levels.first}–${unlocks.levels.last}!"
        }
    BackHandler(onBack = dismiss)
    HudMaterialTheme {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(300, easing = noticeEasing)),
            exit = fadeOut(tween(300, easing = noticeEasing)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp)
                            .widthIn(max = 420.dp)
                            .hudPanel(22.dp)
                            .padding(18.dp),
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
                        unlocks.noticeSections().forEach { section ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = section.title.uppercase(),
                                    style = HudType.caption.copy(fontWeight = FontWeight.Bold),
                                    color = Theme.textSecondary,
                                )
                                section.rows.forEach { row ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        row.picture?.let { MenuImage(it, loader, Modifier.size(30.dp)) }
                                            ?: NoticeSymbolIcon(row.symbol, row.tint, 30.dp)
                                        Text(
                                            text = row.text,
                                            modifier = Modifier.weight(1f),
                                            style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                                            color = Theme.textPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    PillButton(
                        title = "Continue",
                        fill = Theme.accent,
                        text = Theme.onAccent,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = dismiss,
                    )
                }
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
    val loader = rememberMenuAssetLoader()
    val visible = remember(session, summary) { MutableTransitionState(false).apply { targetState = true } }
    val close by rememberUpdatedState(onClose)
    val state = remember(summary) { DaySummaryState.from(summary) }
    LaunchedEffect(session, summary) {
        delay(12_000)
        visible.targetState = false
        delay(2_000)
        close()
    }
    HudMaterialTheme {
        BannerBox {
            AnimatedVisibility(
                visible,
                enter = fadeIn(tween(2_000, easing = noticeEasing)),
                exit = fadeOut(tween(2_000, easing = noticeEasing)),
            ) {
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
                                style = HudType.subheadline.copy(fontFeatureSettings = "tnum"),
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
                                            val item = StorageItem.all.firstOrNull { it.id == sale.itemID }
                                            if (item != null) {
                                                MenuImage(MenuPictures.stored(item), loader, Modifier.size(32.dp))
                                            } else {
                                                NoticeSymbolIcon(NoticeSymbol.PAW, Theme.textSecondary, 32.dp)
                                            }
                                            Text(
                                                text = sale.name,
                                                style =
                                                    HudType.subheadline.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                    ),
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
                        HudIcon(
                            HudIcons.xmarkCircleFill,
                            Theme.textSecondary,
                            size = 28.dp,
                            contentDescription = "Close",
                        )
                    }
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
    val visible = remember(session, rewards) { MutableTransitionState(false).apply { targetState = true } }
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(session, rewards) {
        delay(4000)
        visible.targetState = false
        delay(400)
        close()
    }
    HudMaterialTheme {
        BannerBox {
            AnimatedVisibility(
                visible,
                enter =
                    fadeIn(tween(400, easing = noticeEasing)) +
                        scaleIn(tween(400, easing = noticeEasing), initialScale = 0.9f),
                exit =
                    fadeOut(tween(400, easing = noticeEasing)) +
                        scaleOut(tween(400, easing = noticeEasing), targetScale = 0.9f),
            ) {
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
}

private val noticeEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

internal enum class NoticeSymbol { FISH, ARROW, CHART, PAW }

internal data class NoticeUnlockRow(
    val text: String,
    val picture: MenuPicture?,
    val symbol: NoticeSymbol = NoticeSymbol.CHART,
    val tint: Color = Theme.accent,
)

internal data class NoticeUnlockSection(
    val title: String,
    val rows: List<NoticeUnlockRow>,
)

internal fun LevelUnlocks.noticeSections(): List<NoticeUnlockSection> {
    val pictures =
        mapOf(
            "Crops" to (crops + fruitTrees).map { MenuPictures.stored(StorageItem.CropItem(it)) },
            "Farmland" to listOf(MenuPictures.terrain("farmland")),
            "Buildings" to buildings.map(MenuPictures::building),
            "Decorations" to decorations.map { MenuPictures.decoration(it, it.offeredSizes.lastOrNull() ?: 0) },
            "Trees" to trees.map { MenuPictures.tree(it.smallest, it.colors.first()) },
            "Recipes" to recipes.map { MenuPictures.stored(it.output) },
            "Upgrades" to (
                toolUpgrades.map { ToolPalette.harvestPicture(it.tool) } +
                    homeUpgrades.map { Building.named(it.buildingID)?.let(MenuPictures::building) }
            ),
        )
    return sections().map { section ->
        NoticeUnlockSection(
            section.title,
            section.rows.mapIndexed { index, text ->
                val formatted =
                    if (section.title == "Farmland") {
                        "${ScreenFormat.number(farmlandTiles.toLong())} more tiles, " +
                            "${ScreenFormat.number(farmlandTotal.toLong())} in all"
                    } else {
                        text
                    }
                val fish = section.title == "Fishing" && index == 0 && fishRarities.isNotEmpty()
                val symbol =
                    when {
                        fish -> NoticeSymbol.FISH
                        section.title == "Fishing" -> NoticeSymbol.ARROW
                        else -> NoticeSymbol.CHART
                    }
                val tint =
                    if (fish) {
                        fishRarities.first().color.let {
                            Color(it.red.toFloat(), it.green.toFloat(), it.blue.toFloat())
                        }
                    } else {
                        Theme.accent
                    }
                NoticeUnlockRow(formatted, pictures[section.title]?.getOrNull(index), symbol, tint)
            },
        )
    }
}

@Composable
private fun NoticeSymbolIcon(
    symbol: NoticeSymbol,
    tint: Color,
    size: Dp,
) {
    if (symbol == NoticeSymbol.CHART) {
        HudIcon(HudIcons.chartLineUptrend, tint, size = size)
        return
    }
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24, this.size.height / 24, pivot = Offset.Zero) {
            when (symbol) {
                NoticeSymbol.FISH -> {
                    drawPath(
                        Path().apply {
                            moveTo(3f, 12f)
                            cubicTo(8f, 3f, 18f, 3f, 21f, 12f)
                            cubicTo(18f, 21f, 8f, 21f, 3f, 12f)
                            close()
                            moveTo(5f, 12f)
                            lineTo(1f, 7f)
                            lineTo(1f, 17f)
                            close()
                        },
                        tint,
                    )
                    drawCircle(Color.Black, 1f, Offset(17f, 11f))
                }

                NoticeSymbol.ARROW -> {
                    drawLine(tint, Offset(5f, 19f), Offset(19f, 5f), 2.5f, StrokeCap.Round)
                    drawLine(tint, Offset(9f, 5f), Offset(19f, 5f), 2.5f, StrokeCap.Round)
                    drawLine(tint, Offset(19f, 5f), Offset(19f, 15f), 2.5f, StrokeCap.Round)
                }

                NoticeSymbol.PAW -> {
                    drawOval(tint, Offset(6f, 11f), Size(12f, 10f))
                    listOf(Offset(3f, 9f), Offset(8f, 5f), Offset(15f, 5f), Offset(20f, 9f)).forEach {
                        drawOval(tint, it - Offset(2f, 3f), Size(4f, 6f))
                    }
                }

                NoticeSymbol.CHART -> {}
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
