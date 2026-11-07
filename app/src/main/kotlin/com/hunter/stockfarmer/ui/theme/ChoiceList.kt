package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.model.durationText
import com.hunter.stockfarmer.core.presentation.StatValue
import com.hunter.stockfarmer.core.rules.LevelRules

/** One row of a [ChoiceList]: reduced to what the row draws. */
data class HudChoice(
    val id: String,
    val title: String,
    val onPick: () -> Unit,
    val subtitle: String = "",
    val levelText: String? = null,
    val levelColor: Color? = null,
    val icon: (@Composable () -> Unit)? = null,
    /** Replaces the subtitle, for rows priced in colored amounts. */
    val priceLine: (@Composable () -> Unit)? = null,
    val isSelected: Boolean = false,
    val isLocked: Boolean = false,
    val isNew: Boolean = false,
    val isAffordable: Boolean = true,
    val isAtLimit: Boolean = false,
    val hasUpgrade: Boolean = false,
    val hasInfo: Boolean = false,
    val pulses: Boolean = false,
)

/** Sizes of a [ChoiceList], in dp before the HUD scale. */
object ChoiceMetrics {
    const val TABLET_WIDTH = 560
    const val MAX_HEIGHT = 340
    const val INFO_WIDTH = 60
    const val UPGRADE_WIDTH = 84
    const val DEFAULT_TRAILING = 14

    /** Room the end of a row leaves free for the info and upgrade buttons laid over it. */
    fun trailingRoom(
        reservesInfoRoom: Boolean,
        hasUpgrade: Boolean,
    ): Int = (if (reservesInfoRoom) INFO_WIDTH else DEFAULT_TRAILING) + if (hasUpgrade) UPGRADE_WIDTH else 0

    /** Where to scroll so a row of [rowHeight] sits in the middle of a [viewport], never above the top. */
    fun centeredOffset(
        rowTop: Int,
        rowHeight: Int,
        viewport: Int,
    ): Int = (rowTop - (viewport - rowHeight) / 2).coerceAtLeast(0)
}

/** A small floating list of choices beside the tool circle. */
@Composable
fun ChoiceList(
    choices: List<HudChoice>,
    onPick: () -> Unit,
    onInfo: (HudChoice) -> Unit,
    onUpgrade: (HudChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalHudScale.current
    val scroll = rememberScrollState()
    val viewport = remember { mutableIntStateOf(0) }
    val selectedTop = remember { mutableIntStateOf(-1) }
    val selectedHeight = remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedTop.intValue, viewport.intValue) {
        if (selectedTop.intValue >= 0 && viewport.intValue > 0) {
            scroll.scrollTo(
                ChoiceMetrics.centeredOffset(selectedTop.intValue, selectedHeight.intValue, viewport.intValue),
            )
        }
    }
    // On a tablet the list is as wide as is comfortable rather than as wide as the screen.
    val width =
        if (HudScale.isTablet(
                scale,
            )
        ) {
            Modifier.widthIn(max = ChoiceMetrics.TABLET_WIDTH.dp)
        } else {
            Modifier.fillMaxWidth()
        }
    BarScrollView(
        modifier
            .then(width)
            .heightIn(max = (ChoiceMetrics.MAX_HEIGHT * scale).dp)
            .hudPanel(18.dp)
            .onSizeChanged { viewport.intValue = it.height },
        scrollsOnlyWhenNeeded = true,
        state = scroll,
    ) {
        choices.forEach { choice ->
            Box(
                Modifier
                    .pulsing(choice.pulses, keepsPlace = false, shape = RectangleShape, inside = true)
                    .onGloballyPositioned {
                        if (choice.isSelected) {
                            selectedTop.intValue = it.positionInParent().y.toInt()
                            selectedHeight.intValue = it.size.height
                        }
                    }.semantics { if (choice.isSelected) selected = true }
                    .testTag("tool-choice-${choice.id}")
                    .clickable(role = Role.Button) {
                        choice.onPick()
                        onPick()
                    },
            ) {
                ChoiceRow(choice, reservesInfoRoom = choice.hasInfo)
                // Over the row rather than beside it, so the whole strip at its end belongs to the buttons and not to the row.
                Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                    if (choice.hasUpgrade) UpgradeButton { onUpgrade(choice) }
                    if (choice.hasInfo) InfoButton(choice.title) { onInfo(choice) }
                }
            }
        }
    }
}

/** The green i that opens the page about a choice without picking it, with a tall strip at the end of the row to tap. */
@Composable
private fun InfoButton(
    title: String,
    onClick: () -> Unit,
) {
    val scale = LocalHudScale.current
    Box(
        Modifier
            .width((ChoiceMetrics.INFO_WIDTH * scale).dp)
            .fillMaxHeight()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "About $title" },
        contentAlignment = Alignment.Center,
    ) {
        HudIcon(HudIcons.infoCircle, Theme.accent, size = (22 * scale).dp)
    }
}

