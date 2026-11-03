package com.hunter.stockfarmer.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.compactDollars
import com.hunter.stockfarmer.core.model.compactDurationText
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.rules.GameEvent
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.TILE
import com.hunter.stockfarmer.game.ToolDrag
import com.hunter.stockfarmer.ui.fishing.FishingMode
import com.hunter.stockfarmer.ui.menus.MenuAssetLoader
import com.hunter.stockfarmer.ui.menus.MenuImage
import com.hunter.stockfarmer.ui.menus.MenuPictures
import com.hunter.stockfarmer.ui.menus.rememberMenuAssetLoader
import com.hunter.stockfarmer.ui.rewards.LocalRewardFlights
import com.hunter.stockfarmer.ui.theme.BuildingPriceLine
import com.hunter.stockfarmer.ui.theme.ChoiceList
import com.hunter.stockfarmer.ui.theme.CropPriceLine
import com.hunter.stockfarmer.ui.theme.HudChoice
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.LessonBubble
import com.hunter.stockfarmer.ui.theme.LocalHudScale
import com.hunter.stockfarmer.ui.theme.MoneyPriceLine
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.hudPanel
import com.hunter.stockfarmer.ui.theme.newDot
import com.hunter.stockfarmer.ui.theme.pulsing

/** What the ring's non-tool buttons open. */
enum class RingScreen { Storage, Settings, Time }

/** How far the circle sits in from the screen's left edge, which brings it closer to the rings fanned out around it. */
internal const val PHONE_LEADING_NUDGE = 14f