/** The purple Upgrade pill beside a choice that has an upgrade on offer. */
@Composable
private fun UpgradeButton(onClick: () -> Unit) {
    val scale = LocalHudScale.current
    Box(
        Modifier
            .width((ChoiceMetrics.UPGRADE_WIDTH * scale).dp)
            .fillMaxHeight()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Upgrade" },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "Upgrade",
            Modifier
                .background(Theme.xp.copy(alpha = 0.14f), CircleShape)
                .padding(horizontal = (10 * scale).dp, vertical = (6 * scale).dp),
            style = hudText(13 * scale, FontWeight.SemiBold, Theme.xp),
        )
    }
}

@Composable
fun ChoiceRow(
    choice: HudChoice,
    modifier: Modifier = Modifier,
    showsChevron: Boolean = false,
    reservesInfoRoom: Boolean = false,
) {
    val scale = LocalHudScale.current
    val trailing = ChoiceMetrics.trailingRoom(reservesInfoRoom, choice.hasUpgrade)
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (choice.isAffordable && !choice.isAtLimit && !choice.isLocked) 1f else 0.45f)
            .padding(
                start = (14 * scale).dp,
                end = (trailing * scale).dp,
                top = (10 * scale).dp,
                bottom = (10 * scale).dp,
            ),
        horizontalArrangement = Arrangement.spacedBy((12 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        choice.icon?.let {
            Box(
                Modifier
                    .size((36 * scale).dp)
                    .newDot(choice.isNew, offset = DpOffset(4.dp, (-4).dp)),
            ) { it() }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((6 * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    choice.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = hudText(15 * scale, FontWeight.SemiBold, Theme.textPrimary),
                )
                choice.levelText?.let {
                    BasicText(
                        it,
                        maxLines = 1,
                        style =
                            hudText(
                                11 * scale,
                                if (choice.levelColor == null) FontWeight.Normal else FontWeight.SemiBold,
                                choice.levelColor ?: Theme.textSecondary,
                            ),
                    )
                }
            }
            val priceLine = choice.priceLine
            if (priceLine != null) {
                priceLine()
            } else if (choice.subtitle.isNotEmpty()) {
                BasicText(
                    choice.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = hudText(12 * scale, FontWeight.Normal, Theme.textSecondary),
                )
            }
        }
        when {
            showsChevron -> HudIcon(HudIcons.chevronRight, Theme.textSecondary, size = 13.dp)
            choice.isLocked -> HudIcon(HudIcons.lockFill, Theme.textSecondary, size = 17.dp)
            choice.isSelected -> HudIcon(HudIcons.checkmarkCircleFill, Theme.accent, size = 20.dp)
        }
    }
}

/** What a building takes, money then logs then stones, each green while there is enough of it and red while there isn't. */
@Composable
fun BuildingPriceLine(
    moneyText: String,
    hasMoney: Boolean,
    modifier: Modifier = Modifier,
    logs: Int = 0,
    hasLogs: Boolean = true,
    logIcon: @Composable () -> Unit = {},
    stones: Int = 0,
    hasStones: Boolean = true,
    stoneIcon: @Composable () -> Unit = {},
) {
    val scale = LocalHudScale.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        PriceAmount(moneyText, hasMoney)
        if (logs > 0) {
            PriceDivider()
            PriceAmount(logs.toString(), hasLogs)
            Box(Modifier.size((22 * scale).dp)) { logIcon() }
        }
        if (stones > 0) {
            PriceDivider()
            PriceAmount(stones.toString(), hasStones)
            Box(Modifier.size((16 * scale).dp)) { stoneIcon() }
        }
    }
}

/** What something costs, in the style of a building's price: bare compact money, green while affordable and red while not. */
@Composable
fun MoneyPriceLine(
    priceText: String,
    hasMoney: Boolean,
    modifier: Modifier = Modifier,
) {
    PriceAmount(priceText, hasMoney, modifier)
}

/**
 * How long a crop takes to grow, what harvesting it pays and the XP it earns, as bare numbers in their stat colors:
 * "26m 18s / $5 / +3 XP".
 */
@Composable
fun CropPriceLine(
    crop: Crop,
    modifier: Modifier = Modifier,
) {
    StatValuesLine(
        listOf(
            StatValue.Duration(crop.growDuration),
            StatValue.Money(crop.sellValue),
            StatValue.XP(LevelRules.harvestXP(crop)),
        ),
        modifier.semantics(mergeDescendants = true) {
            contentDescription =
                "Grows in ${crop.growDuration.durationText}, harvest for ${crop.sellValue.dollars} and " +
                "${LevelRules.harvestXP(crop)} XP"
        },
    )
}

@Composable
private fun PriceAmount(
    text: String,
    isEnough: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text,
        modifier,
        maxLines = 1,
        style = hudText(12 * LocalHudScale.current, FontWeight.SemiBold, if (isEnough) Theme.accent else Theme.spend),
    )
}

@Composable
private fun PriceDivider() {
    BasicText("/", style = hudText(12 * LocalHudScale.current, FontWeight.SemiBold, Theme.textSecondary))
}

private fun hudText(
    size: Float,
    weight: FontWeight,
    color: Color,
) = HudType.footnote.copy(fontSize = size.sp, fontWeight = weight, color = color)