/** The bottom-left tool circle with whatever options the tool has beside it, and the ring and list that open from it. */
@Composable
internal fun ToolBar(
    session: GameSession,
    hud: HudState,
    regions: MenuInteractionRegions,
    onRingScreen: (RingScreen) -> Unit,
    modifier: Modifier = Modifier,
    /** Bumped by the caller whenever a sheet or screen opens, which closes the ring and list. */
    closeRequests: Int = 0,
    newTools: Set<HudTool> = hud.newTools,
    onOpenRoute: (ToolRoute) -> Unit = {},
    onDraggingChanged: (Boolean) -> Unit = {},
    /** The fishing mode this bar sits over, if a line is out: the circle then holds the rod and the ring offers only the way out. */
    fishing: FishingMode? = null,
) {
    val scale = LocalHudScale.current
    val density = LocalDensity.current.density
    val haptics = LocalHapticFeedback.current
    val loader = rememberMenuAssetLoader()
    val flights = LocalRewardFlights.current
    flights?.revision
    val storagePulse = flights?.tracker?.storagePulse ?: 0
    val isFishing = fishing?.isFishing == true
    val lesson = hud.lesson
    val lessonTool =
        when (lesson.toolTarget) {
            PlayMode.plant -> HudTool.Planting
            PlayMode.harvest -> HudTool.Harvest
            else -> null
        }
    val drag = remember(session) { ToolDrag(session) }
    var openDropdown by remember { mutableStateOf<String?>(null) }
    var isRingOpen by remember { mutableStateOf(false) }

    fun closeMenus() {
        openDropdown = null
        isRingOpen = false
    }

    val farmTouches by session.farmTouches.collectAsStateWithLifecycle()
    LaunchedEffect(farmTouches, closeRequests) { closeMenus() }

    fun tileAt(point: Offset) = session.view.tileAt(point.x * density, point.y * density, TILE, GameState.GRID_SIZE)

    val open = hud.dropdowns.firstOrNull { it.id == openDropdown }
    val lastOpen = remember { mutableStateOf<ToolDropdown?>(null) }
    if (open != null) lastOpen.value = open
    val spring = spring<Float>(0.85f, 157.9f)

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = (PHONE_LEADING_NUDGE * scale).dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = open != null,
                enter = fadeIn(spring) + scaleIn(spring, 0.9f, TransformOrigin(0f, 1f)),
                exit = fadeOut(spring) + scaleOut(spring, 0.9f, TransformOrigin(0f, 1f)),
            ) {
                val shown = open ?: lastOpen.value
                if (shown != null) {
                    ChoiceList(
                        choices = shown.choices.map { choice(session, it, loader) },
                        onPick = { openDropdown = null },
                        onInfo = { picked ->
                            shown.choices
                                .firstOrNull { it.id == picked.id }
                                ?.detail
                                ?.let(onOpenRoute)
                        },
                        onUpgrade = { picked ->
                            shown.choices
                                .firstOrNull { it.id == picked.id }
                                ?.upgrade
                                ?.let(onOpenRoute)
                        },
                        modifier =
                            Modifier
                                .padding(end = (PHONE_LEADING_NUDGE * scale).dp)
                                .menuInteractionRegion(regions, "tool-choices", isEnabled = open != null),
                    )
                }
            }
            lesson.message?.let { LessonBubble(it) }
            Row(horizontalArrangement = Arrangement.spacedBy((10 * scale).dp), verticalAlignment = Alignment.Bottom) {
                ToolCircle(
                    title = hud.tool.title,
                    tool = hud.circleTool,
                    pulses = lesson.pulses,
                    isNew =
                        if (lesson.isTeaching) lessonTool != null || lesson.storageTarget else newTools.isNotEmpty(),
                    throbTrigger = storagePulse,
                    showsTile = { tileAt(it) != null },
                    onTap = {
                        openDropdown = null
                        isRingOpen = !isRingOpen
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onMove = { to, phase ->
                        closeMenus()
                        // With a fish on the line the tool in hand is the rod, and where it is held decides whether the bar fills.
                        if (isFishing) fishing?.moveRod(if (phase == ToolDragPhase.Inactive) null else to)
                    },
                    onDrag = { _, to -> if (!isFishing) drag.move(tileAt(to)) },
                    onHold = {
                        if (!isFishing) drag.explain()?.let { session.publish(listOf(GameEvent.Rejected(it))) }
                    },
                    onDragEnded = {
                        if (isFishing) fishing?.moveRod(null) else drag.end()
                    },
                    modifier =
                        Modifier
                            .menuInteractionRegion(regions, "tool-circle", (12 * scale).dp),
                    onDraggingChanged = onDraggingChanged,
                ) { MenuImage(hud.toolPicture, loader, Modifier.fillMaxSize()) }
                if (!isFishing) {
                    Options(
                        hud = hud,
                        isRingOpen = isRingOpen,
                        openDropdown = openDropdown,
                        regions = regions,
                        onOpen = { openDropdown = if (openDropdown == it) null else it },
                        modifier =
                            Modifier
                                .padding(bottom = ((ToolCircleMetrics.DIAMETER - DROPDOWN_HEIGHT) * scale / 2).dp)
                                .alpha(if (isRingOpen) 0f else 1f),
                    )
                }
            }
        }
        ToolRing(
            isOpen = isRingOpen,
            state =
                RingState(
                    currentTool = hud.tool,
                    newTools = newTools,
                    lessonTool = lessonTool,
                    isStorageLesson = lesson.storageTarget,
                    storageDescription = hud.storage,
                    minuteOfDay = { now -> session.act { DayNightCycle.minuteOfDay(now, it.state.clockOffset) } },
                ),
            loader = loader,
            regions = regions,
            onPick = { satellite ->
                when (satellite) {
                    is Satellite.OfTool -> {
                        session.act { it.pickUp(satellite.tool) }
                        isRingOpen = false
                        openDropdown = session.act { ToolPalette.dropdowns(it.state, satellite.tool) }.firstOrNull()?.id
                    }

                    Satellite.Storage -> {
                        closeMenus()
                        onRingScreen(RingScreen.Storage)
                    }

                    Satellite.Settings -> {
                        closeMenus()
                        onRingScreen(RingScreen.Settings)
                    }

                    Satellite.Time -> {
                        closeMenus()
                        onRingScreen(RingScreen.Time)
                    }

                    Satellite.ExitFishing -> {
                        closeMenus()
                        fishing?.leave()
                    }
                }
            },
            isFishing = isFishing,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = (PHONE_LEADING_NUDGE * scale).dp),
        )
    }
}

private const val DROPDOWN_HEIGHT = 58f
private const val DROPDOWN_TOUCH_MARGIN = 8

@Composable
private fun Options(
    hud: HudState,
    isRingOpen: Boolean,
    openDropdown: String?,
    regions: MenuInteractionRegions,
    onOpen: (String) -> Unit,
    modifier: Modifier,
) {
    val scale = LocalHudScale.current
    WrappingStack(modifier, spacing = (10 * scale).dp, lineSpacing = (8 * scale).dp) {
        hud.dropdowns.forEach { dropdown ->
            DropdownChip(
                dropdown,
                isOpen = openDropdown == dropdown.id,
                modifier =
                    Modifier
                        .pulsing(dropdown.pulses, keepsPlace = false, shape = RoundedCornerShape((17 * scale).dp))
                        .menuInteractionRegion(
                            regions,
                            "tool-dropdown-${dropdown.id}",
                            DROPDOWN_TOUCH_MARGIN.dp,
                            !isRingOpen,
                        ).clickable(
                            enabled = !isRingOpen,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                        ) { onOpen(dropdown.id) },
            )
        }
    }
}

/** The name of what the tool holds, then how many are left in storage when it comes from there, in red once there are none. */
internal fun dropdownLabel(dropdown: ToolDropdown): AnnotatedString =
    buildAnnotatedString {
        append(dropdown.value)
        dropdown.stock?.let { stock ->
            withStyle(
                SpanStyle(
                    color =
                        if (stock ==
                            0
                        ) {
                            Theme.danger
                        } else {
                            Theme.textPrimary
                        },
                ),
            ) { append(ToolPalette.stockSuffix(stock)) }
        }
    }

@Composable
private fun DropdownChip(
    dropdown: ToolDropdown,
    isOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = LocalHudScale.current
    Row(
        modifier
            .height((DROPDOWN_HEIGHT * scale).dp)
            .hudPanel((17 * scale).dp)
            .newDot(dropdown.hasNew, Alignment.TopEnd, DpOffset(4.dp, (-4).dp))
            .padding(horizontal = (14 * scale).dp)
            .semantics { contentDescription = dropdownLabel(dropdown).text },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            dropdownLabel(dropdown),
            maxLines = 1,
            style =
                HudType.body.copy(
                    fontSize = (18 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.textPrimary,
                ),
        )
        UpDownChevron((13 * scale).dp.value, isOpen)
    }
}

/** SF Symbol `chevron.up.chevron.down`. */
@Composable
private fun UpDownChevron(
    sizeDp: Float,
    @Suppress("UNUSED_PARAMETER") isOpen: Boolean,
) {
    Canvas(Modifier.size((sizeDp * 0.9f).dp, sizeDp.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        val half = h / 2

        fun chevron(
            tipY: Float,
            baseY: Float,
        ) {
            drawLine(Theme.textSecondary, Offset(0f, baseY), Offset(w / 2, tipY), stroke.width, StrokeCap.Round)
            drawLine(Theme.textSecondary, Offset(w, baseY), Offset(w / 2, tipY), stroke.width, StrokeCap.Round)
        }
        chevron(0.5.dp.toPx(), half * 0.75f)
        chevron(h - 0.5.dp.toPx(), half * 1.25f)
    }
}

/** Turns a row of the palette into a row the shared [ChoiceList] draws. */
@Composable
private fun choice(
    session: GameSession,
    choice: ToolChoice,
    loader: MenuAssetLoader,
): HudChoice =
    HudChoice(
        id = choice.id,
        title = choice.title,
        onPick = {
            session
                .act {
                    ToolPalette.perform(
                        it,
                        choice.action,
                    )
                }?.let { session.publish(listOf(GameEvent.Rejected(it))) }
        },
        subtitle = choice.subtitle,
        levelText = choice.levelText,
        levelColor = choice.toolLevel?.let(Theme::toolLevel),
        pulses = choice.pulses,
        icon = choice.picture?.let { picture -> { MenuImage(picture, loader, Modifier.fillMaxSize()) } },
        priceLine = priceLine(choice, loader),
        isSelected = choice.isSelected,
        isLocked = choice.requiredLevel != null,
        isNew = choice.isNew,
        isAffordable = choice.isAffordable,
        isAtLimit = choice.isAtLimit,
        hasInfo = choice.detail != null,
        hasUpgrade = choice.upgrade != null,
    )

private fun priceLine(
    choice: ToolChoice,
    loader: MenuAssetLoader,
): (@Composable () -> Unit)? {
    choice.crop?.let { crop ->
        return {
            CropPriceLine(
                crop.growDuration.compactDurationText,
                crop.sellValue.compactDollars,
                "${LevelRules.harvestXP(crop)} XP",
            )
        }
    }
    val building = choice.building
    val money = choice.prices.firstOrNull() ?: return null
    if (building == null) return { MoneyPriceLine(money.text, money.affordable) }
    val rest = choice.prices.drop(1)
    val logs = if (building.logCost > 0) rest.firstOrNull() else null
    val stones = if (building.stoneCost > 0) rest.lastOrNull()?.takeIf { it !== logs } else null
    return {
        BuildingPriceLine(
            moneyText = money.text,
            hasMoney = money.affordable,
            logs = building.logCost,
            hasLogs = logs?.affordable ?: true,
            logIcon = { MenuImage(MenuPictures.stored(StorageItem.Log), loader, Modifier.fillMaxSize()) },
            stones = building.stoneCost,
            hasStones = stones?.affordable ?: true,
            stoneIcon = { MenuImage(MenuPictures.stored(StorageItem.Stone), loader, Modifier.fillMaxSize()) },
        )
    }
}

/** Splits widths into rows that fit [available], a new row starting when the next would not fit. */
object WrappingRows {
    fun rows(
        widths: List<Int>,
        available: Int,
        spacing: Int,
    ): List<List<Int>> {
        val rows = mutableListOf(mutableListOf<Int>())
        var used = 0
        widths.forEachIndexed { index, width ->
            val row = rows.last()
            val needed = (if (row.isEmpty()) 0 else spacing) + width
            if (used + needed > available && row.isNotEmpty()) {
                rows.add(mutableListOf())
                used = 0
            }
            used += (if (rows.last().isEmpty()) 0 else spacing) + width
            rows.last().add(index)
        }
        return rows
    }
}

/** Lays out in a row, starting a new row above when the next will not fit; the first row sits at the bottom. */
@Composable
private fun WrappingStack(
    modifier: Modifier,
    spacing: androidx.compose.ui.unit.Dp,
    lineSpacing: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables: List<Measurable>, constraints: Constraints ->
        val space = spacing.roundToPx()
        val lineSpace = lineSpacing.roundToPx()
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val placeables: List<Placeable> = measurables.map { it.measure(loose) }
        val rows = WrappingRows.rows(placeables.map { it.width }, constraints.maxWidth, space)
        val rowWidths = rows.map { row -> row.sumOf { placeables[it].width } + space * (row.size - 1).coerceAtLeast(0) }
        val rowHeights = rows.map { row -> row.maxOfOrNull { placeables[it].height } ?: 0 }
        val width =
            (rowWidths.maxOrNull() ?: 0).coerceIn(
                constraints.minWidth,
                constraints.maxWidth.coerceAtLeast(constraints.minWidth),
            )
        val height = rowHeights.sum() + lineSpace * (rows.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var bottom = height
            rows.forEachIndexed { rowIndex, row ->
                var x = 0
                row.forEach { index ->
                    val placeable = placeables[index]
                    placeable.placeRelative(x, bottom - placeable.height)
                    x += placeable.width + space
                }
                bottom -= rowHeights[rowIndex] + lineSpace
            }
        }
    }
}
